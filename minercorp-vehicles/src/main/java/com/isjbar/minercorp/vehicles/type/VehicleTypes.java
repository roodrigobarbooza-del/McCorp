package com.isjbar.minercorp.vehicles.type;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Todos los tipos de vehiculo del config, en orden. */
public final class VehicleTypes {

    private final Map<String, VehicleType> byId = new LinkedHashMap<>();

    public static VehicleTypes load(FileConfiguration config, Logger logger) {
        VehicleTypes types = new VehicleTypes();
        ConfigurationSection root = config.getConfigurationSection("tipos");
        if (root == null) {
            logger.warning("config.yml no tiene la seccion 'tipos': no hay vehiculos.");
            return types;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            types.byId.put(id.toLowerCase(Locale.ROOT), VehicleType.load(id.toLowerCase(Locale.ROOT), s, logger));
        }
        return types;
    }

    public Optional<VehicleType> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<VehicleType> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Tipo de taladro para un tier del taladro viejo de Mining (taladro-N), o el primer taladro que haya. */
    public Optional<VehicleType> drillForTier(int tier) {
        Optional<VehicleType> exact = get("taladro-" + tier);
        if (exact.isPresent()) return exact;
        return byId.values().stream().filter(VehicleType::isDrill).findFirst();
    }
}
