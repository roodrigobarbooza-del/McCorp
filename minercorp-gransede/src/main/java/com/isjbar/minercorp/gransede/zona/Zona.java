package com.isjbar.minercorp.gransede.zona;

import org.bukkit.Location;

/** Cuboide con nombre dentro de un mundo. Las coordenadas son inclusivas. */
public record Zona(String nombre, ZonaTipo tipo, String mundo,
                   int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Zona entre(String nombre, ZonaTipo tipo, Location a, Location b) {
        return new Zona(nombre, tipo, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contiene(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(mundo)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public long volumen() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
