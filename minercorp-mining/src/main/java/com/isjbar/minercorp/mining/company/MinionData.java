package com.isjbar.minercorp.mining.company;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.UUID;

/** Un minion colocado en el mundo que extrae carbon automaticamente de la veta del chunk donde esta parado. */
public class MinionData {

    private final UUID id;
    private final String world;
    private final double x;
    private final double y;
    private final double z;

    public MinionData(UUID id, Location loc) {
        this.id = id;
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
    }

    public MinionData(UUID id, String world, double x, double y, double z) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID getId() {
        return id;
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }
}
