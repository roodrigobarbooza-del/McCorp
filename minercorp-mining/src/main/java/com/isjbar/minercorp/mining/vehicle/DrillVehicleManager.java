package com.isjbar.minercorp.mining.vehicle;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.mining.company.StoredDrill;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Taladro-vehiculo. Ya no es un bote: el bote lo mueve el cliente del
 * jugador, asi que la velocidad por tier no tenia efecto y en tierra andaba
 * lentisimo. Ahora el vehiculo son:
 *
 * <ul>
 *   <li>la raiz, un {@link BlockDisplay} invisible al que se sube el jugador
 *       y que guarda empresa, tier y combustible en su PDC;</li>
 *   <li>la carroceria, un {@link DrillModel} (solo dibuja);</li>
 *   <li>un {@link Interaction} invisible para poder hacerle click derecho
 *       (subirse o cargar combustible).</li>
 * </ul>
 *
 * Cada tick el servidor lee las teclas del conductor
 * ({@link Player#getCurrentInput()}) y mueve las tres entidades; los
 * displays usan teleportDuration para que el cliente interpole y se vea
 * fluido. Al avanzar contra bloques los perfora en una caja de ancho x alto
 * por delante, sin tocar nunca el piso donde esta apoyado.
 */
public class DrillVehicleManager {

    private static final double HALF_WIDTH = 0.45;
    /** Medio largo del vehiculo: el frente de las orugas de DrillModel queda a ~1 bloque del centro. */
    private static final double HALF_LENGTH = 0.9;
    private static final int TELEPORT_TICKS = 2;

    private final MiningPlugin plugin;
    private final TerritoryAPI territory;
    private final EconomyAPI economy;

    private final NamespacedKey companyKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey fuelKey;
    private final NamespacedKey modelKey;
    private final NamespacedKey seatKey;
    private final NamespacedKey rootKey;

    // Claves del taladro viejo (bote), solo para convertirlos.
    private final NamespacedKey legacyCompanyKey;
    private final NamespacedKey legacyTierKey;
    private final NamespacedKey legacyHullKey;
    private final NamespacedKey legacyBitKey;

    private final Map<UUID, DrillVehicle> driving = new HashMap<>();

    private Settings settings;
    private DrillRewards rewards;
    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private boolean dirty;

    public DrillVehicleManager(MiningPlugin plugin, TerritoryAPI territory, EconomyAPI economy) {
        this.plugin = plugin;
        this.territory = territory;
        this.economy = economy;
        this.companyKey = new NamespacedKey(plugin, "taladro_empresa");
        this.tierKey = new NamespacedKey(plugin, "taladro_tier");
        this.fuelKey = new NamespacedKey(plugin, "taladro_combustible");
        this.modelKey = new NamespacedKey(plugin, "taladro_modelo");
        this.seatKey = new NamespacedKey(plugin, "taladro_asiento");
        this.rootKey = new NamespacedKey(plugin, "taladro_raiz");
        this.legacyCompanyKey = new NamespacedKey(plugin, "drill_company");
        this.legacyTierKey = new NamespacedKey(plugin, "drill_tier");
        this.legacyHullKey = new NamespacedKey(plugin, "drill_hull_id");
        this.legacyBitKey = new NamespacedKey(plugin, "drill_bit_id");
    }

    public void start() {
        reloadSettings();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        long saveTicks = Math.max(20L, plugin.getConfig().getLong("taladros.guardado-segundos", 30) * 20L);
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, saveTicks, saveTicks);
        for (World world : plugin.getServer().getWorlds()) {
            convertLegacy(world.getEntities());
        }
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
        for (DrillVehicle v : new ArrayList<>(driving.values())) {
            Entity root = plugin.getServer().getEntity(v.rootId);
            if (root != null) root.eject();
            stopDriving(v.rootId);
        }
        saveIfDirty();
    }

    public void reloadSettings() {
        this.settings = Settings.load(plugin);
        this.rewards = DrillRewards.load(plugin.getConfig(), plugin.getLogger());
    }

    // ---------------------------------------------------------------
    // Compra, carga y remocion
    // ---------------------------------------------------------------

    public PlacementResult spawn(Company company, int tier, Location location) {
        if (!DrillTier.exists(plugin.getConfig(), tier)) {
            return PlacementResult.TIER_INVALIDO;
        }
        if (!ownedBy(company.getId(), location.getChunk())) {
            return PlacementResult.FUERA_DE_TERRITORIO;
        }
        DrillTier tierData = DrillTier.load(plugin.getConfig(), tier);
        if (!economy.withdraw(company.getId(), tierData.costo())) {
            return PlacementResult.SIN_SALDO;
        }
        Location at = location.clone();
        at.setPitch(0);
        createEntities(company.getId(), company.getName(), tierData, at, 0);
        return PlacementResult.OK;
    }

    private BlockDisplay createEntities(UUID companyId, String companyName, DrillTier tier, Location feet, double fuel) {
        World world = feet.getWorld();
        Location rootLoc = feet.clone().add(0, settings.seatHeight, 0);

        // Raiz invisible: lleva al conductor y los datos del taladro. Es un display
        // (sin bloque) para que el cliente interpole su movimiento y el del pasajero.
        BlockDisplay root = world.spawn(rootLoc, BlockDisplay.class, d -> {
            d.setBlock(Material.AIR.createBlockData());
            d.setTeleportDuration(TELEPORT_TICKS);
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            pdc.set(companyKey, PersistentDataType.STRING, companyId.toString());
            pdc.set(tierKey, PersistentDataType.INTEGER, tier.tier());
            pdc.set(fuelKey, PersistentDataType.DOUBLE, fuel);
        });

        DrillModel model = DrillModel.spawn(plugin, feet, tier.tier(),
                Component.text(tier.nombre() + " - " + companyName, NamedTextColor.AQUA));
        Entity chassis = plugin.getServer().getEntity(model.rootId());
        if (chassis != null) {
            chassis.getPersistentDataContainer().set(rootKey, PersistentDataType.STRING, root.getUniqueId().toString());
        }

        Interaction seat = world.spawn(feet, Interaction.class, i -> {
            i.setInteractionWidth(1.8f);
            i.setInteractionHeight(1.3f);
            i.setResponsive(true);
            i.getPersistentDataContainer().set(rootKey, PersistentDataType.STRING, root.getUniqueId().toString());
        });

        root.getPersistentDataContainer().set(modelKey, PersistentDataType.STRING, model.rootId().toString());
        root.getPersistentDataContainer().set(seatKey, PersistentDataType.STRING, seat.getUniqueId().toString());
        return root;
    }

    private Optional<DrillModel> modelOf(Entity root) {
        String id = root.getPersistentDataContainer().get(modelKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : DrillModel.find(plugin, UUID.fromString(id));
    }

    /** Elimina el taladro completo (raiz, carroceria y asiento). */
    public void removeVehicle(BlockDisplay root) {
        stopDriving(root.getUniqueId());
        root.eject();
        modelOf(root).ifPresent(DrillModel::remove);
        linked(root, seatKey).ifPresent(Entity::remove);
        root.remove();
    }

    /**
     * Guarda el taladro en el garaje de la empresa (tier y combustible) y lo saca
     * del mundo. Devuelve false si alguien lo esta manejando.
     */
    public boolean store(Company company, BlockDisplay root) {
        if (!root.getPassengers().isEmpty()) return false;
        DrillTier tier = tierOf(root);
        double fuel = fuelOf(root);
        removeVehicle(root);
        company.getStoredDrills().add(new StoredDrill(tier.tier(), fuel));
        plugin.companies().save();
        return true;
    }

    /** Saca del garaje el taladro numero {@code index} en la posicion dada, sin cobrarlo. */
    public PlacementResult deploy(Company company, int index, Location location) {
        if (index < 0 || index >= company.getStoredDrills().size()) return PlacementResult.TIER_INVALIDO;
        if (!ownedBy(company.getId(), location.getChunk())) return PlacementResult.FUERA_DE_TERRITORIO;
        StoredDrill stored = company.getStoredDrills().get(index);
        int tierNumber = DrillTier.exists(plugin.getConfig(), stored.tier()) ? stored.tier() : 1;
        DrillTier tier = DrillTier.load(plugin.getConfig(), tierNumber);
        Location at = location.clone();
        at.setPitch(0);
        createEntities(company.getId(), company.getName(), tier, at, Math.min(stored.combustible(), tier.combustible()));
        company.getStoredDrills().remove(index);
        plugin.companies().save();
        return PlacementResult.OK;
    }

    /** Taladro de la empresa mas cercano dentro del radio dado. */
    public Optional<BlockDisplay> nearestOwned(Company company, Location location, double maxDistance) {
        BlockDisplay nearest = null;
        double best = maxDistance * maxDistance;
        for (Entity e : location.getWorld().getNearbyEntities(location, maxDistance, maxDistance, maxDistance)) {
            if (!(e instanceof BlockDisplay display)) continue;
            String id = display.getPersistentDataContainer().get(companyKey, PersistentDataType.STRING);
            if (id == null || !id.equals(company.getId().toString())) continue;
            double d = display.getLocation().distanceSquared(location);
            if (d <= best) {
                best = d;
                nearest = display;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** Borra modelos/asientos cercanos cuyo taladro ya no existe (por ejemplo, si se uso /kill). */
    public int removeOrphanParts(Location location, double radius) {
        int removed = 0;
        for (Entity e : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            String rootId = e.getPersistentDataContainer().get(rootKey, PersistentDataType.STRING);
            if (rootId == null) continue;
            if (plugin.getServer().getEntity(UUID.fromString(rootId)) == null) {
                Optional<DrillModel> model = DrillModel.find(plugin, e.getUniqueId());
                if (model.isPresent()) model.get().remove();
                else e.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Suma combustible desde un item en la mano. Devuelve cuantos items consumio. */
    public int refuelFromItem(BlockDisplay root, ItemStack item) {
        Double perItem = settings.fuelItems.get(item.getType());
        if (perItem == null || perItem <= 0) return 0;
        DrillTier tier = tierOf(root);
        double fuel = fuelOf(root);
        double space = tier.combustible() - fuel;
        int usable = (int) Math.min(item.getAmount(), Math.floor(space / perItem));
        if (usable <= 0) return 0;
        setFuel(root, fuel + usable * perItem);
        return usable;
    }

    /** Carga combustible usando carbon crudo de la empresa. Devuelve cuanto carbon crudo uso. */
    public double refuelFromRawCoal(Company company, BlockDisplay root, double maxRawCoal) {
        DrillTier tier = tierOf(root);
        double fuel = fuelOf(root);
        double perUnit = settings.fuelPerRawCoal;
        if (perUnit <= 0) return 0;
        double needed = (tier.combustible() - fuel) / perUnit;
        double used = Math.min(Math.min(maxRawCoal, needed), company.getRawCoal());
        if (used <= 0 || !company.removeRawCoal(used)) return 0;
        setFuel(root, fuel + used * perUnit);
        dirty = true;
        return used;
    }

    public boolean isFuelItem(Material material) {
        return settings.fuelItems.containsKey(material);
    }

    public double fuelOf(BlockDisplay root) {
        DrillVehicle active = driving.get(root.getUniqueId());
        if (active != null) return active.fuel;
        Double fuel = root.getPersistentDataContainer().get(fuelKey, PersistentDataType.DOUBLE);
        return fuel == null ? 0 : fuel;
    }

    private void setFuel(BlockDisplay root, double fuel) {
        DrillVehicle active = driving.get(root.getUniqueId());
        if (active != null) active.fuel = fuel;
        root.getPersistentDataContainer().set(fuelKey, PersistentDataType.DOUBLE, fuel);
    }

    public DrillTier tierOf(BlockDisplay root) {
        Integer tier = root.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return DrillTier.load(plugin.getConfig(), tier == null ? 1 : tier);
    }

    public Optional<UUID> companyOf(Entity root) {
        String id = root.getPersistentDataContainer().get(companyKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    /** Dado un asiento (Interaction) o la raiz, devuelve la raiz. */
    public Optional<BlockDisplay> rootOf(Entity entity) {
        if (entity instanceof BlockDisplay display && display.getPersistentDataContainer().has(companyKey, PersistentDataType.STRING)) {
            return Optional.of(display);
        }
        String rootId = entity.getPersistentDataContainer().get(rootKey, PersistentDataType.STRING);
        if (rootId == null) return Optional.empty();
        Entity root = plugin.getServer().getEntity(UUID.fromString(rootId));
        return root instanceof BlockDisplay display ? Optional.of(display) : Optional.empty();
    }

    public boolean isDrillRoot(Entity entity) {
        return entity instanceof BlockDisplay && entity.getPersistentDataContainer().has(companyKey, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------
    // Subirse / bajarse
    // ---------------------------------------------------------------

    public void startDriving(BlockDisplay root, Player player) {
        UUID companyId = companyOf(root).orElse(null);
        if (companyId == null) return;
        DrillTier tier = tierOf(root);
        BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);

        DrillVehicle v = new DrillVehicle(root.getUniqueId(), companyId, tier, player.getUniqueId(), root.getWorld(), bar);
        Location loc = root.getLocation();
        v.x = loc.getX();
        v.y = loc.getY() - settings.seatHeight;
        v.z = loc.getZ();
        v.yaw = loc.getYaw();
        v.fuel = fuelOf(root);
        v.model = modelOf(root).orElse(null);
        driving.put(root.getUniqueId(), v);

        updateBossBar(v);
        player.showBossBar(bar);
        player.sendActionBar(Component.text("W/S avanzar - A/D girar - Espacio frenar - Shift bajarse", NamedTextColor.GRAY));
    }

    public void stopDriving(UUID rootId) {
        DrillVehicle v = driving.remove(rootId);
        if (v == null) return;
        Entity root = plugin.getServer().getEntity(rootId);
        if (root != null) {
            root.getPersistentDataContainer().set(fuelKey, PersistentDataType.DOUBLE, v.fuel);
        }
        if (v.model != null && v.model.isValid()) v.model.setDrilling(false);
        Player driver = plugin.getServer().getPlayer(v.driverId);
        if (driver != null) {
            driver.hideBossBar(v.bossBar);
            if (v.tripBlocks > 0) {
                driver.sendMessage(Component.text("Viaje terminado: " + v.tripBlocks + " bloques perforados, "
                        + round(v.tripCoal) + " de carbon crudo para la empresa.", NamedTextColor.GREEN));
            }
        }
    }

    public boolean isDriving(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null && driving.containsKey(vehicle.getUniqueId());
    }

    // ---------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------

    private void tick() {
        Iterator<DrillVehicle> it = driving.values().iterator();
        List<UUID> finished = new ArrayList<>();
        while (it.hasNext()) {
            DrillVehicle v = it.next();
            Entity rootEntity = plugin.getServer().getEntity(v.rootId);
            Player driver = plugin.getServer().getPlayer(v.driverId);
            if (!(rootEntity instanceof BlockDisplay root) || !root.isValid() || driver == null
                    || !root.getPassengers().contains(driver)) {
                finished.add(v.rootId);
                continue;
            }
            Optional<Company> company = plugin.companies().getById(v.companyId);
            if (company.isEmpty()) {
                finished.add(v.rootId);
                continue;
            }
            tickVehicle(v, root, driver, company.get());
        }
        for (UUID id : finished) stopDriving(id);
    }

    private void tickVehicle(DrillVehicle v, BlockDisplay root, Player driver, Company company) {
        v.ticks++;
        Input input = driver.getCurrentInput();

        // Direccion
        int turn = (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0);
        v.yaw = wrapYaw(v.yaw + (float) (turn * v.tier.giro()));
        Vector dir = direction(v.yaw);

        // Perforacion: solo mientras se aprieta W y hay combustible
        boolean drilling = input.isForward() && drill(v, company, driver, dir);
        if (!input.isForward()) v.drillProgress = 0;

        // Velocidad
        double max = v.tier.velocidad() * (v.fuel > 0 ? 1.0 : settings.speedWithoutFuel);
        double target = input.isForward() ? max : input.isBackward() ? -max * 0.5 : 0;
        double accel = max * (input.isJump() ? 0.4 : 0.15);
        if (input.isJump()) target = 0;
        if (v.speed < target) v.speed = Math.min(target, v.speed + accel);
        else if (v.speed > target) v.speed = Math.max(target, v.speed - accel);

        // Movimiento horizontal con colision y limite de territorio
        if (Math.abs(v.speed) > 1.0E-3) {
            double nx = v.x + dir.getX() * v.speed;
            double nz = v.z + dir.getZ() * v.speed;
            MoveResult move = tryMove(v, company, dir, nx, nz);
            switch (move) {
                case OK -> { v.x = nx; v.z = nz; }
                case STEP -> { v.x = nx; v.z = nz; v.y = Math.floor(v.y + 0.01) + 1; }
                case BLOQUEADO -> v.speed = 0;
                case FUERA_DE_TERRITORIO -> {
                    v.speed = 0;
                    warn(v, driver, "Limite del territorio de tu empresa");
                }
            }
        }

        // Gravedad
        applyGravity(v);

        // Mover entidades
        Location rootLoc = new Location(v.world, v.x, v.y + settings.seatHeight, v.z, v.yaw, 0);
        root.teleport(rootLoc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        Location feet = new Location(v.world, v.x, v.y, v.z, v.yaw, 0);
        if (v.model != null && v.model.isValid()) {
            v.model.moveTo(feet);
            v.model.setDrilling(drilling);
        }
        linked(root, seatKey).ifPresent(seat -> seat.teleport(feet));

        effects(v, drilling);
        if (v.ticks % 10 == 0) updateBossBar(v);
    }

    private boolean drill(DrillVehicle v, Company company, Player driver, Vector dir) {
        if (v.fuel <= 0) {
            v.drillProgress = 0;
            warn(v, driver, "Sin combustible: click derecho con carbon o /empresa taladro cargar");
            return false;
        }

        Set<Block> front = frontBlocks(v, dir);
        List<Block> targets = new ArrayList<>();
        String blockedReason = null;
        float maxHardness = 0;
        for (Block block : front) {
            if (block.isPassable()) continue;
            String reason = cannotBreak(block, company.getId());
            if (reason != null) {
                blockedReason = reason;
                continue;
            }
            targets.add(block);
            maxHardness = Math.max(maxHardness, block.getType().getHardness());
        }

        if (blockedReason != null) warn(v, driver, blockedReason);
        if (targets.isEmpty()) {
            v.drillProgress = 0;
            return false;
        }

        int needed = Math.max(1, (int) Math.ceil(maxHardness * settings.ticksPerHardness / v.tier.potencia()));
        v.drillProgress++;
        if (v.ticks % 4 == 0 && settings.particles) {
            for (Block b : targets) {
                v.world.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3, 0, b.getBlockData());
            }
        }
        if (v.drillProgress < needed) return true;

        v.drillProgress = 0;
        double bonus = 1 + v.tier.bonusRendimiento();
        double coal = 0;
        double money = 0;
        double xp = 0;
        for (Block block : targets) {
            if (v.fuel <= 0) break;
            Material type = block.getType();
            DrillRewards.Reward reward = rewards.get(type);
            if (reward.veta()) {
                double extracted = territory.extractFromVein(block.getChunk(), settings.baseProduction * bonus);
                if (extracted > 0) {
                    coal += extracted;
                    xp += extracted * reward.xpPorUnidad();
                }
            }
            money += reward.dinero() * bonus;
            xp += reward.xp() * bonus;

            if (v.model != null && v.model.isValid()) v.model.onBlockBroken(block);
            block.setType(Material.AIR);
            v.fuel = Math.max(0, v.fuel - settings.fuelPerBlock);
            v.tripBlocks++;
        }

        if (coal > 0) {
            company.addRawCoal(coal);
            v.tripCoal += coal;
        }
        if (money > 0) economy.deposit(company.getId(), money);
        if (xp > 0) company.addXp(xp);
        if (coal > 0 || money > 0 || xp > 0) {
            plugin.companies().checkLevelUp(company);
            dirty = true;
        }
        return true;
    }

    /** Null si se puede romper; si no, el motivo para mostrarle al jugador. */
    private String cannotBreak(Block block, UUID companyId) {
        Material type = block.getType();
        if (type.getHardness() < 0 || settings.blacklist.contains(type)) return "Bloque imposible de perforar adelante";
        if (block.getState(false) instanceof TileState) return "Hay un bloque con contenido adelante (cofre, horno, etc)";
        if (!ownedBy(companyId, block.getChunk())) return "Limite del territorio de tu empresa";
        if (settings.stopAtLiquids && touchesLiquid(block)) return "Hay agua o lava detras del frente";
        return null;
    }

    private boolean touchesLiquid(Block block) {
        if (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) return true;
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = block.getRelative(face);
            if (n.isLiquid()) return true;
            if (n.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) return true;
        }
        return false;
    }

    /** Caja de ancho x alto x profundidad delante del vehiculo. El piso (y - 1) nunca se incluye. */
    private Set<Block> frontBlocks(DrillVehicle v, Vector dir) {
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        Set<Block> blocks = new LinkedHashSet<>();
        int floorY = (int) Math.floor(v.y + 0.01);
        double halfWidth = v.tier.ancho() / 2.0 - 0.5;
        List<Double> lateral = new ArrayList<>();
        for (double l = -halfWidth; l <= halfWidth + 1.0E-6; l += 0.5) lateral.add(l);
        lateral.add(-HALF_WIDTH);
        lateral.add(HALF_WIDTH);

        for (double d = HALF_LENGTH + 0.1; d <= v.tier.profundidad() + HALF_LENGTH + 1.0E-6; d += 0.4) {
            for (double l : lateral) {
                double bx = v.x + dir.getX() * d + perp.getX() * l;
                double bz = v.z + dir.getZ() * d + perp.getZ() * l;
                for (int up = 0; up < v.tier.alto(); up++) {
                    blocks.add(v.world.getBlockAt((int) Math.floor(bx), floorY + up, (int) Math.floor(bz)));
                }
            }
        }
        return blocks;
    }

    private MoveResult tryMove(DrillVehicle v, Company company, Vector dir, double nx, double nz) {
        double edge = v.speed >= 0 ? HALF_LENGTH : -HALF_LENGTH;
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        int floorY = (int) Math.floor(v.y + 0.01);

        boolean feetBlocked = false;
        boolean bodyBlocked = false;
        boolean stepRoom = true;
        for (double l : new double[]{-HALF_WIDTH, 0, HALF_WIDTH}) {
            int bx = (int) Math.floor(nx + dir.getX() * edge + perp.getX() * l);
            int bz = (int) Math.floor(nz + dir.getZ() * edge + perp.getZ() * l);
            if (!ownedBy(company.getId(), v.world.getBlockAt(bx, floorY, bz).getChunk())) {
                return MoveResult.FUERA_DE_TERRITORIO;
            }
            if (!v.world.getBlockAt(bx, floorY, bz).isPassable()) feetBlocked = true;
            if (!v.world.getBlockAt(bx, floorY + 1, bz).isPassable()) bodyBlocked = true;
            if (!v.world.getBlockAt(bx, floorY + 2, bz).isPassable()) stepRoom = false;
        }
        if (!feetBlocked && !bodyBlocked) return MoveResult.OK;
        if (feetBlocked && !bodyBlocked && stepRoom && settings.canStep && v.fallSpeed == 0) return MoveResult.STEP;
        return MoveResult.BLOQUEADO;
    }

    private void applyGravity(DrillVehicle v) {
        int bx = (int) Math.floor(v.x);
        int bz = (int) Math.floor(v.z);
        if (!v.world.getBlockAt(bx, (int) Math.floor(v.y - 0.01), bz).isPassable()) {
            v.fallSpeed = 0;
            return;
        }
        v.fallSpeed = Math.min(1.0, v.fallSpeed + 0.08);
        double ny = v.y - v.fallSpeed;
        if (ny < v.world.getMinHeight()) {
            ny = v.world.getMinHeight();
            v.fallSpeed = 0;
        }
        int landing = (int) Math.floor(ny);
        if (!v.world.getBlockAt(bx, landing, bz).isPassable()) {
            v.y = landing + 1;
            v.fallSpeed = 0;
        } else {
            v.y = ny;
        }
    }

    /** Sonido de motor al andar. Las chispas, el humo y el sonido de perforacion los pone DrillModel. */
    private void effects(DrillVehicle v, boolean drilling) {
        boolean moving = Math.abs(v.speed) > 0.01;
        if (settings.sounds && settings.engineSound != null && moving && !drilling
                && v.ticks % settings.engineInterval == 0) {
            v.world.playSound(net.kyori.adventure.sound.Sound.sound(settings.engineSound,
                    net.kyori.adventure.sound.Sound.Source.NEUTRAL, settings.volume * 0.5f, 1.0f),
                    v.x, v.y, v.z);
        }
    }

    private void warn(DrillVehicle v, Player driver, String message) {
        if (v.ticks - v.lastWarningTick < 30) return;
        v.lastWarningTick = v.ticks;
        driver.sendActionBar(Component.text(message, NamedTextColor.RED));
        if (settings.sounds && settings.blockedSound != null) {
            driver.playSound(net.kyori.adventure.sound.Sound.sound(settings.blockedSound,
                    net.kyori.adventure.sound.Sound.Source.NEUTRAL, settings.volume * 0.4f, 1.4f));
        }
    }

    private void updateBossBar(DrillVehicle v) {
        float progress = (float) Math.max(0, Math.min(1, v.fuel / v.tier.combustible()));
        v.bossBar.progress(progress);
        v.bossBar.color(progress > 0.5f ? BossBar.Color.GREEN : progress > 0.2f ? BossBar.Color.YELLOW : BossBar.Color.RED);
        v.bossBar.name(Component.text(v.tier.nombre() + "  |  Combustible " + (int) Math.ceil(v.fuel) + "/" + (int) v.tier.combustible()
                + "  |  Carbon del viaje: " + round(v.tripCoal), NamedTextColor.WHITE));
    }

    private void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        plugin.companies().save();
    }

    // ---------------------------------------------------------------
    // Taladros viejos (bote)
    // ---------------------------------------------------------------

    /** Convierte los botes-taladro de la version anterior al vehiculo nuevo, conservando empresa y tier. */
    public void convertLegacy(List<Entity> entities) {
        for (Entity entity : entities) {
            if (!(entity instanceof Boat boat)) continue;
            PersistentDataContainer pdc = boat.getPersistentDataContainer();
            String companyId = pdc.get(legacyCompanyKey, PersistentDataType.STRING);
            if (companyId == null) continue;
            Integer tier = pdc.get(legacyTierKey, PersistentDataType.INTEGER);
            int tierNumber = tier == null || !DrillTier.exists(plugin.getConfig(), tier) ? 1 : tier;

            for (NamespacedKey key : new NamespacedKey[]{legacyHullKey, legacyBitKey}) {
                String partId = pdc.get(key, PersistentDataType.STRING);
                if (partId == null) continue;
                Entity part = plugin.getServer().getEntity(UUID.fromString(partId));
                if (part != null) part.remove();
            }
            boat.eject();
            Location at = boat.getLocation();
            at.setPitch(0);
            boat.remove();

            UUID id = UUID.fromString(companyId);
            String name = plugin.companies().getById(id).map(Company::getName).orElse("?");
            createEntities(id, name, DrillTier.load(plugin.getConfig(), tierNumber), at, 0);
            plugin.getLogger().info("Taladro viejo de la empresa " + name + " convertido al vehiculo nuevo en "
                    + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ());
        }
    }

    // ---------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------

    private boolean ownedBy(UUID companyId, Chunk chunk) {
        Optional<UUID> owner = territory.getOwner(chunk);
        return owner.isPresent() && owner.get().equals(companyId);
    }

    private Optional<Entity> linked(Entity root, NamespacedKey key) {
        String id = root.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(plugin.getServer().getEntity(UUID.fromString(id)));
    }

    private static Vector direction(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static float wrapYaw(float yaw) {
        yaw %= 360f;
        if (yaw >= 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        return yaw;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private enum MoveResult { OK, STEP, BLOQUEADO, FUERA_DE_TERRITORIO }

    public enum PlacementResult {
        OK, TIER_INVALIDO, FUERA_DE_TERRITORIO, SIN_SALDO
    }

    /** Valores globales de taladros.* que no dependen del tier. */
    private record Settings(double baseProduction, double ticksPerHardness, boolean stopAtLiquids, boolean canStep,
                            double speedWithoutFuel, Set<Material> blacklist, Map<Material, Double> fuelItems,
                            double fuelPerRawCoal, double fuelPerBlock, boolean particles, boolean sounds,
                            float volume, Key engineSound, int engineInterval, Key blockedSound,
                            double seatHeight) {

        static Settings load(MiningPlugin plugin) {
            FileConfiguration c = plugin.getConfig();
            Set<Material> blacklist = EnumSet.noneOf(Material.class);
            for (String name : c.getStringList("taladros.lista-negra")) {
                Material m = Material.matchMaterial(name);
                if (m != null) blacklist.add(m);
                else plugin.getLogger().warning("taladros.lista-negra: material desconocido '" + name + "'");
            }
            Map<Material, Double> fuelItems = new HashMap<>();
            ConfigurationSection items = c.getConfigurationSection("taladros.combustible.items");
            if (items != null) {
                for (String name : items.getKeys(false)) {
                    Material m = Material.matchMaterial(name);
                    if (m != null) fuelItems.put(m, items.getDouble(name));
                    else plugin.getLogger().warning("taladros.combustible.items: material desconocido '" + name + "'");
                }
            }
            return new Settings(
                    c.getDouble("taladros.produccion-base", 8.0),
                    Math.max(0, c.getDouble("taladros.ticks-por-dureza", 4.0)),
                    c.getBoolean("taladros.frenar-ante-liquidos", true),
                    c.getBoolean("taladros.puede-subir-escalones", true),
                    Math.max(0, c.getDouble("taladros.velocidad-sin-combustible", 0.4)),
                    blacklist,
                    fuelItems,
                    c.getDouble("taladros.combustible.por-carbon-crudo", 5),
                    Math.max(0, c.getDouble("taladros.combustible.por-bloque", 1.0)),
                    c.getBoolean("taladros.efectos.particulas", true),
                    c.getBoolean("taladros.efectos.sonidos", true),
                    (float) c.getDouble("taladros.efectos.volumen", 0.8),
                    parseKey(plugin, c.getString("taladros.efectos.sonido-motor", "entity.minecart.riding")),
                    Math.max(1, c.getInt("taladros.efectos.intervalo-motor", 20)),
                    parseKey(plugin, c.getString("taladros.efectos.sonido-bloqueado", "block.anvil.land")),
                    c.getDouble("taladros.efectos.altura-asiento", 0.6)
            );
        }

        private static Key parseKey(MiningPlugin plugin, String value) {
            if (value == null || value.isBlank()) return null;
            try {
                return Key.key(value.toLowerCase(Locale.ROOT));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Sonido invalido en config: '" + value + "'");
                return null;
            }
        }
    }
}
