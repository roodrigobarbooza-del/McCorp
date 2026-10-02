package com.isjbar.minercorp.vehicles.type;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Datos de perforacion de un taladro (tipos.&lt;id&gt;.perforacion).
 *
 * @param ancho             ancho del tunel en bloques (minimo 3: el taladro mide 2.6 de ancho)
 * @param alto              alto del tunel, contando desde el piso donde esta apoyado (minimo 3)
 * @param profundidad       cuantos bloques hacia adelante perfora de una vez
 * @param potencia          divide el tiempo de perforacion de cada bloque
 * @param bonusRendimiento  multiplica recompensas por (1 + bonus)
 */
public record DrillSpec(int ancho, int alto, int profundidad, double potencia, double bonusRendimiento) {

    /** Tunel minimo: el taladro mide 2.6 de ancho y casi 3 de alto. */
    public static final int MIN_TUNNEL = 3;

    static DrillSpec load(ConfigurationSection s) {
        if (s == null) return new DrillSpec(MIN_TUNNEL, MIN_TUNNEL, 1, 1.0, 0.0);
        // El taladro tiene que entrar en su propio tunel.
        return new DrillSpec(
                Math.max(MIN_TUNNEL, s.getInt("ancho", MIN_TUNNEL)),
                Math.max(MIN_TUNNEL, s.getInt("alto", MIN_TUNNEL)),
                Math.max(1, s.getInt("profundidad", 1)),
                Math.max(0.01, s.getDouble("potencia", 1.0)),
                s.getDouble("bonus-rendimiento", 0.0));
    }
}
