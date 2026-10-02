package com.isjbar.minercorp.vehicles.vehicle;

import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.api.OwnerKind;
import com.isjbar.minercorp.vehicles.fuel.FuelRegistry;
import com.isjbar.minercorp.vehicles.integration.PackModels;
import com.isjbar.minercorp.vehicles.garage.StoredVehicle;
import com.isjbar.minercorp.vehicles.model.Shape;
import com.isjbar.minercorp.vehicles.model.Shapes;
import com.isjbar.minercorp.vehicles.model.VehicleModel;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Vehiculos en el mundo: armado, garaje, combustible, carga y manejo.
 *
 * Un vehiculo son:
 * <ul>
 *   <li>la raiz, un {@link BlockDisplay} invisible que es el asiento del
 *       conductor y guarda tipo, dueno, combustible y carga en su PDC;</li>
 *   <li>un asiento invisible mas por cada acompanante;</li>
 *   <li>la carroceria {@link VehicleModel} (solo dibuja);</li>
 *   <li>una o mas {@link Interaction} invisibles a lo largo del vehiculo
 *       para hacerle click.</li>
 * </ul>
 *
 * Mientras alguien maneja, cada tick se leen sus teclas
 * ({@link Player#getCurrentInput()}) y el servidor mueve todo. La fisica es
 * la de un auto simple: acelera, frena, dobla segun la velocidad, se apoya
 * en dos puntos (adelante y atras) que siguen el terreno, sube escalones de
 * un bloque y se inclina en las subidas.
 */
public class VehicleManager {

    private static final int TELEPORT_TICKS = 2;
    /** Cuanto puede subir de golpe un punto de apoyo (un bloque entero). */
    private static final double STEP = 1.05;
    /** Cuanto puede bajar de golpe un punto de apoyo sin caer. */
    private static final double DROP = 1.0;
    /** Caja de click del panel de control del taladro. */
    private static final float CONSOLE_WIDTH = 1.1f;
    private static final float CONSOLE_HEIGHT = 1.8f;

    private final VehiclesPlugin plugin;

    private final NamespacedKey typeKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey fuelKey;
    private final NamespacedKey cargoKey;
    private final NamespacedKey modelKey;
    private final NamespacedKey seatsKey;
    private final NamespacedKey clicksKey;
    private final NamespacedKey rootKey;
    private final NamespacedKey shapeKey;
    private final NamespacedKey consoleKey;

    private final Map<UUID, VehicleState> driving = new HashMap<>();
    private final Map<UUID, Inventory> openCargo = new HashMap<>();
    private final Map<String, Shape> shapes = new HashMap<>();

    private DrillSettings drill;
    private DrillRewards rewards;
    private Effects effects;
    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private boolean companiesDirty;
    private boolean resourcePack;
    private boolean flipModels;

    public VehicleManager(VehiclesPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "tipo");
        this.ownerKey = new NamespacedKey(plugin, "dueno");
        this.fuelKey = new NamespacedKey(plugin, "combustible");
        this.cargoKey = new NamespacedKey(plugin, "carga");
        this.modelKey = new NamespacedKey(plugin, "modelo");
        this.seatsKey = new NamespacedKey(plugin, "asientos");
        this.clicksKey = new NamespacedKey(plugin, "clicks");
        this.rootKey = new NamespacedKey(plugin, "raiz");
        this.shapeKey = new NamespacedKey(plugin, "forma");
        this.consoleKey = new NamespacedKey(plugin, "panel");
    }

    public void start() {
        reloadSettings();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        long saveTicks = Math.max(20L, plugin.getConfig().getLong("guardado-segundos", 30) * 20L);
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveIfDirty, saveTicks, saveTicks);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (saveTask != null) saveTask.cancel();
        for (VehicleState v : new ArrayList<>(driving.values())) {
            Entity root = plugin.getServer().getEntity(v.rootId);
            if (root != null) root.eject();
            stopDriving(v.rootId);
        }
        for (UUID rootId : new ArrayList<>(openCargo.keySet())) closeCargo(rootId);
        saveIfDirty();
    }

    public void reloadSettings() {
        FileConfiguration c = plugin.getConfig();
        this.drill = DrillSettings.load(c, plugin.getLogger());
        this.rewards = DrillRewards.load(c, plugin.getLogger());
        this.effects = Effects.load(c, plugin);
        String usar = c.getString("resource-pack.usar", "auto").toLowerCase(Locale.ROOT);
        this.resourcePack = switch (usar) {
            case "true", "si", "yes" -> true;
            case "false", "no" -> false;
            default -> PackModels.active();
        };
        this.flipModels = c.getBoolean("resource-pack.girar-180", false);
        this.shapes.clear();
    }

    /** True si los vehiculos se dibujan con los modelos del resource pack. */
    public boolean usesResourcePack() {
        return resourcePack;
    }

    public Shape shapeOf(VehicleType type) {
        return shapes.computeIfAbsent(type.modelo(), id -> Shapes.get(id, resourcePack, flipModels));
    }

    // ---------------------------------------------------------------
    // Armado y desarmado
    // ---------------------------------------------------------------

    /**
     * Pone un vehiculo en el mundo con su centro en {@code center} (a ras del
     * piso) mirando hacia el yaw de esa posicion.
     */
    public BlockDisplay spawn(VehicleType type, UUID ownerId, Location center, double fuel, byte[] cargo) {
        Shape shape = shapeOf(type);
        World world = center.getWorld();
        Location at = center.clone();
        at.setPitch(0);

        Vector3f driverSeat = shape.seats().get(0);
        BlockDisplay root = world.spawn(VehicleModel.local(at, driverSeat.x, driverSeat.y, driverSeat.z), BlockDisplay.class, d -> {
            d.setBlock(Material.AIR.createBlockData());
            d.setTeleportDuration(TELEPORT_TICKS);
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            pdc.set(typeKey, PersistentDataType.STRING, type.id());
            pdc.set(ownerKey, PersistentDataType.STRING, ownerId.toString());
            pdc.set(fuelKey, PersistentDataType.DOUBLE, Math.min(fuel, type.tanque()));
            pdc.set(shapeKey, PersistentDataType.STRING, shape.signature());
            if (cargo != null) pdc.set(cargoKey, PersistentDataType.BYTE_ARRAY, cargo);
        });
        String rootId = root.getUniqueId().toString();

        VehicleModel model = VehicleModel.spawn(plugin, shape, type, at,
                Component.text(type.nombre() + " - " + ownerName(type, ownerId), NamedTextColor.AQUA));
        Entity modelRoot = plugin.getServer().getEntity(model.rootId());
        if (modelRoot != null) modelRoot.getPersistentDataContainer().set(rootKey, PersistentDataType.STRING, rootId);

        List<String> seatIds = new ArrayList<>();
        for (int i = 1; i < shape.seats().size(); i++) {
            Vector3f s = shape.seats().get(i);
            BlockDisplay seat = world.spawn(VehicleModel.local(at, s.x, s.y, s.z), BlockDisplay.class, d -> {
                d.setBlock(Material.AIR.createBlockData());
                d.setTeleportDuration(TELEPORT_TICKS);
                d.getPersistentDataContainer().set(rootKey, PersistentDataType.STRING, rootId);
            });
            seatIds.add(seat.getUniqueId().toString());
        }

        List<String> clickIds = new ArrayList<>();
        List<Location> clicks = clickPositions(shape, at);
        for (int n = 0; n < clicks.size(); n++) {
            // La ultima es el panel de control, si la forma tiene.
            boolean console = shape.console() != null && n == clicks.size() - 1;
            Interaction click = world.spawn(clicks.get(n), Interaction.class, i -> {
                i.setInteractionWidth(console ? CONSOLE_WIDTH : shape.interactionWidth());
                i.setInteractionHeight(console ? CONSOLE_HEIGHT : shape.interactionHeight());
                i.setResponsive(true);
                i.getPersistentDataContainer().set(rootKey, PersistentDataType.STRING, rootId);
                if (console) i.getPersistentDataContainer().set(consoleKey, PersistentDataType.BYTE, (byte) 1);
            });
            clickIds.add(click.getUniqueId().toString());
        }

        PersistentDataContainer pdc = root.getPersistentDataContainer();
        pdc.set(modelKey, PersistentDataType.STRING, model.rootId().toString());
        pdc.set(seatsKey, PersistentDataType.STRING, String.join(",", seatIds));
        pdc.set(clicksKey, PersistentDataType.STRING, String.join(",", clickIds));
        plugin.garage().setParked(ownerId, root.getUniqueId(), type.id(), at);
        return root;
    }

    /** Elimina el vehiculo completo del mundo (sin guardarlo). */
    public void remove(BlockDisplay root) {
        stopDriving(root.getUniqueId());
        closeCargo(root.getUniqueId());
        root.eject();
        modelOf(root).ifPresent(VehicleModel::remove);
        for (Entity e : linked(root, seatsKey)) {
            e.eject();
            e.remove();
        }
        for (Entity e : linked(root, clicksKey)) e.remove();
        ownerOf(root).ifPresent(owner -> plugin.garage().removeParked(owner, root.getUniqueId()));
        root.remove();
    }

    /**
     * Guarda el vehiculo en el garaje de su dueno (tipo, combustible y carga)
     * y lo saca del mundo. Devuelve false si hay alguien arriba.
     */
    public boolean store(BlockDisplay root) {
        if (!root.getPassengers().isEmpty()) return false;
        for (Entity seat : linked(root, seatsKey)) {
            if (!seat.getPassengers().isEmpty()) return false;
        }
        Optional<UUID> owner = ownerOf(root);
        Optional<VehicleType> type = typeOf(root);
        if (owner.isEmpty() || type.isEmpty()) return false;
        closeCargo(root.getUniqueId());
        byte[] cargo = root.getPersistentDataContainer().get(cargoKey, PersistentDataType.BYTE_ARRAY);
        StoredVehicle stored = new StoredVehicle(type.get().id(), fuelOf(root),
                cargo == null ? null : Base64.getEncoder().encodeToString(cargo));
        remove(root);
        plugin.garage().add(owner.get(), stored);
        plugin.garage().save();
        return true;
    }

    /**
     * Saca del garaje del dueno el vehiculo numero {@code index} delante del
     * jugador. No cobra nada: ya estaba pago.
     */
    public DeployResult deploy(Player player, UUID ownerId, int index) {
        List<StoredVehicle> list = plugin.garage().stored(ownerId);
        if (index < 0 || index >= list.size()) return DeployResult.NO_EXISTE;
        StoredVehicle stored = list.get(index);
        Optional<VehicleType> typeOpt = plugin.types().get(stored.type());
        if (typeOpt.isEmpty()) return DeployResult.TIPO_DESCONOCIDO;
        VehicleType type = typeOpt.get();
        Shape shape = shapeOf(type);

        Location eye = player.getLocation();
        float yaw = eye.getYaw();
        Vector dir = direction(yaw);
        // Primero delante del jugador; si no entra, donde esta parado.
        List<Location> candidates = List.of(
                new Location(eye.getWorld(), eye.getX() + dir.getX() * (shape.halfLength() + 1.2), eye.getY(),
                        eye.getZ() + dir.getZ() * (shape.halfLength() + 1.2), yaw, 0),
                new Location(eye.getWorld(), eye.getX(), eye.getY(), eye.getZ(), yaw, 0));
        DeployResult failure = DeployResult.SIN_LUGAR;
        for (Location at : candidates) {
            Placement p = evaluate(shape, type, type.isDrill() ? ownerId : null, at.getWorld(), at.getX(), at.getZ(), yaw,
                    at.getY(), at.getY(), 0, true);
            if (!p.ok()) {
                if (p.reason() == Obstacle.TERRITORIO) failure = DeployResult.FUERA_DE_TERRITORIO;
                continue;
            }
            double yF = Double.isNaN(p.yFront()) ? at.getY() : p.yFront();
            double yR = Double.isNaN(p.yRear()) ? at.getY() : p.yRear();
            Location center = new Location(at.getWorld(), at.getX(), Math.max(yF, yR), at.getZ(), yaw, 0);
            plugin.garage().take(ownerId, index);
            byte[] cargo = stored.cargo() == null ? null : Base64.getDecoder().decode(stored.cargo());
            spawn(type, ownerId, center, stored.fuel(), cargo);
            plugin.garage().save();
            return DeployResult.OK;
        }
        return failure;
    }

    public enum DeployResult {
        OK("Vehiculo afuera. Subite con click derecho."),
        NO_EXISTE("Ese vehiculo ya no esta en el garaje."),
        TIPO_DESCONOCIDO("Ese tipo de vehiculo ya no existe en el config."),
        FUERA_DE_TERRITORIO("El taladro solo se puede sacar dentro del territorio de tu empresa."),
        SIN_LUGAR("No hay lugar: busca un espacio abierto y plano.");

        private final String message;

        DeployResult(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    /** Vehiculo mas cercano dentro del radio que el jugador puede manejar. */
    public Optional<BlockDisplay> nearestUsable(Player player, double radius) {
        BlockDisplay nearest = null;
        double best = radius * radius;
        Location at = player.getLocation();
        for (Entity e : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            Optional<BlockDisplay> root = rootOf(e);
            if (root.isEmpty() || !canUse(player, root.get())) continue;
            double d = e.getLocation().distanceSquared(at);
            if (d <= best) {
                best = d;
                nearest = root.get();
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** Borra piezas cercanas cuyo vehiculo ya no existe (por ejemplo, si se uso /kill). */
    public int removeOrphanParts(Location location, double radius) {
        int removed = 0;
        for (Entity e : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            String rootId = e.getPersistentDataContainer().get(rootKey, PersistentDataType.STRING);
            if (rootId == null) {
                if (VehicleModel.isModelPart(plugin, e) && !isAnyModelRootLinked(e)) {
                    e.remove();
                    removed++;
                }
                continue;
            }
            if (plugin.getServer().getEntity(UUID.fromString(rootId)) == null) {
                if (VehicleModel.isModelPart(plugin, e)) {
                    for (UUID id : VehicleModel.partIds(plugin, e)) {
                        Entity part = plugin.getServer().getEntity(id);
                        if (part != null) part.remove();
                    }
                } else {
                    e.remove();
                }
                removed++;
            }
        }
        return removed;
    }

    /** Piezas sueltas de un modelo cuyo chasis ya no esta. */
    private boolean isAnyModelRootLinked(Entity part) {
        // Solo las raices de modelo tienen rootKey; una pieza suelta sin raiz de
        // modelo cercana que la liste se considera huerfana.
        for (Entity e : part.getNearbyEntities(4, 4, 4)) {
            if (!e.getPersistentDataContainer().has(rootKey, PersistentDataType.STRING)) continue;
            if (!VehicleModel.isModelPart(plugin, e)) continue;
            if (VehicleModel.partIds(plugin, e).contains(part.getUniqueId())) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Datos de la raiz
    // ---------------------------------------------------------------

    /** Dado un asiento, una caja de click, la carroceria o la raiz, devuelve la raiz. */
    public Optional<BlockDisplay> rootOf(Entity entity) {
        if (isRoot(entity)) return Optional.of((BlockDisplay) entity);
        String rootId = entity.getPersistentDataContainer().get(rootKey, PersistentDataType.STRING);
        if (rootId == null) return Optional.empty();
        Entity root = plugin.getServer().getEntity(UUID.fromString(rootId));
        return root instanceof BlockDisplay display && isRoot(display) ? Optional.of(display) : Optional.empty();
    }

    public boolean isRoot(Entity entity) {
        return entity instanceof BlockDisplay && entity.getPersistentDataContainer().has(typeKey, PersistentDataType.STRING);
    }

    public Optional<VehicleType> typeOf(Entity root) {
        return plugin.types().get(root.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING));
    }

    public Optional<UUID> ownerOf(Entity root) {
        String id = root.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    public double fuelOf(BlockDisplay root) {
        VehicleState active = driving.get(root.getUniqueId());
        if (active != null) return active.fuel;
        Double fuel = root.getPersistentDataContainer().get(fuelKey, PersistentDataType.DOUBLE);
        return fuel == null ? 0 : fuel;
    }

    private void setFuel(BlockDisplay root, double fuel) {
        VehicleState active = driving.get(root.getUniqueId());
        if (active != null) active.fuel = fuel;
        root.getPersistentDataContainer().set(fuelKey, PersistentDataType.DOUBLE, fuel);
    }

    /** True si el jugador puede manejar, cargar y guardar este vehiculo. */
    public boolean canUse(Player player, Entity root) {
        Optional<UUID> owner = ownerOf(root);
        if (owner.isEmpty()) return false;
        if (owner.get().equals(player.getUniqueId())) return true;
        return plugin.mining().companies().getById(owner.get())
                .map(c -> c.isMember(player.getUniqueId())).orElse(false);
    }

    private String ownerName(VehicleType type, UUID ownerId) {
        if (type.dueno() == OwnerKind.EMPRESA) {
            return plugin.mining().companies().getById(ownerId).map(Company::getName).orElse("?");
        }
        OfflinePlayer p = plugin.getServer().getOfflinePlayer(ownerId);
        return p.getName() == null ? "?" : p.getName();
    }

    private Optional<VehicleModel> modelOf(Entity root) {
        String id = root.getPersistentDataContainer().get(modelKey, PersistentDataType.STRING);
        Optional<VehicleType> type = typeOf(root);
        if (id == null) return Optional.empty();
        if (type.isPresent()) return VehicleModel.find(plugin, shapeOf(type.get()), UUID.fromString(id));
        // Tipo borrado del config: igual hay que poder desarmarlo.
        return VehicleModel.find(plugin, Shapes.get("camioneta", false, false), UUID.fromString(id));
    }

    private List<Entity> linked(Entity root, NamespacedKey key) {
        List<Entity> out = new ArrayList<>();
        String ids = root.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (ids == null || ids.isBlank()) return out;
        for (String id : ids.split(",")) {
            if (id.isBlank()) continue;
            Entity e = plugin.getServer().getEntity(UUID.fromString(id));
            if (e != null) out.add(e);
        }
        return out;
    }

    /** True si todas las entidades del vehiculo estan cargadas (si no, desarmarlo dejaria restos). */
    private boolean allPartsLoaded(BlockDisplay root) {
        List<String> ids = new ArrayList<>();
        for (NamespacedKey key : List.of(seatsKey, clicksKey, modelKey)) {
            String list = root.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (list != null) for (String id : list.split(",")) if (!id.isBlank()) ids.add(id);
        }
        for (String id : ids) {
            Entity e = plugin.getServer().getEntity(UUID.fromString(id));
            if (e == null) return false;
            if (VehicleModel.isModelPart(plugin, e)) {
                for (UUID part : VehicleModel.partIds(plugin, e)) {
                    if (plugin.getServer().getEntity(part) == null) return false;
                }
            }
        }
        return true;
    }

    /** True si es la caja de click del panel de control de un taladro. */
    public boolean isConsole(Entity entity) {
        return entity.getPersistentDataContainer().has(consoleKey, PersistentDataType.BYTE);
    }

    /**
     * Vuelve a armar los vehiculos estacionados cuya forma cambio (otra
     * version del plugin, o se paso de bloques a resource pack o al reves),
     * en el mismo lugar y con el mismo combustible y carga.
     */
    public int refreshOutdated(Collection<? extends Entity> entities) {
        int rebuilt = 0;
        for (Entity e : entities) {
            if (!isRoot(e) || !e.isValid() || driving.containsKey(e.getUniqueId())) continue;
            BlockDisplay root = (BlockDisplay) e;
            Optional<VehicleType> type = typeOf(root);
            Optional<UUID> owner = ownerOf(root);
            if (type.isEmpty() || owner.isEmpty()) continue;
            Shape shape = shapeOf(type.get());
            if (shape.signature().equals(root.getPersistentDataContainer().get(shapeKey, PersistentDataType.STRING))) continue;
            if (!root.getPassengers().isEmpty() || !allPartsLoaded(root)) continue;
            if (linked(root, seatsKey).stream().anyMatch(seat -> !seat.getPassengers().isEmpty())) continue;
            // El centro del vehiculo es donde esta la carroceria (la raiz es el asiento del conductor).
            Location center = modelOf(root).map(VehicleModel::location).orElse(root.getLocation());
            if (!center.isChunkLoaded()) continue;
            closeCargo(root.getUniqueId());
            double fuel = fuelOf(root);
            byte[] cargo = root.getPersistentDataContainer().get(cargoKey, PersistentDataType.BYTE_ARRAY);
            remove(root);
            spawn(type.get(), owner.get(), center, fuel, cargo);
            rebuilt++;
        }
        return rebuilt;
    }

    // ---------------------------------------------------------------
    // Combustible
    // ---------------------------------------------------------------

    public record RefuelResult(int used, String acceptedNames) {
    }

    /** Carga combustible desde el item en la mano. used = items consumidos (0 si no es combustible de este vehiculo o esta lleno). */
    public RefuelResult refuelFromItem(BlockDisplay root, ItemStack item) {
        Optional<VehicleType> type = typeOf(root);
        if (type.isEmpty()) return new RefuelResult(0, "");
        FuelRegistry fuels = plugin.fuels();
        Optional<FuelRegistry.Fuel> fuel = fuels.match(item, type.get().combustibles());
        String accepted = fuels.names(type.get().combustibles());
        if (fuel.isEmpty()) return new RefuelResult(-1, accepted);
        double current = fuelOf(root);
        double space = type.get().tanque() - current;
        int usable = (int) Math.min(item.getAmount(), Math.floor(space / fuel.get().litrosPorItem()));
        if (usable <= 0) return new RefuelResult(0, accepted);
        setFuel(root, current + usable * fuel.get().litrosPorItem());
        return new RefuelResult(usable, accepted);
    }

    /** Carga combustible con todo lo que sirva del inventario del jugador. Devuelve los items usados. */
    public int refuelFromInventory(Player player, BlockDisplay root) {
        int used = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.isEmpty()) continue;
            RefuelResult r = refuelFromItem(root, item);
            if (r.used() <= 0) continue;
            used += r.used();
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) item.setAmount(item.getAmount() - r.used());
        }
        player.getInventory().setStorageContents(contents);
        return used;
    }

    /** Carga combustible usando carbon crudo de la empresa duena. Devuelve cuanto carbon crudo uso. */
    public double refuelFromRawCoal(Company company, BlockDisplay root, double maxRawCoal) {
        Optional<VehicleType> type = typeOf(root);
        if (type.isEmpty() || !type.get().combustibles().contains("carbon")) return 0;
        double perUnit = plugin.fuels().litrosPorCarbonCrudo();
        if (perUnit <= 0) return 0;
        double fuel = fuelOf(root);
        double needed = (type.get().tanque() - fuel) / perUnit;
        double used = Math.min(Math.min(maxRawCoal, needed), company.getRawCoal());
        if (used <= 0 || !company.removeRawCoal(used)) return 0;
        setFuel(root, fuel + used * perUnit);
        companiesDirty = true;
        return used;
    }

    // ---------------------------------------------------------------
    // Carga
    // ---------------------------------------------------------------

    /** Inventario de la carga de un vehiculo, para saber de cual es al cerrarlo. */
    public record CargoHolder(UUID rootId) implements org.bukkit.inventory.InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void openCargo(Player player, BlockDisplay root) {
        Optional<VehicleType> type = typeOf(root);
        if (type.isEmpty() || type.get().carga() <= 0) {
            player.sendActionBar(Component.text("Este vehiculo no lleva carga", NamedTextColor.RED));
            return;
        }
        Inventory inv = openCargo.computeIfAbsent(root.getUniqueId(), id -> {
            Inventory created = plugin.getServer().createInventory(new CargoHolder(id), type.get().carga(),
                    Component.text("Carga - " + type.get().nombre()));
            byte[] bytes = root.getPersistentDataContainer().get(cargoKey, PersistentDataType.BYTE_ARRAY);
            if (bytes != null) {
                ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
                for (int i = 0; i < items.length && i < created.getSize(); i++) {
                    if (items[i] != null && !items[i].isEmpty()) created.setItem(i, items[i]);
                }
            }
            return created;
        });
        player.openInventory(inv);
    }

    /** Llamarlo al cerrar un inventario de carga: si ya nadie lo mira, lo guarda en la raiz. */
    public void onCargoClosed(UUID rootId, Inventory inv, HumanEntity closing) {
        long others = inv.getViewers().stream().filter(v -> !v.equals(closing)).count();
        if (others > 0) return;
        saveCargo(rootId, inv);
        openCargo.remove(rootId);
    }

    private void closeCargo(UUID rootId) {
        Inventory inv = openCargo.remove(rootId);
        if (inv == null) return;
        saveCargo(rootId, inv);
        for (HumanEntity viewer : new ArrayList<>(inv.getViewers())) viewer.closeInventory();
    }

    private void saveCargo(UUID rootId, Inventory inv) {
        Entity root = plugin.getServer().getEntity(rootId);
        if (root == null) return;
        ItemStack[] items = inv.getContents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) items[i] = ItemStack.empty();
        }
        root.getPersistentDataContainer().set(cargoKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(items));
    }

    // ---------------------------------------------------------------
    // Subirse / bajarse
    // ---------------------------------------------------------------

    public enum SitResult { CONDUCTOR, ACOMPANANTE, SIN_LUGAR }

    /** Sube al jugador: al volante si esta libre y lo puede manejar, si no en un asiento libre. */
    public SitResult sit(Player player, BlockDisplay root) {
        if (root.getPassengers().isEmpty() && canUse(player, root) && typeOf(root).isPresent()) {
            if (root.addPassenger(player)) {
                startDriving(root, player);
                return SitResult.CONDUCTOR;
            }
        }
        for (Entity seat : linked(root, seatsKey)) {
            if (seat.getPassengers().isEmpty() && seat.addPassenger(player)) return SitResult.ACOMPANANTE;
        }
        return SitResult.SIN_LUGAR;
    }

    public void startDriving(BlockDisplay root, Player player) {
        Optional<VehicleType> typeOpt = typeOf(root);
        Optional<UUID> owner = ownerOf(root);
        if (typeOpt.isEmpty() || owner.isEmpty()) return;
        VehicleType type = typeOpt.get();
        Shape shape = shapeOf(type);
        BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);

        VehicleState v = new VehicleState(root.getUniqueId(), type, shape, owner.get(), player.getUniqueId(), root.getWorld(), bar);
        v.model = modelOf(root).orElse(null);
        Location center = v.model != null ? v.model.location() : root.getLocation();
        v.x = center.getX();
        v.z = center.getZ();
        v.yFront = center.getY();
        v.yRear = center.getY();
        v.yaw = center.getYaw();
        v.fuel = fuelOf(root);
        driving.put(root.getUniqueId(), v);

        updateBossBar(v);
        player.showBossBar(bar);
        player.sendActionBar(Component.text(type.isDrill()
                ? "W/S avanzar - A/D girar - Espacio frenar - Shift bajarse"
                : "W acelerar - S frenar/reversa - A/D doblar - Espacio freno de mano - Shift bajarse", NamedTextColor.GRAY));
    }

    public void stopDriving(UUID rootId) {
        VehicleState v = driving.remove(rootId);
        if (v == null) return;
        Entity root = plugin.getServer().getEntity(rootId);
        if (root != null) {
            root.getPersistentDataContainer().set(fuelKey, PersistentDataType.DOUBLE, v.fuel);
            plugin.garage().setParked(v.ownerId, rootId, v.type.id(), new Location(v.world, v.x, v.y(), v.z));
        }
        if (v.model != null && v.model.isValid()) {
            v.model.setDrilling(false);
            v.model.moveTo(new Location(v.world, v.x, v.y(), v.z, v.yaw, v.pitch()), 0, 0);
        }
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

    /** True si el jugador va sentado en un vehiculo, manejando o de acompanante. */
    public boolean isRiding(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null && rootOf(vehicle).isPresent();
    }

    // ---------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------

    private void tick() {
        List<UUID> finished = new ArrayList<>();
        for (VehicleState v : driving.values()) {
            Entity rootEntity = plugin.getServer().getEntity(v.rootId);
            Player driver = plugin.getServer().getPlayer(v.driverId);
            if (!(rootEntity instanceof BlockDisplay root) || !root.isValid() || driver == null
                    || !root.getPassengers().contains(driver)) {
                finished.add(v.rootId);
                continue;
            }
            Company company = null;
            if (v.type.isDrill()) {
                company = plugin.mining().companies().getById(v.ownerId).orElse(null);
                if (company == null) {
                    finished.add(v.rootId);
                    continue;
                }
            }
            tickVehicle(v, root, driver, company);
        }
        for (UUID id : finished) stopDriving(id);
    }

    private void tickVehicle(VehicleState v, BlockDisplay root, Player driver, Company company) {
        v.ticks++;
        Input input = driver.getCurrentInput();
        VehicleType type = v.type;
        int turn = (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0);

        // Perforacion: solo mientras se aprieta W y hay combustible
        boolean drilling = type.isDrill() && input.isForward() && drill(v, company, driver);
        if (type.isDrill() && !input.isForward()) v.drillProgress = 0;

        // Velocidad
        double max = type.velocidad() * (v.fuel > 0 ? 1.0 : type.velocidadSinCombustible());
        if (max <= 0 && (input.isForward() || input.isBackward())) {
            warn(v, driver, "Sin combustible: click derecho con " + plugin.fuels().names(type.combustibles()));
        }
        double target;
        double rate;
        if (input.isJump()) {
            target = 0;
            rate = type.frenado() * 2;
        } else if (input.isForward()) {
            target = max;
            rate = v.speed < 0 ? type.frenado() : type.aceleracion();
        } else if (input.isBackward()) {
            if (v.speed > 0.01) {
                target = 0;
                rate = type.frenado();
            } else {
                target = -max * type.reversa();
                rate = type.aceleracion();
            }
        } else {
            target = 0;
            // Sin pedales: el taladro frena solo, los de ruedas ruedan un poco.
            rate = type.isDrill() ? type.frenado() : type.aceleracion() * 0.6;
        }
        if (v.speed < target) v.speed = Math.min(target, v.speed + rate);
        else if (v.speed > target) v.speed = Math.max(target, v.speed - rate);

        // Direccion: las orugas giran en el lugar; las ruedas solo doblan andando.
        float yawDelta;
        if (type.isDrill()) {
            yawDelta = (float) (turn * type.giro());
        } else {
            double factor = Math.min(1.0, Math.abs(v.speed) / (type.velocidad() * 0.35));
            yawDelta = (float) (turn * type.giro() * factor * Math.signum(v.speed));
        }
        UUID territoryOwner = type.isDrill() ? v.ownerId : null;
        if (Math.abs(yawDelta) > 1.0E-3) {
            float newYaw = wrapYaw(v.yaw + yawDelta);
            Placement turned = evaluate(v.shape, type, territoryOwner, v.world, v.x, v.z, newYaw, v.yFront, v.yRear, 0, true);
            if (turned.ok()) v.yaw = newYaw;
        }

        // Movimiento
        if (Math.abs(v.speed) > 1.0E-3) {
            Vector dir = direction(v.yaw);
            double nx = v.x + dir.getX() * v.speed;
            double nz = v.z + dir.getZ() * v.speed;
            Placement moved = evaluate(v.shape, type, territoryOwner, v.world, nx, nz, v.yaw, v.yFront, v.yRear,
                    (int) Math.signum(v.speed), false);
            if (moved.ok()) {
                double dist = Math.abs(v.speed);
                v.x = nx;
                v.z = nz;
                v.tripDistance += dist;
                if (type.consumo() > 0 && v.fuel > 0) v.fuel = Math.max(0, v.fuel - dist * type.consumo());
            } else {
                v.speed = 0;
                if (moved.reason() == Obstacle.TERRITORIO) warn(v, driver, "Limite del territorio de tu empresa");
                else if (moved.reason() == Obstacle.AGUA) warn(v, driver, "No puede meterse al agua");
            }
        }
        applyGround(v);

        // Mover entidades
        Location center = new Location(v.world, v.x, v.y(), v.z, v.yaw, v.pitch());
        Vector3f s0 = v.shape.seats().get(0);
        Location seatLoc = VehicleModel.local(center, s0.x, s0.y, s0.z);
        seatLoc.setYaw(v.yaw);
        seatLoc.setPitch(0);
        boolean changed = root.getLocation().distanceSquared(seatLoc) > 1.0E-6
                || Math.abs(root.getLocation().getYaw() - v.yaw) > 0.01f;
        if (changed) {
            root.teleport(seatLoc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            List<Entity> seats = linked(root, seatsKey);
            for (int i = 0; i < seats.size() && i + 1 < v.shape.seats().size(); i++) {
                Vector3f s = v.shape.seats().get(i + 1);
                Location l = VehicleModel.local(center, s.x, s.y, s.z);
                l.setYaw(v.yaw);
                l.setPitch(0);
                seats.get(i).teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            List<Location> clicks = clickPositions(v.shape, center);
            List<Entity> clickEntities = linked(root, clicksKey);
            for (int i = 0; i < clickEntities.size() && i < clicks.size(); i++) {
                clickEntities.get(i).teleport(clicks.get(i));
            }
        }
        if (v.model != null && v.model.isValid()) {
            v.model.moveTo(center, v.speed, turn);
            v.model.setDrilling(drilling);
            if (!type.isDrill() && input.isForward() && v.fuel > 0 && v.ticks % 3 == 0 && effects.particles()) {
                v.model.exhaust();
            }
        }

        engineSound(v, drilling);
        if (v.ticks % 10 == 0) updateBossBar(v);
    }

    // ---------------------------------------------------------------
    // Fisica
    // ---------------------------------------------------------------

    /** Por que no se puede ocupar un lugar. */
    enum Obstacle { NADA, PARED, AGUA, TERRITORIO }

    /**
     * @param yFront altura del piso bajo el apoyo delantero (NaN: no hay piso cerca, cae)
     * @param yRear  idem el trasero
     */
    record Placement(boolean ok, Obstacle reason, double yFront, double yRear) {
        static Placement blocked(Obstacle reason) {
            return new Placement(false, reason, Double.NaN, Double.NaN);
        }
    }

    /**
     * Mira si el vehiculo entra con su centro en (x, z) y ese yaw.
     *
     * @param territoryOwner si no es null, todo el vehiculo tiene que quedar en territorio de ese dueno
     * @param leading        1 o -1 para revisar la carroceria del frente o de la cola (al moverse), 0 nada
     * @param corners        revisar las cuatro esquinas (al girar o al sacarlo del garaje)
     */
    private Placement evaluate(Shape shape, VehicleType type, UUID territoryOwner, World world, double x, double z,
                               float yaw, double curFront, double curRear, int leading, boolean corners) {
        Vector dir = direction(yaw);
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        double c = shape.contact();

        if (territoryOwner != null) {
            for (double[] p : new double[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                double px = x + dir.getX() * shape.halfLength() * p[0] + perp.getX() * shape.halfWidth() * p[1];
                double pz = z + dir.getZ() * shape.halfLength() * p[0] + perp.getZ() * shape.halfWidth() * p[1];
                if (!ownedBy(territoryOwner, world.getBlockAt((int) Math.floor(px), 0, (int) Math.floor(pz)).getChunk())) {
                    return Placement.blocked(Obstacle.TERRITORIO);
                }
            }
        }

        double yF = surface(world, x + dir.getX() * c, z + dir.getZ() * c, curFront);
        double yR = surface(world, x - dir.getX() * c, z - dir.getZ() * c, curRear);
        if (yF == WALL || yR == WALL) return Placement.blocked(Obstacle.PARED);
        if (yF == WATER || yR == WATER) return Placement.blocked(Obstacle.AGUA);

        // Carroceria: los bloques por encima del piso no pueden estar ocupados.
        List<double[]> samples = new ArrayList<>();
        if (leading != 0) {
            double lz = shape.halfLength() * leading;
            for (double lx = -shape.halfWidth(); lx <= shape.halfWidth() + 1.0E-6; lx += 0.5) samples.add(new double[]{lx, lz});
            samples.add(new double[]{shape.halfWidth(), lz});
        }
        if (corners) {
            for (double sl : new double[]{1, -1}) {
                for (double sw : new double[]{1, -1}) samples.add(new double[]{shape.halfWidth() * sw, shape.halfLength() * sl});
            }
            samples.add(new double[]{0, shape.halfLength()});
            samples.add(new double[]{0, -shape.halfLength()});
        }
        double baseF = Double.isNaN(yF) ? curFront : yF;
        double baseR = Double.isNaN(yR) ? curRear : yR;
        for (double[] s : samples) {
            double lx = s[0], lz = s[1];
            double t = Math.max(0, Math.min(1, (lz + c) / (2 * c)));
            double base = baseR + (baseF - baseR) * t;
            double px = x + dir.getX() * lz + perp.getX() * lx;
            double pz = z + dir.getZ() * lz + perp.getZ() * lx;
            int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
            int floor = (int) Math.floor(base + 0.01);
            for (int k = 1; k < shape.height(); k++) {
                Block b = world.getBlockAt(bx, floor + k, bz);
                if (b.isLiquid()) return Placement.blocked(Obstacle.AGUA);
                if (!b.isPassable()) return Placement.blocked(Obstacle.PARED);
            }
        }
        return new Placement(true, Obstacle.NADA, yF, yR);
    }

    private static final double WALL = Double.POSITIVE_INFINITY;
    private static final double WATER = Double.NEGATIVE_INFINITY;

    /**
     * Altura del piso bajo (x, z) cerca de {@code cur}: sube hasta {@link #STEP}
     * y baja hasta {@link #DROP}. WALL si hay algo mas alto, WATER si hay
     * liquido, NaN si no hay piso en ese rango (cae).
     */
    private static double surface(World world, double x, double z, double cur) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int top = (int) Math.floor(cur + STEP);
        int bottom = Math.max(world.getMinHeight(), (int) Math.floor(cur - DROP - 1.0E-3));
        for (int y = top; y >= bottom; y--) {
            Block b = world.getBlockAt(bx, y, bz);
            if (b.isLiquid() || (b.getBlockData() instanceof Waterlogged w && w.isWaterlogged())) return WATER;
            if (b.isPassable()) continue;
            double surface = b.getBoundingBox().getMaxY();
            if (surface > cur + STEP + 1.0E-3) return WALL;
            if (surface < cur - DROP - 1.0E-3) return Double.NaN;
            return surface;
        }
        return Double.NaN;
    }

    /** Apoya el vehiculo en el piso bajo sus dos puntos de apoyo, o lo deja caer. */
    private void applyGround(VehicleState v) {
        Vector dir = direction(v.yaw);
        double c = v.shape.contact();
        double yF = surface(v.world, v.x + dir.getX() * c, v.z + dir.getZ() * c, v.yFront);
        double yR = surface(v.world, v.x - dir.getX() * c, v.z - dir.getZ() * c, v.yRear);
        // Pared o agua justo donde esta apoyado (alguien puso un bloque): se queda como esta.
        if (yF == WALL || yF == WATER) yF = v.yFront;
        if (yR == WALL || yR == WATER) yR = v.yRear;
        boolean frontAir = Double.isNaN(yF);
        boolean rearAir = Double.isNaN(yR);

        if (frontAir && rearAir) {
            v.fallSpeed = Math.min(DROP, v.fallSpeed + 0.08);
            v.yFront -= v.fallSpeed;
            v.yRear -= v.fallSpeed;
        } else {
            v.fallSpeed = 0;
            // El apoyo que quedo en el aire baja de a un bloque por tick.
            v.yFront = frontAir ? v.yFront - DROP : yF;
            v.yRear = rearAir ? v.yRear - DROP : yR;
            // Mas de 45 grados: cae nivelado desde el apoyo mas bajo.
            if (Math.abs(v.yFront - v.yRear) > v.shape.contact() * 2) {
                double low = Math.min(v.yFront, v.yRear);
                v.yFront = low;
                v.yRear = low;
            }
        }
        double min = v.world.getMinHeight();
        if (v.yFront < min || v.yRear < min) {
            v.yFront = Math.max(min, v.yFront);
            v.yRear = Math.max(min, v.yRear);
            v.fallSpeed = 0;
        }
    }

    // ---------------------------------------------------------------
    // Taladro
    // ---------------------------------------------------------------

    private boolean drill(VehicleState v, Company company, Player driver) {
        if (v.fuel <= 0) {
            v.drillProgress = 0;
            warn(v, driver, "Sin combustible: click derecho con " + plugin.fuels().names(v.type.combustibles())
                    + " o /vehiculo cargar");
            return false;
        }

        Set<Block> front = frontBlocks(v);
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

        int needed = Math.max(1, (int) Math.ceil(maxHardness * drill.ticksPerHardness() / v.type.perforacion().potencia()));
        v.drillProgress++;
        if (v.ticks % 4 == 0 && effects.particles()) {
            for (Block b : targets) {
                v.world.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3, 0, b.getBlockData());
            }
        }
        if (v.drillProgress < needed) return true;

        v.drillProgress = 0;
        double bonus = 1 + v.type.perforacion().bonusRendimiento();
        double coal = 0;
        double money = 0;
        double xp = 0;
        for (Block block : targets) {
            if (v.fuel <= 0) break;
            DrillRewards.Reward reward = rewards.get(block.getType());
            if (reward.veta()) {
                double extracted = plugin.territory().extractFromVein(block.getChunk(), drill.baseProduction() * bonus);
                if (extracted > 0) {
                    coal += extracted;
                    xp += extracted * reward.xpPorUnidad();
                }
            }
            money += reward.dinero() * bonus;
            xp += reward.xp() * bonus;

            if (v.model != null && v.model.isValid()) v.model.onBlockBroken(block);
            block.setType(Material.AIR);
            v.fuel = Math.max(0, v.fuel - drill.fuelPerBlock());
            v.tripBlocks++;
        }

        if (coal > 0) {
            company.addRawCoal(coal);
            v.tripCoal += coal;
        }
        if (money > 0) plugin.economy().deposit(company.getId(), money);
        if (xp > 0) company.addXp(xp);
        if (coal > 0 || money > 0 || xp > 0) {
            plugin.mining().companies().checkLevelUp(company);
            companiesDirty = true;
        }
        return true;
    }

    /** Null si se puede romper; si no, el motivo para mostrarle al jugador. */
    private String cannotBreak(Block block, UUID companyId) {
        Material type = block.getType();
        if (type.getHardness() < 0 || drill.blacklist().contains(type)) return "Bloque imposible de perforar adelante";
        if (block.getState(false) instanceof TileState) return "Hay un bloque con contenido adelante (cofre, horno, etc)";
        if (!ownedBy(companyId, block.getChunk())) return "Limite del territorio de tu empresa";
        if (drill.stopAtLiquids() && touchesLiquid(block)) return "Hay agua o lava detras del frente";
        return null;
    }

    private static boolean touchesLiquid(Block block) {
        if (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) return true;
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = block.getRelative(face);
            if (n.isLiquid()) return true;
            if (n.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) return true;
        }
        return false;
    }

    /** Caja de ancho x alto x profundidad delante del taladro. El piso nunca se incluye. */
    private Set<Block> frontBlocks(VehicleState v) {
        Vector dir = direction(v.yaw);
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        Set<Block> blocks = new LinkedHashSet<>();
        int floorY = (int) Math.floor(v.y() + 0.01);
        var spec = v.type.perforacion();
        double halfWidth = spec.ancho() / 2.0 - 0.5;
        List<Double> lateral = new ArrayList<>();
        for (double l = -halfWidth; l <= halfWidth + 1.0E-6; l += 0.5) lateral.add(l);
        lateral.add(-v.shape.halfWidth());
        lateral.add(v.shape.halfWidth());

        double front = v.shape.halfLength();
        for (double d = front + 0.1; d <= spec.profundidad() + front + 1.0E-6; d += 0.4) {
            for (double l : lateral) {
                double bx = v.x + dir.getX() * d + perp.getX() * l;
                double bz = v.z + dir.getZ() * d + perp.getZ() * l;
                for (int up = 0; up < spec.alto(); up++) {
                    blocks.add(v.world.getBlockAt((int) Math.floor(bx), floorY + up, (int) Math.floor(bz)));
                }
            }
        }
        return blocks;
    }

    // ---------------------------------------------------------------
    // Efectos y barra
    // ---------------------------------------------------------------

    private void engineSound(VehicleState v, boolean drilling) {
        if (!effects.sounds() || effects.engine() == null) return;
        boolean moving = Math.abs(v.speed) > 0.01;
        if (!moving || drilling || v.ticks % effects.engineInterval() != 0) return;
        float pitch = (float) (0.6 + 0.8 * Math.min(1, Math.abs(v.speed) / v.type.velocidad()));
        v.world.playSound(Sound.sound(effects.engine(), Sound.Source.NEUTRAL, effects.volume() * 0.5f, pitch),
                v.x, v.y(), v.z);
    }

    private void warn(VehicleState v, Player driver, String message) {
        if (v.ticks - v.lastWarningTick < 30) return;
        v.lastWarningTick = v.ticks;
        driver.sendActionBar(Component.text(message, NamedTextColor.RED));
        if (effects.sounds() && effects.blocked() != null) {
            driver.playSound(Sound.sound(effects.blocked(), Sound.Source.NEUTRAL, effects.volume() * 0.4f, 1.4f));
        }
    }

    private void updateBossBar(VehicleState v) {
        float progress = (float) Math.max(0, Math.min(1, v.fuel / v.type.tanque()));
        v.bossBar.progress(progress);
        v.bossBar.color(progress > 0.5f ? BossBar.Color.GREEN : progress > 0.2f ? BossBar.Color.YELLOW : BossBar.Color.RED);
        String text;
        if (v.type.isDrill()) {
            text = v.type.nombre() + "  |  Combustible " + (int) Math.ceil(v.fuel) + "/" + (int) v.type.tanque()
                    + "  |  Carbon del viaje: " + round(v.tripCoal);
        } else {
            // bloques por tick -> km/h: x20 ticks/s x3.6
            int kmh = (int) Math.round(Math.abs(v.speed) * 20 * 3.6);
            text = v.type.nombre() + "  |  " + kmh + " km/h  |  Combustible " + (int) Math.ceil(v.fuel) + "/"
                    + (int) v.type.tanque() + " L";
        }
        v.bossBar.name(Component.text(text, NamedTextColor.WHITE));
    }

    private void saveIfDirty() {
        plugin.garage().saveIfDirty();
        if (!companiesDirty) return;
        companiesDirty = false;
        plugin.mining().companies().save();
    }

    // ---------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------

    /** Posiciones de las cajas de click, repartidas a lo largo del cuerpo, mas la del panel al final. */
    private static List<Location> clickPositions(Shape shape, Location center) {
        int n = shape.interactionCount();
        List<Location> out = new ArrayList<>(n + 1);
        double from = shape.clickRear() + shape.interactionWidth() / 2.0;
        double to = shape.halfLength() - shape.interactionWidth() / 2.0;
        for (int i = 0; i < n; i++) {
            double lz = n == 1 ? (shape.clickRear() + shape.halfLength()) / 2 : from + (to - from) * i / (n - 1);
            Location l = VehicleModel.local(center, 0f, 0f, (float) lz);
            l.setPitch(0);
            out.add(l);
        }
        if (shape.console() != null) {
            Vector3f c = shape.console();
            Location l = VehicleModel.local(center, c.x, c.y, c.z);
            l.setPitch(0);
            out.add(l);
        }
        return out;
    }

    private boolean ownedBy(UUID ownerId, Chunk chunk) {
        Optional<UUID> owner = plugin.territory().getOwner(chunk);
        return owner.isPresent() && owner.get().equals(ownerId);
    }

    static Vector direction(float yaw) {
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

    /** Efectos de sonido y particulas del config. */
    private record Effects(boolean particles, boolean sounds, float volume, Key engine, int engineInterval, Key blocked) {
        static Effects load(FileConfiguration c, VehiclesPlugin plugin) {
            return new Effects(
                    c.getBoolean("efectos.particulas", true),
                    c.getBoolean("efectos.sonidos", true),
                    (float) c.getDouble("efectos.volumen", 0.8),
                    parseKey(plugin, c.getString("efectos.sonido-motor", "entity.minecart.riding")),
                    Math.max(1, c.getInt("efectos.intervalo-motor", 20)),
                    parseKey(plugin, c.getString("efectos.sonido-bloqueado", "block.anvil.land")));
        }

        private static Key parseKey(VehiclesPlugin plugin, String value) {
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
