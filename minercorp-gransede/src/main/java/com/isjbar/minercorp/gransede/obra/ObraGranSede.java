package com.isjbar.minercorp.gransede.obra;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

/**
 * La obra de la Gran Sede en curso: donde esta, hacia donde mira y por donde
 * va. La geometria (local a mundo) es la misma que usa la obra de la sede de
 * cada empresa en Mining: el plano queda centrado en el ancla y su frente
 * (z=0 local) mira hacia {@code frente}.
 */
public class ObraGranSede {

    public enum Fase { DESPEJE, CONSTRUCCION }

    private final String world;
    private final int ax, ay, az;
    private final BlockFace frente;
    Fase fase = Fase.DESPEJE;
    /** DESPEJE: siguiente columna (x,z) del area a despejar. CONSTRUCCION: siguiente pieza. */
    int cursor;
    int enVuelo;
    boolean conTicket;

    public ObraGranSede(String world, int ax, int ay, int az, BlockFace frente) {
        this.world = world;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.frente = frente;
    }

    public String world() { return world; }
    public int ax() { return ax; }
    public int ay() { return ay; }
    public int az() { return az; }
    public BlockFace frente() { return frente; }
    public Fase fase() { return fase; }
    public int cursor() { return cursor; }

    public static int[] aMundo(PlanoGranSede plano, BlockFace frente, int ax, int ay, int az, int lx, int ly, int lz) {
        int bx = -frente.getModX(), bz = -frente.getModZ(); // "atras": hacia donde crece z local
        int rx = bz, rz = -bx;                              // "derecha": +x local
        int cx = lx - plano.ancho() / 2;
        int cz = lz - plano.fondo() / 2;
        return new int[]{ax + rx * cx + bx * cz, ay + ly, az + rz * cx + bz * cz};
    }

    public int[] aMundo(PlanoGranSede plano, int lx, int ly, int lz) {
        return aMundo(plano, frente, ax, ay, az, lx, ly, lz);
    }

    public static StructureRotation rotacion(BlockFace frente) {
        return switch (frente) {
            case SOUTH -> StructureRotation.CLOCKWISE_180;
            case WEST -> StructureRotation.COUNTERCLOCKWISE_90;
            case EAST -> StructureRotation.CLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }

    public BlockData dataRotada(BlockData original) {
        BlockData data = original.clone();
        StructureRotation rot = rotacion(frente);
        if (rot != StructureRotation.NONE) data.rotate(rot);
        return data;
    }

    /** Hacia donde mira alguien parado mirando la entrada desde adentro (el frente). */
    public float yawFrente() {
        return switch (frente) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            default -> -90f;
        };
    }

    public World mundo() {
        return Bukkit.getWorld(world);
    }

    public Block bloque(int[] p) {
        World w = mundo();
        return w == null ? null : w.getBlockAt(p[0], p[1], p[2]);
    }

    public Location centro() {
        return new Location(mundo(), ax + 0.5, ay + 1, az + 0.5);
    }
}
