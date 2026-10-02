package com.isjbar.minercorp.resources.machine;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Una maquina configurada en maquinas.&lt;id&gt; del config.yml.
 *
 * @param input   para PROCESO: recurso de entrada y cuantos consume por ciclo (vacio en extractores)
 * @param outputs para PROCESO: lo que sale por ciclo; para extractores, una sola salida
 *                cuya cantidad es lo que intenta extraer por ciclo
 * @param fuels   claves aceptadas como combustible (ids de recurso o materiales vanilla)
 */
public record MachineType(String id, String name, Kind kind, Material block, String model,
                          double price, int cycleTicks, Map<String, Integer> input,
                          List<Output> outputs, Set<String> fuels, List<String> description) {

    public enum Kind {
        /** Saca de la veta de carbon del chunk (MinerCorp-Territory). */
        VETA_CARBON,
        /** Saca del yacimiento de petroleo del chunk. */
        PETROLEO,
        /** Transforma la entrada en las salidas. */
        PROCESO;

        static Kind parse(String s) {
            return switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
                case "veta-carbon", "veta" -> VETA_CARBON;
                case "petroleo" -> PETROLEO;
                default -> PROCESO;
            };
        }
    }

    /** Una salida: recurso, cantidad y probabilidad de salir en cada ciclo (1 = siempre). */
    public record Output(String resource, double amount, double chance) {
    }

    public boolean isExtractor() {
        return kind != Kind.PROCESO;
    }

    public boolean acceptsFuel(String key) {
        return key != null && fuels.contains(key);
    }

    public boolean acceptsInput(String key) {
        return key != null && input.containsKey(key);
    }

    static MachineType parse(String id, ConfigurationSection sec, Logger log) {
        Kind kind = Kind.parse(sec.getString("tipo"));
        Material block = Material.matchMaterial(sec.getString("bloque", "IRON_BLOCK"));
        if (block == null || !block.isBlock() || !block.isSolid()) {
            log.warning("Maquina '" + id + "': bloque invalido, uso IRON_BLOCK.");
            block = Material.IRON_BLOCK;
        }

        Map<String, Integer> input = new LinkedHashMap<>();
        List<Output> outputs = new ArrayList<>();
        if (kind == Kind.PROCESO) {
            ConfigurationSection in = sec.getConfigurationSection("entrada");
            if (in != null) {
                for (String k : in.getKeys(false)) input.put(k.toLowerCase(Locale.ROOT), Math.max(1, in.getInt(k, 1)));
            }
            for (Map<?, ?> map : sec.getMapList("salidas")) {
                Object res = map.get("recurso");
                if (res == null) continue;
                outputs.add(new Output(res.toString().toLowerCase(Locale.ROOT),
                        number(map.get("cantidad"), 1), number(map.get("probabilidad"), 1)));
            }
        } else {
            outputs.add(new Output(sec.getString("salida", kind == Kind.PETROLEO ? "petroleo_crudo" : "carbon_crudo")
                    .toLowerCase(Locale.ROOT), sec.getDouble("extrae", 1), 1));
        }

        Set<String> fuels = new LinkedHashSet<>();
        for (String f : sec.getStringList("combustibles")) {
            // Los materiales vanilla van en mayusculas (COAL) y los recursos en minusculas (coque).
            fuels.add(f.equals(f.toUpperCase(Locale.ROOT)) ? f : f.toLowerCase(Locale.ROOT));
        }

        return new MachineType(id, sec.getString("nombre", id), kind, block, sec.getString("modelo", "refineria"),
                sec.getDouble("precio", 0), Math.max(1, (int) Math.round(sec.getDouble("ciclo-segundos", 10) * 20)),
                input, outputs, fuels, sec.getStringList("descripcion"));
    }

    private static double number(Object o, double fallback) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return fallback;
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
