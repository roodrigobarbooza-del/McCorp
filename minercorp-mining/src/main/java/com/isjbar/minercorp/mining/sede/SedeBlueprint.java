package com.isjbar.minercorp.mining.sede;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * El edificio de la sede, como lista de bloques en coordenadas locales.
 *
 * Coordenadas locales: x de 0 a ancho-1, z de 0 a fondo-1, y=0 es la
 * fundacion (va al nivel del suelo). El frente del edificio es el lado z=0;
 * el cofre de obra va delante de ese lado.
 *
 * Se puede reemplazar por un {@code .nbt} hecho con un bloque de estructura
 * vanilla (ver {@link #load}). Los bloques se reparten en etapas segun su
 * material, y los materiales que pide cada etapa salen de esos bloques.
 */
public final class SedeBlueprint {

    public static final String[] NOMBRES_ETAPAS = {"Fundacion", "Estructura", "Techo", "Detalles"};

    /** Un bloque del edificio en coordenadas locales. */
    public record Pieza(int x, int y, int z, BlockData data, int etapa) {
    }

    private final int ancho;
    private final int alto;
    private final int fondo;
    /** Ordenadas por etapa, despues por altura: es el orden en que se construyen. */
    private final List<Pieza> piezas;
    /** Cuantas piezas hay antes de terminar cada etapa (indice exclusivo en {@link #piezas}). */
    private final int[] finDeEtapa;
    private final List<Map<Material, Integer>> materiales;

    private SedeBlueprint(int ancho, int alto, int fondo, List<Pieza> piezas) {
        this.ancho = ancho;
        this.alto = alto;
        this.fondo = fondo;
        List<Pieza> ordenadas = new ArrayList<>(piezas);
        ordenadas.sort(Comparator.comparingInt(Pieza::etapa)
                .thenComparingInt(Pieza::y)
                .thenComparingInt(Pieza::z)
                .thenComparingInt(Pieza::x));
        this.piezas = List.copyOf(ordenadas);

        this.finDeEtapa = new int[NOMBRES_ETAPAS.length];
        this.materiales = new ArrayList<>();
        for (int i = 0; i < NOMBRES_ETAPAS.length; i++) materiales.add(new LinkedHashMap<>());
        for (int i = 0; i < this.piezas.size(); i++) {
            Pieza p = this.piezas.get(i);
            finDeEtapa[p.etapa()] = i + 1;
            Material item = materialQuePide(p.data());
            if (item != null) materiales.get(p.etapa()).merge(item, 1, Integer::sum);
        }
        // Etapas vacias: terminan donde termina la anterior.
        for (int i = 1; i < finDeEtapa.length; i++) {
            if (finDeEtapa[i] < finDeEtapa[i - 1]) finDeEtapa[i] = finDeEtapa[i - 1];
        }
    }

    public int ancho() {
        return ancho;
    }

    public int alto() {
        return alto;
    }

    public int fondo() {
        return fondo;
    }

    public List<Pieza> piezas() {
        return piezas;
    }

    public int etapas() {
        return NOMBRES_ETAPAS.length;
    }

    /** Indice (exclusivo) de la ultima pieza de la etapa indicada. */
    public int finDeEtapa(int etapa) {
        if (etapa < 0) return 0;
        return finDeEtapa[Math.min(etapa, finDeEtapa.length - 1)];
    }

    /** Materiales que hay que dejar en el cofre para pagar esa etapa. */
    public Map<Material, Integer> materiales(int etapa) {
        return materiales.get(etapa);
    }

    /**
     * El item que el jugador tiene que entregar por este bloque, o null si
     * no pide nada (por ejemplo la mitad de arriba de una puerta, que ya se
     * pago con la de abajo).
     */
    static Material materialQuePide(BlockData data) {
        if (data.getMaterial().isAir()) return null;
        if (data instanceof Bisected b && b.getHalf() == Bisected.Half.TOP) return null;
        Material item = data.getPlacementMaterial();
        return item.isItem() && !item.isAir() ? item : null;
    }

    static int etapaPorMaterial(BlockData data, int y) {
        Material m = data.getMaterial();
        if (Tag.STAIRS.isTagged(m) || Tag.SLABS.isTagged(m)) return 2;
        // Lo que no es un bloque macizo, o lo que se usa (horno, mesa, puerta...), va al final.
        if (!m.isOccluding() || m.isInteractable() || data instanceof Directional) return 3;
        if (y == 0) return 0;
        return 1;
    }

    // ---------------------------------------------------------------
    // Carga
    // ---------------------------------------------------------------

    /**
     * Usa el {@code .nbt} indicado si existe (guardado con un bloque de
     * estructura vanilla); si no, la sede minera que trae el plugin.
     */
    public static SedeBlueprint load(File archivo, Logger log) {
        if (archivo.isFile()) {
            try {
                Structure structure = Bukkit.getStructureManager().loadStructure(archivo);
                if (structure.getPalettes().isEmpty()) throw new IOException("la estructura no tiene bloques");
                Palette palette = structure.getPalettes().get(0);
                List<Pieza> piezas = new ArrayList<>();
                for (BlockState state : palette.getBlocks()) {
                    BlockData data = state.getBlockData();
                    Material m = data.getMaterial();
                    if (m.isAir() || m == Material.STRUCTURE_VOID) continue;
                    piezas.add(new Pieza(state.getX(), state.getY(), state.getZ(), data,
                            etapaPorMaterial(data, state.getY())));
                }
                BlockVector size = structure.getSize();
                log.info("Sede cargada desde " + archivo.getName() + " (" + piezas.size() + " bloques).");
                return new SedeBlueprint(size.getBlockX(), size.getBlockY(), size.getBlockZ(), piezas);
            } catch (IOException | RuntimeException e) {
                log.warning("No se pudo leer " + archivo.getName() + " (" + e.getMessage() + "); se usa la sede por defecto.");
            }
        }
        return casaMinera();
    }

    /**
     * Sede minera por defecto, 7x7: base de piedra labrada, esquinas de
     * tronco de roble oscuro, paredes de abeto, techo a dos aguas y una
     * linterna colgando del cumbrero.
     */
    static SedeBlueprint casaMinera() {
        final int n = 7;
        List<Pieza> piezas = new ArrayList<>();
        BlockData piedra = Material.COBBLESTONE.createBlockData();
        BlockData piso = Material.SPRUCE_PLANKS.createBlockData();
        BlockData tronco = Material.DARK_OAK_LOG.createBlockData();
        BlockData tabla = Material.SPRUCE_PLANKS.createBlockData();
        BlockData cumbrera = Material.DARK_OAK_PLANKS.createBlockData();

        // Fundacion: borde de piedra y piso de madera.
        for (int x = 0; x < n; x++) {
            for (int z = 0; z < n; z++) {
                boolean borde = x == 0 || z == 0 || x == n - 1 || z == n - 1;
                add(piezas, x, 0, z, borde ? piedra : piso);
            }
        }

        // Paredes: zocalo de piedra (y=1) y tablas (y=2..3), esquinas de tronco.
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    boolean borde = x == 0 || z == 0 || x == n - 1 || z == n - 1;
                    if (!borde) continue;
                    boolean esquina = (x == 0 || x == n - 1) && (z == 0 || z == n - 1);
                    boolean puerta = z == 0 && x == 3 && y <= 2;
                    boolean ventana = y == 2 && ((x == 0 || x == n - 1) && z == 3 || z == n - 1 && x == 3);
                    if (puerta) continue;
                    if (ventana) {
                        add(piezas, x, y, z, panel(x == 0 || x == n - 1));
                    } else if (esquina) {
                        add(piezas, x, y, z, tronco);
                    } else {
                        add(piezas, x, y, z, y == 1 ? piedra : tabla);
                    }
                }
            }
        }

        // Techo a dos aguas: el cumbrero corre de frente a fondo (eje z).
        for (int paso = 0; paso <= 2; paso++) {
            int y = 4 + paso;
            for (int z = 0; z < n; z++) {
                add(piezas, paso, y, z, escalon(BlockFace.EAST));
                add(piezas, n - 1 - paso, y, z, escalon(BlockFace.WEST));
            }
            // Hastiales: tapan el triangulo en el frente y el fondo.
            for (int x = paso + 1; x < n - 1 - paso; x++) {
                add(piezas, x, y, 0, tabla);
                add(piezas, x, y, n - 1, tabla);
            }
        }
        for (int z = 0; z < n; z++) {
            add(piezas, 3, 6, z, cumbrera);
            Slab losa = (Slab) Material.DARK_OAK_SLAB.createBlockData();
            losa.setType(Slab.Type.BOTTOM);
            add(piezas, 3, 7, z, losa);
        }

        // Detalles: puerta, horno, mesa, cofre y linternas.
        add(piezas, 3, 1, 0, puerta(Bisected.Half.BOTTOM));
        add(piezas, 3, 2, 0, puerta(Bisected.Half.TOP));
        Directional horno = (Directional) Material.FURNACE.createBlockData();
        horno.setFacing(BlockFace.NORTH);
        add(piezas, 1, 1, 5, horno);
        add(piezas, 5, 1, 5, Material.CRAFTING_TABLE.createBlockData());
        Directional cofre = (Directional) Material.CHEST.createBlockData();
        cofre.setFacing(BlockFace.NORTH);
        add(piezas, 3, 1, 5, cofre);
        add(piezas, 5, 2, 5, Material.LANTERN.createBlockData());
        Lantern colgante = (Lantern) Material.LANTERN.createBlockData();
        colgante.setHanging(true);
        add(piezas, 3, 5, 3, colgante);

        return new SedeBlueprint(n, 8, n, piezas);
    }

    private static void add(List<Pieza> piezas, int x, int y, int z, BlockData data) {
        piezas.add(new Pieza(x, y, z, data.clone(), etapaPorMaterial(data, y)));
    }

    private static BlockData escalon(BlockFace sube) {
        Stairs s = (Stairs) Material.DARK_OAK_STAIRS.createBlockData();
        s.setFacing(sube);
        s.setHalf(Bisected.Half.BOTTOM);
        return s;
    }

    private static BlockData panel(boolean paredEsteOeste) {
        MultipleFacing p = (MultipleFacing) Material.GLASS_PANE.createBlockData();
        if (paredEsteOeste) {
            p.setFace(BlockFace.NORTH, true);
            p.setFace(BlockFace.SOUTH, true);
        } else {
            p.setFace(BlockFace.EAST, true);
            p.setFace(BlockFace.WEST, true);
        }
        return p;
    }

    private static BlockData puerta(Bisected.Half mitad) {
        Door d = (Door) Material.SPRUCE_DOOR.createBlockData();
        d.setFacing(BlockFace.SOUTH);
        d.setHalf(mitad);
        d.setHinge(Door.Hinge.LEFT);
        return d;
    }
}
