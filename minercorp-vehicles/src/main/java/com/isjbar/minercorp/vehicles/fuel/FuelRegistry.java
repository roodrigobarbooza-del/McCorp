package com.isjbar.minercorp.vehicles.fuel;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Combustibles conocidos: los del config (combustibles.&lt;id&gt;) y los que
 * registran otros plugins por VehiclesAPI. Los del API sobreviven a un
 * /vehiculo recargar.
 */
public final class FuelRegistry {

    public record Fuel(String id, String nombre, Predicate<ItemStack> matcher, double litrosPorItem) {
    }

    private final Map<String, Fuel> fromConfig = new HashMap<>();
    private final Map<String, Fuel> fromApi = new HashMap<>();
    private double litrosPorCarbonCrudo;

    public void loadConfig(FileConfiguration config, Logger logger) {
        fromConfig.clear();
        litrosPorCarbonCrudo = config.getDouble("combustibles.carbon.por-carbon-crudo", 5);
        ConfigurationSection root = config.getConfigurationSection("combustibles");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            ConfigurationSection items = s == null ? null : s.getConfigurationSection("items");
            if (items == null) continue;
            String nombre = s.getString("nombre", id);
            for (String key : items.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null) {
                    logger.warning("combustibles." + id + ".items: material desconocido '" + key + "'");
                    continue;
                }
                double litros = items.getDouble(key);
                if (litros <= 0) continue;
                // Un Fuel por material: asi cada item tiene su rendimiento.
                String fid = id.toLowerCase(Locale.ROOT);
                fromConfig.put(fid + "#" + m.name(), new Fuel(fid, nombre, item -> item.getType() == m, litros));
            }
        }
    }

    public void register(String id, String nombre, Predicate<ItemStack> matcher, double litrosPorItem) {
        register(id, id, nombre, matcher, litrosPorItem);
    }

    /**
     * Igual, pero con una clave propia: asi varios items distintos pueden ser
     * el mismo combustible (por ejemplo el carbon del config y el carbon crudo
     * de MinerCorp-Recursos), y registrar otra vez la misma clave la reemplaza.
     */
    public void register(String key, String id, String nombre, Predicate<ItemStack> matcher, double litrosPorItem) {
        String fid = id.toLowerCase(Locale.ROOT);
        fromApi.put(key.toLowerCase(Locale.ROOT), new Fuel(fid, nombre, matcher, litrosPorItem));
    }

    /** El combustible que es este item, entre los que acepta el vehiculo. */
    public Optional<Fuel> match(ItemStack item, List<String> accepted) {
        if (item == null || item.getType().isAir()) return Optional.empty();
        for (Fuel f : fromApi.values()) {
            if (accepted.contains(f.id()) && f.matcher().test(item)) return Optional.of(f);
        }
        for (Fuel f : fromConfig.values()) {
            if (accepted.contains(f.id()) && f.matcher().test(item)) return Optional.of(f);
        }
        return Optional.empty();
    }

    /** True si el item es algun combustible conocido, aunque este vehiculo no lo acepte. */
    public boolean isAnyFuel(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        for (Fuel f : fromApi.values()) if (f.matcher().test(item)) return true;
        for (Fuel f : fromConfig.values()) if (f.matcher().test(item)) return true;
        return false;
    }

    /** Nombres legibles de una lista de ids, solo los que existen. */
    public String names(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            String nombre = null;
            for (Fuel f : fromApi.values()) if (f.id().equals(id)) { nombre = f.nombre(); break; }
            if (nombre == null) for (Fuel f : fromConfig.values()) if (f.id().equals(id)) { nombre = f.nombre(); break; }
            if (nombre == null) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(nombre);
        }
        return sb.isEmpty() ? "ninguno disponible" : sb.toString();
    }

    public double litrosPorCarbonCrudo() {
        return litrosPorCarbonCrudo;
    }
}
