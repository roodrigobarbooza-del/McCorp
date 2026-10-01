package com.isjbar.minercorp.mining.vehicle;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * Taladro-vehiculo: un bote vanilla (sin dependencias externas) que el
 * jugador conduce dentro del territorio reclamado de su empresa. Mientras
 * tiene un pasajero, va "perforando" el mineral de carbon que encuentra
 * delante segun el radio de su tier.
 *
 * Para que no parezca "un bote con otro nombre", se le acopla una carroceria
 * hecha con dos BlockDisplay (casco + punta) armada combinando bloques
 * vanilla existentes - sin resourcepack ni plugins de terceros. El bote
 * sigue siendo el que maneja la fisica/el asiento; la carroceria solo seguir
 * su posicion y rotacion cada ciclo.
 */
public class DrillVehicleManager {

    private final MiningPlugin plugin;
    private final TerritoryAPI territory;
    private final EconomyAPI economy;
    private final NamespacedKey companyIdKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey hullIdKey;
    private final NamespacedKey bitIdKey;
    private BukkitTask task;

    public DrillVehicleManager(MiningPlugin plugin, TerritoryAPI territory, EconomyAPI economy) {
        this.plugin = plugin;
        this.territory = territory;
        this.economy = economy;
        this.companyIdKey = new NamespacedKey(plugin, "drill_company");
        this.tierKey = new NamespacedKey(plugin, "drill_tier");
        this.hullIdKey = new NamespacedKey(plugin, "drill_hull_id");
        this.bitIdKey = new NamespacedKey(plugin, "drill_bit_id");
    }

