package com.isjbar.minercorp.vehicles.type;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Datos de perforacion de un taladro (tipos.&lt;id&gt;.perforacion).
 *
 * @param ancho             ancho del tunel en bloques
 * @param alto              alto del tunel, contando desde el piso donde esta apoyado
 * @param profundidad       cuantos bloques hacia adelante perfora de una vez
 * @param potencia          divide el tiempo de perforacion de cada bloque
 * @param bonusRendimiento  multiplica recompensas por (1 + bonus)
 */
public record DrillSpec(int ancho, int alto, int profundidad, double potencia, double bonusRendimiento) {

    static DrillSpec load(ConfigurationSection s) {
        if (s == null) return new DrillSpec(2, 2, 1, 1.0, 0.0);
        return new DrillSpec(
                Math.max(1, s.getInt("ancho", 2)),
                Math.max(2, s.getInt("alto", 2)),
                Math.max(1, s.getInt("profundidad", 1)),
                Math.max(0.01, s.getDouble("potencia", 1.0)),
                s.getDouble("bonus-rendimiento", 0.0));
    }
}
