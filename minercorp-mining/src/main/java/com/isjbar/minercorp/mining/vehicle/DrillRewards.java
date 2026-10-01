package com.isjbar.minercorp.mining.vehicle;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/** Tabla de recompensas por bloque perforado (taladros.recompensas en config.yml). */
public final class DrillRewards {

    /**
     * @param veta        si true, el bloque saca carbon de la veta del chunk
     * @param xpPorUnidad XP de empresa por cada unidad de carbon extraida de la veta
     * @param dinero      dinero directo al balance de la empresa
     * @param xp          XP de empresa fija por bloque
     */
    public record Reward(boolean veta, double xpPorUnidad, double dinero, double xp) {
        static Reward load(ConfigurationSection sec) {
            if (sec == null) return new Reward(false, 0, 0, 0);
            return new Reward(
                    sec.getBoolean("veta", false),
                    sec.getDouble("xp-por-unidad", 0.0),
                    sec.getDouble("dinero", 0.0),
                    sec.getDouble("xp", 0.0)
            );
        }
    }

    private final Map<Material, Reward> porMaterial = new EnumMap<>(Material.class);
    private final Reward porDefecto;

    private DrillRewards(Reward porDefecto) {
        this.porDefecto = porDefecto;
    }

    public static DrillRewards load(FileConfiguration config, java.util.logging.Logger logger) {
        ConfigurationSection root = config.getConfigurationSection("taladros.recompensas");
        DrillRewards rewards = new DrillRewards(Reward.load(root == null ? null : root.getConfigurationSection("default")));
        if (root == null) return rewards;
        for (String key : root.getKeys(false)) {
            if (key.equalsIgnoreCase("default")) continue;
            Material material = Material.matchMaterial(key);
            if (material == null) {
                logger.warning("taladros.recompensas: material desconocido '" + key + "', se ignora.");
                continue;
            }
            rewards.porMaterial.put(material, Reward.load(root.getConfigurationSection(key)));
        }
        return rewards;
    }

    public Reward get(Material material) {
        return porMaterial.getOrDefault(material, porDefecto);
    }
}
