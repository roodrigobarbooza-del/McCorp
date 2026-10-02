package com.isjbar.minercorp.vehicles.vehicle;

import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.mining.company.StoredDrill;
import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.garage.StoredVehicle;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pasa al plugin nuevo los taladros que hizo MinerCorp-Mining antes de que
 * los vehiculos tuvieran su propio plugin:
 *
 * <ul>
 *   <li>taladros en el mundo (vehiculo propio del PR #2): raiz con
 *       {@code minercorp-mining:taladro_*}, carroceria DrillModel y asiento;</li>
 *   <li>botes-taladro todavia mas viejos ({@code minercorp-mining:drill_*});</li>
 *   <li>taladros guardados en el garaje de la empresa (companies.yml de Mining).</li>
 * </ul>
 *
 * Cada uno se rearma como el taladro del tier equivalente (taladro-N) con la
 * misma empresa y el mismo combustible.
 */
public final class LegacyMigration {

    private static final String NS = "minercorp-mining";

    private final VehiclesPlugin plugin;
    private final VehicleManager vehicles;

    private final NamespacedKey companyKey = new NamespacedKey(NS, "taladro_empresa");
    private final NamespacedKey tierKey = new NamespacedKey(NS, "taladro_tier");
    private final NamespacedKey fuelKey = new NamespacedKey(NS, "taladro_combustible");
    private final NamespacedKey modelKey = new NamespacedKey(NS, "taladro_modelo");
    private final NamespacedKey seatKey = new NamespacedKey(NS, "taladro_asiento");
    private final NamespacedKey rootKey = new NamespacedKey(NS, "taladro_raiz");
    private final NamespacedKey modelPartsKey = new NamespacedKey(NS, "drill_model_parts");
    private final NamespacedKey modelPartKey = new NamespacedKey(NS, "drill_model_part");

    private final NamespacedKey boatCompanyKey = new NamespacedKey(NS, "drill_company");
    private final NamespacedKey boatTierKey = new NamespacedKey(NS, "drill_tier");
    private final NamespacedKey boatHullKey = new NamespacedKey(NS, "drill_hull_id");
    private final NamespacedKey boatBitKey = new NamespacedKey(NS, "drill_bit_id");

    public LegacyMigration(VehiclesPlugin plugin, VehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    /** Pasa los taladros guardados en las empresas de Mining al garaje de cada empresa. */
    public void migrateStoredDrills() {
        int moved = 0;
        for (Company company : plugin.mining().companies().all()) {
            List<StoredDrill> old = company.getStoredDrills();
            if (old.isEmpty()) continue;
            for (StoredDrill d : old) {
                Optional<VehicleType> type = plugin.types().drillForTier(d.tier());
                if (type.isEmpty()) continue;
                plugin.garage().add(company.getId(), new StoredVehicle(type.get().id(), d.combustible(), null));
                moved++;
            }
            old.clear();
        }
        if (moved > 0) {
            plugin.garage().save();
            plugin.mining().companies().save();
            plugin.getLogger().info(moved + " taladros guardados en empresas pasaron al garaje de MinerCorp-Vehicles.");
        }
    }

    /** Convierte los taladros viejos que haya entre estas entidades. */
    public void convert(List<Entity> entities) {
        for (Entity entity : entities) {
            if (!entity.isValid()) continue;
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (pdc.has(companyKey, PersistentDataType.STRING)) {
                convertDrill(entity);
            } else if (entity instanceof Boat && pdc.has(boatCompanyKey, PersistentDataType.STRING)) {
                convertBoat(entity);
            }
        }
    }

    private void convertDrill(Entity root) {
        PersistentDataContainer pdc = root.getPersistentDataContainer();
        UUID companyId = UUID.fromString(pdc.get(companyKey, PersistentDataType.STRING));
        Integer tier = pdc.get(tierKey, PersistentDataType.INTEGER);
        Double fuel = pdc.get(fuelKey, PersistentDataType.DOUBLE);

        Location at = root.getLocation();
        Entity chassis = entity(pdc.get(modelKey, PersistentDataType.STRING));
        if (chassis != null) {
            at = chassis.getLocation();
            for (Entity part : modelParts(chassis)) part.remove();
            chassis.remove();
        } else {
            // La raiz vieja iba 0.6 por encima del piso.
            at.subtract(0, 0.6, 0);
        }
        Entity seat = entity(pdc.get(seatKey, PersistentDataType.STRING));
        if (seat != null) seat.remove();
        root.eject();
        root.remove();
        respawn(companyId, tier == null ? 1 : tier, fuel == null ? 0 : fuel, at);
    }

    private void convertBoat(Entity boat) {
        PersistentDataContainer pdc = boat.getPersistentDataContainer();
        UUID companyId = UUID.fromString(pdc.get(boatCompanyKey, PersistentDataType.STRING));
        Integer tier = pdc.get(boatTierKey, PersistentDataType.INTEGER);
        for (NamespacedKey key : new NamespacedKey[]{boatHullKey, boatBitKey}) {
            Entity part = entity(pdc.get(key, PersistentDataType.STRING));
            if (part != null) part.remove();
        }
        Location at = boat.getLocation();
        boat.eject();
        boat.remove();
        respawn(companyId, tier == null ? 1 : tier, 0, at);
    }

    private void respawn(UUID companyId, int tier, double fuel, Location at) {
        Optional<VehicleType> type = plugin.types().drillForTier(tier);
        if (type.isEmpty()) {
            plugin.getLogger().warning("No hay ningun tipo de taladro en el config: se descarta un taladro viejo.");
            return;
        }
        Location center = at.clone();
        center.setPitch(0);
        vehicles.spawn(type.get(), companyId, center, fuel, null);
        String name = plugin.mining().companies().getById(companyId).map(Company::getName).orElse("?");
        plugin.getLogger().info("Taladro de la empresa " + name + " pasado a MinerCorp-Vehicles en "
                + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ());
    }

    /** Borra piezas sueltas de taladros viejos cerca (carrocerias o asientos sin raiz). */
    public int removeOrphans(Location location, double radius) {
        int removed = 0;
        for (Entity e : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if (pdc.has(rootKey, PersistentDataType.STRING) || pdc.has(modelPartKey, PersistentDataType.STRING)) {
                if (pdc.has(companyKey, PersistentDataType.STRING)) continue;
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    private List<Entity> modelParts(Entity chassis) {
        List<Entity> parts = new ArrayList<>();
        String ids = chassis.getPersistentDataContainer().get(modelPartsKey, PersistentDataType.STRING);
        if (ids == null) return parts;
        for (String id : ids.split(",")) {
            Entity e = entity(id);
            if (e != null) parts.add(e);
        }
        return parts;
    }

    private Entity entity(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return plugin.getServer().getEntity(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
