package com.isjbar.minercorp.territory.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/** Identifica un chunk de forma estable para persistencia (mundo + x + z). */
public final class ChunkKey {

    private final String world;
    private final int x;
    private final int z;

    public ChunkKey(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static ChunkKey of(Location loc) {
        return new ChunkKey(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static ChunkKey parse(String raw) {
        String[] parts = raw.split(";");
        return new ChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public String serialize() {
        return world + ";" + x + ";" + z;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public boolean isLoadedChunk(World bukkitWorld) {
        return bukkitWorld != null && bukkitWorld.getName().equals(world);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkKey other)) return false;
        return x == other.x && z == other.z && world.equals(other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }

    @Override
    public String toString() {
        return serialize();
    }
}
