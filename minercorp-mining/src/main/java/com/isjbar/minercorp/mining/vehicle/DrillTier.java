package com.isjbar.minercorp.mining.vehicle;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Datos de un tier de taladro-vehiculo, leidos de config.yml (taladros.tier-N).
 *
 * @param ancho       ancho del tunel en bloques
 * @param alto        alto del tunel en bloques, contando desde el piso donde esta apoyado
 * @param profundidad cuantos bloques hacia adelante perfora de una vez
 * @param velocidad   velocidad maxima en bloques por tick
 * @param giro        grados por tick al girar con A/D
 * @param potencia    divide el tiempo de perforacion de cada bloque
 * @param combustible capacidad del tanque
 */
public record DrillTier(int tier, String nombre, double costo, double bonusRendimiento,
                        int ancho, int alto, int profundidad,
                        double velocidad, double giro, double potencia, double combustible) {

    public static DrillTier load(FileConfiguration config, int tier) {
        String path = "taladros.tier-" + tier;
        return new DrillTier(
                tier,
                config.getString(path + ".nombre", "Taladro"),
                config.getDouble(path + ".costo", 0.0),
                config.getDouble(path + ".bonus-rendimiento", 0.0),
                Math.max(1, config.getInt(path + ".ancho", 2)),
                Math.max(2, config.getInt(path + ".alto", 2)),
                Math.max(1, config.getInt(path + ".profundidad", 1)),
                Math.max(0.01, config.getDouble(path + ".velocidad", 0.15)),
                config.getDouble(path + ".giro", 4.0),
                Math.max(0.01, config.getDouble(path + ".potencia", 1.0)),
                Math.max(1, config.getDouble(path + ".combustible", 100))
        );
    }

    public static boolean exists(FileConfiguration config, int tier) {
        return config.isConfigurationSection("taladros.tier-" + tier);
    }
}
