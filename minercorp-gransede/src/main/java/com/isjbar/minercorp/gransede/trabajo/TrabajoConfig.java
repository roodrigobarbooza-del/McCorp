package com.isjbar.minercorp.gransede.trabajo;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Reglas de un trabajo de inicio (la mina o el bosque), leidas del config. */
public record TrabajoConfig(String nombre, int regeneracionTicks, Material agotado,
                            Map<Material, Double> pago, Map<Material, Integer> regenera) {

    /** True si el bloque vuelve igual que era, en vez de sortearse. */
    public boolean regeneraMismo() {
        return regenera.isEmpty();
    }

    public Material sortear(Material original) {
        if (regeneraMismo()) return original;
        int total = regenera.values().stream().mapToInt(Integer::intValue).sum();
        int r = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        for (Map.Entry<Material, Integer> e : regenera.entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return original;
    }

    public static TrabajoConfig leer(String nombre, ConfigurationSection sec, Material agotadoPorDefecto, Logger logger) {
        if (sec == null) return new TrabajoConfig(nombre, 400, agotadoPorDefecto, Map.of(), Map.of());
        int ticks = Math.max(1, sec.getInt("regeneracion-segundos", 20)) * 20;
        Material agotado = material(sec.getString("bloque-agotado"), logger);
        if (agotado == null || !agotado.isBlock()) agotado = agotadoPorDefecto;

        Map<Material, Double> pago = new EnumMap<>(Material.class);
        ConfigurationSection p = sec.getConfigurationSection("pago");
        if (p != null) {
            for (String k : p.getKeys(false)) {
                Material m = material(k, logger);
                if (m != null) pago.put(m, p.getDouble(k));
            }
        }

        Map<Material, Integer> regenera = new LinkedHashMap<>();
        ConfigurationSection r = sec.getConfigurationSection("regenera");
        if (r != null) {
            for (String k : r.getKeys(false)) {
                Material m = material(k, logger);
                if (m != null && m.isBlock() && r.getInt(k) > 0) regenera.put(m, r.getInt(k));
            }
        }
        // El bloque agotado nunca paga, aunque alguien lo ponga en la lista.
        pago.remove(agotado);
        return new TrabajoConfig(nombre, ticks, agotado, pago, regenera);
    }

    private static Material material(String nombre, Logger logger) {
        if (nombre == null) return null;
        Material m = Material.matchMaterial(nombre);
        if (m == null) logger.warning("Material desconocido en el config de la Gran Sede: " + nombre);
        return m;
    }
}
