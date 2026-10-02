package com.isjbar.minercorp.vehicles.garage;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Garajes de jugadores y empresas (garajes.yml). Cada dueno (UUID de
 * jugador o de empresa) tiene una lista de vehiculos guardados, y ademas se
 * recuerda donde quedo cada vehiculo que esta afuera, para poder mostrarlo
 * en el menu.
 */
public final class Garage {

    /** Un vehiculo que esta en el mundo. */
    public record Parked(UUID rootId, String type, String world, int x, int y, int z) {
    }

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, List<StoredVehicle>> stored = new HashMap<>();
    private final Map<UUID, Map<UUID, Parked>> parked = new HashMap<>();
    private boolean dirty;

    public Garage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "garajes.yml");
    }

    public List<StoredVehicle> stored(UUID owner) {
        return Collections.unmodifiableList(stored.getOrDefault(owner, List.of()));
    }

    public void add(UUID owner, StoredVehicle vehicle) {
        stored.computeIfAbsent(owner, k -> new ArrayList<>()).add(vehicle);
        dirty = true;
    }

    /** Saca el vehiculo numero {@code index} del garaje, o null si no existe. */
    public StoredVehicle take(UUID owner, int index) {
        List<StoredVehicle> list = stored.get(owner);
        if (list == null || index < 0 || index >= list.size()) return null;
        dirty = true;
        return list.remove(index);
    }

    public List<Parked> parked(UUID owner) {
        return List.copyOf(parked.getOrDefault(owner, Map.of()).values());
    }

    public void setParked(UUID owner, UUID rootId, String type, Location at) {
        parked.computeIfAbsent(owner, k -> new LinkedHashMap<>())
                .put(rootId, new Parked(rootId, type, at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        dirty = true;
    }

    public void removeParked(UUID owner, UUID rootId) {
        Map<UUID, Parked> map = parked.get(owner);
        if (map != null && map.remove(rootId) != null) dirty = true;
    }

    public void load() {
        stored.clear();
        parked.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection sec = yaml.getConfigurationSection(key);
            if (sec == null) continue;
            List<StoredVehicle> list = new ArrayList<>();
            for (Map<?, ?> map : sec.getMapList("guardados")) {
                Object type = map.get("tipo");
                if (type == null) continue;
                double fuel = map.get("combustible") instanceof Number n ? n.doubleValue() : 0;
                Object cargo = map.get("carga");
                list.add(new StoredVehicle(type.toString(), fuel, cargo == null ? null : cargo.toString()));
            }
            if (!list.isEmpty()) stored.put(owner, list);
            Map<UUID, Parked> out = new LinkedHashMap<>();
            for (Map<?, ?> map : sec.getMapList("afuera")) {
                try {
                    UUID id = UUID.fromString(String.valueOf(map.get("id")));
                    out.put(id, new Parked(id, String.valueOf(map.get("tipo")), String.valueOf(map.get("mundo")),
                            ((Number) map.get("x")).intValue(), ((Number) map.get("y")).intValue(),
                            ((Number) map.get("z")).intValue()));
                } catch (RuntimeException ignored) {
                    // entrada rota: se descarta
                }
            }
            if (!out.isEmpty()) parked.put(owner, out);
        }
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        java.util.Set<UUID> owners = new java.util.HashSet<>(stored.keySet());
        owners.addAll(parked.keySet());
        for (UUID owner : owners) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (StoredVehicle v : stored.getOrDefault(owner, List.of())) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("tipo", v.type());
                map.put("combustible", v.fuel());
                if (v.cargo() != null) map.put("carga", v.cargo());
                list.add(map);
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Parked p : parked.getOrDefault(owner, Map.of()).values()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", p.rootId().toString());
                map.put("tipo", p.type());
                map.put("mundo", p.world());
                map.put("x", p.x());
                map.put("y", p.y());
                map.put("z", p.z());
                out.add(map);
            }
            if (list.isEmpty() && out.isEmpty()) continue;
            if (!list.isEmpty()) yaml.set(owner + ".guardados", list);
            if (!out.isEmpty()) yaml.set(owner + ".afuera", out);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar garajes.yml: " + e.getMessage());
        }
    }
}