    public void start() {
        long interval = plugin.getConfig().getLong("taladros.intervalo-ticks", 10);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public NamespacedKey companyIdKey() {
        return companyIdKey;
    }

    public PlacementResult spawn(Company company, int tier, Location location) {
        if (!DrillTier.exists(plugin.getConfig(), tier)) {
            return PlacementResult.TIER_INVALIDO;
        }

        Chunk chunk = location.getChunk();
        Optional<UUID> owner = territory.getOwner(chunk);
        if (owner.isEmpty() || !owner.get().equals(company.getId())) {
            return PlacementResult.FUERA_DE_TERRITORIO;
        }

        DrillTier tierData = DrillTier.load(plugin.getConfig(), tier);
        if (!economy.withdraw(company.getId(), tierData.costo())) {
            return PlacementResult.SIN_SALDO;
        }

        Boat boat = (Boat) location.getWorld().spawnEntity(location, EntityType.OAK_BOAT);
        boat.customName(Component.text(tierData.nombre() + " - " + company.getName(), NamedTextColor.AQUA));
        boat.setCustomNameVisible(true);
        boat.setInvulnerable(true);
        boat.setWorkOnLand(true);
        boat.setMaxSpeed(tierData.velocidad());
        boat.getPersistentDataContainer().set(companyIdKey, PersistentDataType.STRING, company.getId().toString());
        boat.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

        BlockDisplay hull = spawnHull(location);
        BlockDisplay bit = spawnBit(location);
        boat.getPersistentDataContainer().set(hullIdKey, PersistentDataType.STRING, hull.getUniqueId().toString());
        boat.getPersistentDataContainer().set(bitIdKey, PersistentDataType.STRING, bit.getUniqueId().toString());

        return PlacementResult.OK;
    }

    private BlockDisplay spawnHull(Location location) {
        BlockDisplay display = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        display.setBlock(Material.IRON_BLOCK.createBlockData());
        // Caja ancha y baja que cubre el casco del bote, centrada en la entidad.
        float sx = 1.3f, sy = 0.55f, sz = 0.95f;
        display.setTransformation(new Transformation(
                new Vector3f(-sx / 2f, 0.05f, -sz / 2f),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(sx, sy, sz),
                new AxisAngle4f(0, 0, 0, 1)
        ));
        display.setInterpolationDuration(4);
        display.setInterpolationDelay(0);
        return display;
    }

    private BlockDisplay spawnBit(Location location) {
        BlockDisplay display = (BlockDisplay) location.getWorld().spawnEntity(location, EntityType.BLOCK_DISPLAY);
        display.setBlock(Material.CHISELED_DEEPSLATE.createBlockData());
        // Punta mas chica, desplazada hacia adelante (-Z local) del casco.
        float sx = 0.6f, sy = 0.45f, sz = 0.6f;
        display.setTransformation(new Transformation(
                new Vector3f(-sx / 2f, 0.1f, -(sz / 2f) - 0.75f),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(sx, sy, sz),
                new AxisAngle4f(0, 0, 0, 1)
        ));
        display.setInterpolationDuration(4);
        display.setInterpolationDelay(0);
        return display;
    }

    private void tick() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Boat boat : world.getEntitiesByClass(Boat.class)) {
                syncCarroceria(boat);

                if (boat.getPassengers().isEmpty()) continue;

                String companyIdStr = boat.getPersistentDataContainer().get(companyIdKey, PersistentDataType.STRING);
                Integer tier = boat.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
                if (companyIdStr == null || tier == null) continue;

                plugin.companies().getById(UUID.fromString(companyIdStr))
                        .ifPresent(company -> mine(company, DrillTier.load(plugin.getConfig(), tier), boat));
            }
        }
    }

    private void syncCarroceria(Boat boat) {
        Location loc = boat.getLocation();
        findLinkedDisplay(boat, hullIdKey).ifPresent(d -> teleportKeepingTransform(d, loc));
        findLinkedDisplay(boat, bitIdKey).ifPresent(d -> teleportKeepingTransform(d, loc));
    }

    private void teleportKeepingTransform(BlockDisplay display, Location boatLocation) {
        Location target = boatLocation.clone();
        target.setPitch(0);
        display.teleport(target);
    }

    private Optional<BlockDisplay> findLinkedDisplay(Boat boat, NamespacedKey key) {
        String idStr = boat.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (idStr == null) return Optional.empty();
        Entity entity = plugin.getServer().getEntity(UUID.fromString(idStr));
        return entity instanceof BlockDisplay display ? Optional.of(display) : Optional.empty();
    }

    /** Elimina el bote y la carroceria que tiene asociada. Se usa al desarmar el taladro o cuando el vehiculo se destruye. */
    public void removeVehicle(Boat boat) {
        findLinkedDisplay(boat, hullIdKey).ifPresent(Entity::remove);
        findLinkedDisplay(boat, bitIdKey).ifPresent(Entity::remove);
        boat.remove();
    }

    /** Busca el taladro mas cercano que le pertenezca a la empresa, dentro del radio dado. */
    public Optional<Boat> nearestOwned(Company company, Location location, double maxDistance) {
        Boat nearest = null;
        double nearestDistSq = maxDistance * maxDistance;
        for (Boat boat : location.getWorld().getEntitiesByClass(Boat.class)) {
            String companyIdStr = boat.getPersistentDataContainer().get(companyIdKey, PersistentDataType.STRING);
            if (companyIdStr == null || !UUID.fromString(companyIdStr).equals(company.getId())) continue;
            double distSq = boat.getLocation().distanceSquared(location);
            if (distSq <= nearestDistSq) {
                nearest = boat;
                nearestDistSq = distSq;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private void mine(Company company, DrillTier tierData, Boat boat) {
        Location loc = boat.getLocation();
        Vector dir = loc.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) return;
        dir.normalize();
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());

        Location front = loc.clone().add(dir);
        int half = tierData.radio() - 1;
        double produccionBase = plugin.getConfig().getDouble("taladros.produccion-base", 8.0);
        double cantidad = produccionBase * (1 + tierData.bonusRendimiento());

        double totalExtraido = 0;
        for (int side = -half; side <= half; side++) {
            for (int up = -half; up <= half; up++) {
                Block block = front.clone().add(perp.clone().multiply(side)).add(0, up, 0).getBlock();
                Material type = block.getType();
                if (type.isAir()) continue;

                Chunk chunk = block.getChunk();
                Optional<UUID> owner = territory.getOwner(chunk);
                if (owner.isEmpty() || !owner.get().equals(company.getId())) continue;

                if (type == Material.COAL_ORE || type == Material.DEEPSLATE_COAL_ORE) {
                    double extraido = territory.extractFromVein(chunk, cantidad);
                    if (extraido <= 0) continue;
                    company.addRawCoal(extraido);
                    company.addXp(extraido * 0.5);
                    block.setType(Material.AIR);
                    totalExtraido += extraido;
                } else if (type.isSolid() && type.getHardness() >= 0) {
                    // Abre tunel real: cualquier otro bloque solido y rompible del
                    // camino desaparece sin dar recompensa, solo el carbon cuenta.
                    block.setType(Material.AIR);
                }
            }
        }

        if (totalExtraido > 0) {
            plugin.companies().checkLevelUp(company);
            plugin.companies().save();
        }
    }

    public enum PlacementResult {
        OK, TIER_INVALIDO, FUERA_DE_TERRITORIO, SIN_SALDO
    }
}
