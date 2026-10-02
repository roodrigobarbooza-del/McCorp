package com.isjbar.minercorp.mining.sede;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.TextDisplay;

import java.util.*;

/**
 * Una obra de sede en curso: donde esta, como esta rotada, cuanto se limpio,
 * que materiales se entregaron y cuantos bloques ya se colocaron.
 *
 * La geometria (pasar de coordenadas locales del {@link SedeBlueprint} a
 * coordenadas del mundo) tambien vive aca, para que la vista previa del
 * plano y la obra real coincidan siempre.
 */
public class Obra {

    public enum Fase { LIMPIEZA, MATERIALES, CONSTRUYENDO }

    private final UUID companyId;
    private final String world;
    /** Bloque del suelo sobre el que se apoya el centro del edificio. */
    private final int ax, ay, az;
    /** Hacia donde mira el frente del edificio (el lado del cofre). */
    private final BlockFace frente;

    private int limpiezaInicial;
    private boolean limpia;
    /** Cuantas etapas ya estan pagadas completas. */
    private int etapasPagadas;
    /** Lo entregado para la etapa que se esta pagando ahora. */
    private final Map<Material, Integer> entregado = new EnumMap<>(Material.class);
    /** Bloques ya colocados del todo (persistido). */
    private int colocados;
    /** Andamios que puso la obra, para sacarlos al terminar. */
    private final List<int[]> andamios = new ArrayList<>();

    // Estado en memoria, no se guarda.
    transient int cursor;
    transient int enVuelo;
    transient int pendientesLimpieza;
    transient boolean conTicket;
    transient TextDisplay holograma;
    /** Hasta que pieza esta pagado (depende del plano, lo mantiene el manager). */
    transient int finPagado;

    public Obra(UUID companyId, String world, int ax, int ay, int az, BlockFace frente) {
        this.companyId = companyId;
        this.world = world;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.frente = frente;
    }

    // ---------------------------------------------------------------
    // Geometria
    // ---------------------------------------------------------------

    /**
     * Convierte coordenadas locales del plano a un bloque del mundo. Local
     * z=0 es el frente; el edificio queda centrado en el ancla.
     */
    public static int[] aMundo(SedeBlueprint bp, BlockFace frente, int ax, int ay, int az, int lx, int ly, int lz) {
        // "atras" = hacia donde crece z local = opuesto al frente.
        int bx = -frente.getModX(), bz = -frente.getModZ();
        // "derecha" local (+x) = atras rotado -90 grados (mismo sentido que x->z en Minecraft).
        int rx = bz, rz = -bx;
        int cx = lx - bp.ancho() / 2;
        int cz = lz - bp.fondo() / 2;
        return new int[]{ax + rx * cx + bx * cz, ay + ly, az + rz * cx + bz * cz};
    }

    /** La rotacion a aplicar a los BlockData para que miren hacia donde corresponde. */
    public static StructureRotation rotacion(BlockFace frente) {
        return switch (frente) {
            case SOUTH -> StructureRotation.CLOCKWISE_180;
            case WEST -> StructureRotation.COUNTERCLOCKWISE_90;
            case EAST -> StructureRotation.CLOCKWISE_90;
            default -> StructureRotation.NONE; // NORTH: el plano ya tiene el frente en z=0 (norte)
        };
    }

    public int[] aMundo(SedeBlueprint bp, int lx, int ly, int lz) {
        return aMundo(bp, frente, ax, ay, az, lx, ly, lz);
    }

    public BlockData dataRotada(BlockData original) {
        BlockData data = original.clone();
        StructureRotation rot = rotacion(frente);
        if (rot != StructureRotation.NONE) data.rotate(rot);
        return data;
    }

    /** Posicion del cofre de obra: centrado, dos bloques delante del frente, a nivel del piso. */
    public static int[] posCofre(SedeBlueprint bp, BlockFace frente, int ax, int ay, int az) {
        return aMundo(bp, frente, ax, ay, az, bp.ancho() / 2, 1, -2);
    }

    public int[] posCofre(SedeBlueprint bp) {
        return posCofre(bp, frente, ax, ay, az);
    }

    /** True si (x,y,z) cae dentro de la parcela de la obra (base del edificio y todo lo de arriba). */
    public boolean enParcela(SedeBlueprint bp, String w, int x, int y, int z) {
        if (!world.equals(w)) return false;
        if (y < ay || y > ay + bp.alto()) return false;
        int[] a = aMundo(bp, 0, 0, 0);
        int[] b = aMundo(bp, bp.ancho() - 1, 0, bp.fondo() - 1);
        return x >= Math.min(a[0], b[0]) && x <= Math.max(a[0], b[0])
                && z >= Math.min(a[2], b[2]) && z <= Math.max(a[2], b[2]);
    }

    public World mundo() {
        return Bukkit.getWorld(world);
    }

    public Block bloque(int[] pos) {
        World w = mundo();
        return w == null ? null : w.getBlockAt(pos[0], pos[1], pos[2]);
    }

    public boolean cargada() {
        World w = mundo();
        return w != null && w.isChunkLoaded(ax >> 4, az >> 4);
    }

    public Location centro(SedeBlueprint bp) {
        return new Location(mundo(), ax + 0.5, ay + 1, az + 0.5);
    }

    // ---------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------

    public Fase fase() {
        if (!limpia) return Fase.LIMPIEZA;
        return colocados < finPagado ? Fase.CONSTRUYENDO : Fase.MATERIALES;
    }

    public UUID companyId() {
        return companyId;
    }

    public String world() {
        return world;
    }

    public int ax() {
        return ax;
    }

    public int ay() {
        return ay;
    }

    public int az() {
        return az;
    }

    public BlockFace frente() {
        return frente;
    }

    public int limpiezaInicial() {
        return limpiezaInicial;
    }

    public void setLimpiezaInicial(int v) {
        this.limpiezaInicial = v;
    }

    public boolean limpia() {
        return limpia;
    }

    public void setLimpia(boolean v) {
        this.limpia = v;
    }

    public int etapasPagadas() {
        return etapasPagadas;
    }

    public void setEtapasPagadas(int v) {
        this.etapasPagadas = v;
    }

    public Map<Material, Integer> entregado() {
        return entregado;
    }

    public int colocados() {
        return colocados;
    }

    public void setColocados(int v) {
        this.colocados = v;
    }

    public List<int[]> andamios() {
        return andamios;
    }
}
