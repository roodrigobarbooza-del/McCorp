package com.isjbar.minercorp.mining.vehicle;

import org.bukkit.configuration.file.FileConfiguration;

/** Datos de un tier de taladro-vehiculo, leidos de config.yml (taladros.tier-N). */
public record DrillTier(int tier, String nombre, double costo, double bonusRendimiento, int radio, double velocidad) {

    public static DrillTier load(FileConfiguration config, int tier) {
        String path = "taladros.tier-" + tier;
        return new DrillTier(
                tier,
                config.getString(path + ".nombre", "Taladro"),
                config.getDouble(path + ".costo", 0.0),
                config.getDouble(path + ".bonus-rendimiento", 0.0),
                Math.max(1, config.getInt(path + ".radio", 1)),
                config.getDouble(path + ".velocidad", 0.4)
        );
    }

    public static boolean exists(FileConfiguration config, int tier) {
        return config.isConfigurationSection("taladros.tier-" + tier);
    }
}
