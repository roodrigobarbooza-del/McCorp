package com.isjbar.minercorp.resources.oil;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Yacimientos de petroleo por chunk.
 *
 * Si un chunk tiene petroleo y cuanto se decide con la semilla del mundo y
 * las coordenadas del chunk, asi que es siempre lo mismo para el mismo chunk
 * y no hace falta guardar nada hasta que alguien empieza a bombear. Lo unico
 * que se guarda (petroleo.yml) es cuanto se extrajo de cada yacimiento.
 *
 * Solo hay petroleo en mundos normales (no en el Nether ni el End).
 */
public class OilFieldManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Double> extracted = new HashMap<>();
    private boolean dirty;

    private double chance;
    private int min;
    private int max;
    private long salt;

    public OilFieldManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "petroleo.yml");
        loadConfig(plugin.getConfig());
        load();
    }

    public void loadConfig(FileConfiguration config) {
        chance = config.getDouble("petroleo.probabilidad-chunk", 0.12);
        min = config.getInt("petroleo.barriles-min", 200);
        max = Math.max(min, config.getInt("petroleo.barriles-max", 1200));
        salt = config.getLong("petroleo.sal", 7341);
    }

    /** Barriles que tenia el yacimiento al descubrirse (0 si el chunk no tiene). */
    public int capacity(Chunk chunk) {
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return 0;
        long seed = world.getSeed() ^ (salt * 0x9E3779B97F4A7C15L)
                ^ ((long) chunk.getX() * 341873128712L) ^ ((long) chunk.getZ() * 132897987541L);
        SplittableRandom random = new SplittableRandom(seed);
        if (random.nextDouble() >= chance) return 0;
        return min + random.nextInt(max - min + 1);
    }

    /** Barriles que le quedan al yacimiento del chunk. */
    public double remaining(Chunk chunk) {
        int capacity = capacity(chunk);
        if (capacity <= 0) return 0;
        return Math.max(0, capacity - extracted.getOrDefault(key(chunk), 0.0));
    }

    /** Extrae hasta {@code amount} barriles. Devuelve cuanto saco de verdad. */
    public double extract(Chunk chunk, double amount) {
        double available = remaining(chunk);
        double taken = Math.min(available, amount);
        if (taken <= 0) return 0;
        extracted.merge(key(chunk), taken, Double::sum);
        dirty = true;
        return taken;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        extracted.forEach((k, v) -> yaml.set("extraido." + k, v));
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar petroleo.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(file).getConfigurationSection("extraido");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) extracted.put(k, sec.getDouble(k));
    }

    private static String key(Chunk chunk) {
        // Los puntos separan secciones en YAML, por eso se usa "_".
        return chunk.getWorld().getName().replace('.', '-') + "_" + chunk.getX() + "_" + chunk.getZ();
    }
}
