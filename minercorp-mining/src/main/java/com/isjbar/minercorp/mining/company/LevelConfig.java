package com.isjbar.minercorp.mining.company;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Lee la configuracion de niveles de empresa desde config.yml. */
public class LevelConfig {

    private final FileConfiguration config;

    public LevelConfig(FileConfiguration config) {
        this.config = config;
    }

    public int maxNivel() {
        return config.getInt("niveles.max-nivel", 10);
    }

    public double xpRequerida(int nivelActual) {
        double base = config.getDouble("niveles.xp-base-por-nivel", 1000);
        double mult = config.getDouble("niveles.multiplicador", 1.4);
        return base * Math.pow(mult, nivelActual - 1);
    }

    public int chunksPermitidos(int nivel) {
        return valorDeLista("niveles.chunks-por-nivel", nivel, 1);
    }

    public int minionsPermitidos(int nivel) {
        return valorDeLista("niveles.minions-por-nivel", nivel, 0);
    }

    private int valorDeLista(String path, int nivel, int fallback) {
        List<Integer> lista = config.getIntegerList(path);
        int idx = nivel - 1;
        if (lista.isEmpty() || idx < 0) return fallback;
        if (idx >= lista.size()) return lista.get(lista.size() - 1);
        return lista.get(idx);
    }
}
