package com.isjbar.minercorp.vehicles.vehicle;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

/** Reglas globales del taladro (seccion "taladro" del config). */
record DrillSettings(double baseProduction, double ticksPerHardness, double fuelPerBlock, boolean stopAtLiquids,
                     Set<Material> blacklist) {

    static DrillSettings load(FileConfiguration c, Logger logger) {
        Set<Material> blacklist = EnumSet.noneOf(Material.class);
        for (String name : c.getStringList("taladro.lista-negra")) {
            Material m = Material.matchMaterial(name);
            if (m != null) blacklist.add(m);
            else logger.warning("taladro.lista-negra: material desconocido '" + name + "'");
        }
        return new DrillSettings(
                c.getDouble("taladro.produccion-base", 8.0),
                Math.max(0, c.getDouble("taladro.ticks-por-dureza", 4.0)),
                Math.max(0, c.getDouble("taladro.consumo-por-bloque", 1.0)),
                c.getBoolean("taladro.frenar-ante-liquidos", true),
                blacklist);
    }
}
