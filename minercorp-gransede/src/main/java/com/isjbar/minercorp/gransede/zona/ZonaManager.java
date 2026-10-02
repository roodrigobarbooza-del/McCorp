package com.isjbar.minercorp.gransede.zona;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Zonas de la Gran Sede y el punto de llegada, guardados en zonas.yml. */
public class ZonaManager {

    private final File file;
    private final Logger logger;
    private final Map<String, Zona> zonas = new LinkedHashMap<>();
    private Location spawn;

    public ZonaManager(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "zonas.yml");
        this.logger = logger;
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("zonas");
        if (sec != null) {
            for (String nombre : sec.getKeys(false)) {
                ConfigurationSection z = sec.getConfigurationSection(nombre);
                if (z == null) continue;
                try {
                    zonas.put(nombre.toLowerCase(), new Zona(nombre.toLowerCase(), ZonaTipo.valueOf(z.getString("tipo", "SEDE")),
                            z.getString("mundo"), z.getInt("min-x"), z.getInt("min-y"), z.getInt("min-z"),
                            z.getInt("max-x"), z.getInt("max-y"), z.getInt("max-z")));
                } catch (IllegalArgumentException e) {
                    logger.warning("Zona '" + nombre + "' con tipo invalido en zonas.yml, se ignora.");
                }
            }
        }
        if (yml.isConfigurationSection("spawn")) {
            World w = Bukkit.getWorld(yml.getString("spawn.mundo", ""));
            if (w != null) {
                spawn = new Location(w, yml.getDouble("spawn.x"), yml.getDouble("spawn.y"), yml.getDouble("spawn.z"),
                        (float) yml.getDouble("spawn.yaw"), (float) yml.getDouble("spawn.pitch"));
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Zona z : zonas.values()) {
            String p = "zonas." + z.nombre() + ".";
            yml.set(p + "tipo", z.tipo().name());
            yml.set(p + "mundo", z.mundo());
            yml.set(p + "min-x", z.minX());
            yml.set(p + "min-y", z.minY());
            yml.set(p + "min-z", z.minZ());
            yml.set(p + "max-x", z.maxX());
            yml.set(p + "max-y", z.maxY());
            yml.set(p + "max-z", z.maxZ());
        }
        if (spawn != null && spawn.getWorld() != null) {
            yml.set("spawn.mundo", spawn.getWorld().getName());
            yml.set("spawn.x", spawn.getX());
            yml.set("spawn.y", spawn.getY());
            yml.set("spawn.z", spawn.getZ());
            yml.set("spawn.yaw", spawn.getYaw());
            yml.set("spawn.pitch", spawn.getPitch());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            logger.severe("No se pudo guardar zonas.yml: " + e.getMessage());
        }
    }

    public void put(Zona zona) {
        zonas.put(zona.nombre(), zona);
        save();
    }

    public boolean remove(String nombre) {
        boolean ok = zonas.remove(nombre.toLowerCase()) != null;
        if (ok) save();
        return ok;
    }

    public List<Zona> all() {
        return new ArrayList<>(zonas.values());
    }

    /**
     * La zona que manda en esa ubicacion. Si se pisan, gana la mas chica, asi
     * una mina o un bosque dentro de la zona SEDE tienen sus propias reglas.
     */
    public Optional<Zona> at(Location loc) {
        Zona mejor = null;
        for (Zona z : zonas.values()) {
            if (z.contiene(loc) && (mejor == null || z.volumen() < mejor.volumen())) mejor = z;
        }
        return Optional.ofNullable(mejor);
    }

    public Optional<Location> spawn() {
        return Optional.ofNullable(spawn).map(Location::clone);
    }

    public void setSpawn(Location loc) {
        this.spawn = loc.clone();
        save();
    }
}
