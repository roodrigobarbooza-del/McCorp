package com.isjbar.minercorp.gransede.obra;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.Axis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * La Gran Sede que trae el plugin, 63 x 63 bloques:
 *
 * <pre>
 *   z=62  +--------------------------------------------------------+
 *         |  BOSQUE          EDIFICIO DE LA GRAN SEDE     MINA      |
 *         |  (arboles)       (2 pisos, columnas)          (cerro    |
 *         |                                               de piedra |
 *         |  FERRETERIA      PLAZA con fuente             con tunel)|
 *         |  ======== calle transversal =========================== |
 *         |  CONCESIONARIA   ||  avenida  ||        TALLER DE       |
 *         |  + estacionam.   ||           ||        TALADROS        |
 *   z=0   +-----------------[  porton  ]----------------------------+
 *        x=0                                                    x=62
 * </pre>
 *
 * Todo con bloques vanilla, que siga pareciendo algo que construyo un
 * jugador: asfalto de concreto negro con lineas blancas, veredas de piedra,
 * faroles, una muralla baja y edificios de concreto, cuarzo y madera.
 */
final class PlanoPorDefecto {

    static final int N = 63;
    private static final int TERRENO = 0, EDIFICIOS = 1, NATURAL = 2, DETALLES = 3;

    private final Map<Long, PlanoGranSede.Pieza> piezas = new LinkedHashMap<>();
    private final List<Marcador> marcadores = new ArrayList<>();
    private final Random random = new Random(42); // fijo: la mina sale igual siempre

    static PlanoGranSede crear() {
        PlanoPorDefecto p = new PlanoPorDefecto();
        p.terreno();
        p.muralla();
        p.concesionaria();
        p.tallerTaladros();
        p.ferreteria();
        p.edificioCentral();
        p.plaza();
        p.bosque();
        p.mina();
        p.faroles();
        int alto = 0;
        for (PlanoGranSede.Pieza pz : p.piezas.values()) alto = Math.max(alto, pz.y() + 1);
        return new PlanoGranSede(N, alto, N, new ArrayList<>(p.piezas.values()), p.marcadores);
    }

    // ---------------------------------------------------------------
    // Partes
    // ---------------------------------------------------------------

    private void terreno() {
        BlockData vereda = Material.STONE_BRICKS.createBlockData();
        BlockData borde = Material.SMOOTH_STONE.createBlockData();
        BlockData asfalto = Material.BLACK_CONCRETE.createBlockData();
        BlockData linea = Material.WHITE_CONCRETE.createBlockData();
        BlockData pasto = Material.GRASS_BLOCK.createBlockData();
        BlockData grava = Material.GRAVEL.createBlockData();

        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                BlockData d = (x + z) % 7 == 0 ? borde : vereda;
                set(x, 0, z, d, TERRENO);
            }
        }
        // Avenida desde el porton hasta la plaza, con linea discontinua al medio.
        fill(28, 0, 0, 34, 0, 27, asfalto, TERRENO);
        for (int z = 1; z <= 27; z += 3) set(31, 0, z, linea, TERRENO);
        // Calle transversal.
        fill(2, 0, 22, 60, 0, 26, asfalto, TERRENO);
        for (int x = 3; x <= 59; x += 3) if (x < 28 || x > 34) set(x, 0, 24, linea, TERRENO);
        // Estacionamiento de la concesionaria, con lineas de cajones.
        fill(3, 0, 2, 25, 0, 8, asfalto, TERRENO);
        for (int x = 4; x <= 24; x += 4) fill(x, 0, 3, x, 0, 7, linea, TERRENO);
        // Patio del taller.
        fill(37, 0, 2, 59, 0, 5, Material.GRAY_CONCRETE.createBlockData(), TERRENO);
        // Bosque y mina.
        fill(1, 0, 42, 20, 0, 61, pasto, TERRENO);
        fill(42, 0, 30, 61, 0, 61, grava, TERRENO);
    }

    private void muralla() {
        BlockData ladrillo = Material.STONE_BRICKS.createBlockData();
        BlockData musgo = Material.MOSSY_STONE_BRICKS.createBlockData();
        BlockData losa = slab(Material.STONE_BRICK_SLAB, Slab.Type.BOTTOM);
        for (int i = 0; i < N; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, N - 1}, {0, i}, {N - 1, i}}) {
                int x = p[0], z = p[1];
                if (z == 0 && x >= 26 && x <= 36) continue; // porton
                set(x, 1, z, random.nextInt(5) == 0 ? musgo : ladrillo, EDIFICIOS);
                boolean poste = (x + z) % 8 == 0;
                set(x, 2, z, poste ? ladrillo : losa, EDIFICIOS);
                if (poste) set(x, 3, z, Material.LANTERN.createBlockData(), DETALLES);
            }
        }
        // Porton: dos columnas de cuarzo y una viga con el nombre en concreto.
        BlockData columna = Material.QUARTZ_PILLAR.createBlockData();
        for (int y = 1; y <= 6; y++) {
            set(25, y, 0, columna, EDIFICIOS);
            set(37, y, 0, columna, EDIFICIOS);
        }
        fill(25, 7, 0, 37, 7, 0, Material.SMOOTH_QUARTZ.createBlockData(), EDIFICIOS);
        fill(27, 6, 0, 35, 6, 0, Material.ORANGE_CONCRETE.createBlockData(), EDIFICIOS);
        set(25, 8, 0, Material.LANTERN.createBlockData(), DETALLES);
        set(37, 8, 0, Material.LANTERN.createBlockData(), DETALLES);
    }

    /** Vidriera de autos: paredes blancas, frente todo de vidrio, techo plano. */
    private void concesionaria() {
        int x0 = 4, x1 = 20, z0 = 10, z1 = 20, techo = 6;
        caja(x0, z0, x1, z1, techo, Material.WHITE_CONCRETE, Material.SMOOTH_QUARTZ);
        // Frente vidriado con puerta doble al medio.
        for (int x = x0 + 1; x < x1; x++) {
            for (int y = 1; y <= 4; y++) set(x, y, z0, panel(true), DETALLES);
        }
        set(12, 1, z0, puerta(BlockFace.SOUTH, Bisected.Half.BOTTOM, Door.Hinge.LEFT), DETALLES);
        set(12, 2, z0, puerta(BlockFace.SOUTH, Bisected.Half.TOP, Door.Hinge.LEFT), DETALLES);
        set(13, 1, z0, puerta(BlockFace.SOUTH, Bisected.Half.BOTTOM, Door.Hinge.RIGHT), DETALLES);
        set(13, 2, z0, puerta(BlockFace.SOUTH, Bisected.Half.TOP, Door.Hinge.RIGHT), DETALLES);
        // Cartel de la marca sobre la vidriera.
        fill(x0, 5, z0, x1, 5, z0, Material.LIGHT_BLUE_CONCRETE.createBlockData(), EDIFICIOS);
        // Piso de exhibicion y un mostrador.
        fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, Material.POLISHED_DIORITE.createBlockData(), TERRENO);
        fill(9, 1, 17, 15, 1, 17, Material.SMOOTH_QUARTZ.createBlockData(), DETALLES);
        set(9, 2, 17, Material.LANTERN.createBlockData(), DETALLES);
        set(15, 2, 17, Material.LANTERN.createBlockData(), DETALLES);
        luces(x0, z0, x1, z1, techo);
        npc("concesionaria", 12, 1, 15);
    }

    /** Taller industrial: concreto gris, porton de garaje abierto, yunques adentro. */
    private void tallerTaladros() {
        int x0 = 42, x1 = 58, z0 = 7, z1 = 20, techo = 7;
        caja(x0, z0, x1, z1, techo, Material.GRAY_CONCRETE, Material.SMOOTH_STONE);
        // Porton de garaje: hueco grande al frente con marco amarillo.
        fill(46, 1, z0, 54, 5, z0, Material.AIR.createBlockData(), EDIFICIOS);
        fill(45, 6, z0, 55, 6, z0, Material.YELLOW_CONCRETE.createBlockData(), EDIFICIOS);
        for (int y = 1; y <= 5; y++) {
            set(45, y, z0, Material.YELLOW_CONCRETE.createBlockData(), EDIFICIOS);
            set(55, y, z0, Material.YELLOW_CONCRETE.createBlockData(), EDIFICIOS);
        }
        // Ventanas con rejas en los costados.
        for (int z = z0 + 3; z < z1; z += 4) {
            set(x0, 3, z, barras(false), DETALLES);
            set(x1, 3, z, barras(false), DETALLES);
        }
        fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, Material.POLISHED_ANDESITE.createBlockData(), TERRENO);
        // Banco de trabajo al fondo.
        set(44, 1, 19, Material.ANVIL.createBlockData(), DETALLES);
        set(46, 1, 19, Material.SMITHING_TABLE.createBlockData(), DETALLES);
        set(48, 1, 19, Material.BLAST_FURNACE.createBlockData(), DETALLES);
        set(52, 1, 19, Material.GRINDSTONE.createBlockData(), DETALLES);
        set(56, 1, 19, Material.IRON_BLOCK.createBlockData(), DETALLES);
        luces(x0, z0, x1, z1, techo);
        npc("taladros", 50, 1, 15);
    }

    /** Ferreteria de barrio: madera de abeto, toldo a rayas, barriles y estantes. */
    private void ferreteria() {
        int x0 = 5, x1 = 19, z0 = 29, z1 = 39, techo = 5;
        caja(x0, z0, x1, z1, techo, Material.SPRUCE_PLANKS, Material.SPRUCE_SLAB);
        for (int y = 1; y <= techo; y++) {
            set(x0, y, z0, Material.STRIPPED_DARK_OAK_LOG.createBlockData(), EDIFICIOS);
            set(x1, y, z0, Material.STRIPPED_DARK_OAK_LOG.createBlockData(), EDIFICIOS);
            set(x0, y, z1, Material.STRIPPED_DARK_OAK_LOG.createBlockData(), EDIFICIOS);
            set(x1, y, z1, Material.STRIPPED_DARK_OAK_LOG.createBlockData(), EDIFICIOS);
        }
        // Vidriera y puerta.
        for (int x = x0 + 2; x <= x1 - 2; x++) if (x != 12) set(x, 2, z0, panel(true), DETALLES);
        set(12, 1, z0, puerta(BlockFace.SOUTH, Bisected.Half.BOTTOM, Door.Hinge.LEFT), DETALLES);
        set(12, 2, z0, puerta(BlockFace.SOUTH, Bisected.Half.TOP, Door.Hinge.LEFT), DETALLES);
        // Toldo a rayas rojas y blancas.
        for (int x = x0; x <= x1; x++) {
            Material lana = x % 2 == 0 ? Material.RED_WOOL : Material.WHITE_WOOL;
            set(x, 4, z0 - 1, Material.valueOf(lana.name().replace("_WOOL", "_CARPET")).createBlockData(), DETALLES);
            set(x, 3, z0 - 1, lana.createBlockData(), EDIFICIOS);
        }
        fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, Material.SPRUCE_PLANKS.createBlockData(), TERRENO);
        // Estantes y mercaderia.
        for (int x = x0 + 1; x < x1; x += 2) {
            set(x, 1, z1 - 1, Material.BARREL.createBlockData(), DETALLES);
            set(x, 2, z1 - 1, Material.CHISELED_BOOKSHELF.createBlockData(), DETALLES);
        }
        fill(9, 1, 34, 15, 1, 34, Material.SPRUCE_PLANKS.createBlockData(), DETALLES);
        luces(x0, z0, x1, z1, techo);
        npc("ferreteria", 12, 1, 32);
    }

    /** El edificio de la Gran Sede: dos pisos, columnas de cuarzo, ventanales y escalinata. */
    private void edificioCentral() {
        int x0 = 22, x1 = 40, z0 = 44, z1 = 59, techo = 12;
        caja(x0, z0, x1, z1, techo, Material.STONE_BRICKS, Material.SMOOTH_QUARTZ);
        // Fachada: columnas de cuarzo cada 3 y ventanales en ambos pisos.
        for (int x = x0; x <= x1; x++) {
            boolean columna = (x - x0) % 3 == 0;
            for (int y = 1; y < techo; y++) {
                if (columna) {
                    set(x, y, z0, Material.QUARTZ_PILLAR.createBlockData(), EDIFICIOS);
                } else if (y != 6 && y != 1) {
                    set(x, y, z0, panel(true), DETALLES);
                } else {
                    set(x, y, z0, Material.SMOOTH_QUARTZ.createBlockData(), EDIFICIOS);
                }
            }
        }
        // Ventanas de los costados y el fondo.
        for (int z = z0 + 2; z < z1; z += 3) {
            for (int y : new int[]{3, 4, 8, 9}) {
                set(x0, y, z, panel(false), DETALLES);
                set(x1, y, z, panel(false), DETALLES);
            }
        }
        // Entrada: hueco de 3 con puertas dobles de roble oscuro.
        fill(30, 1, z0, 32, 4, z0, Material.AIR.createBlockData(), EDIFICIOS);
        set(30, 1, z0, puerta(BlockFace.SOUTH, Bisected.Half.BOTTOM, Door.Hinge.LEFT, Material.DARK_OAK_DOOR), DETALLES);
        set(30, 2, z0, puerta(BlockFace.SOUTH, Bisected.Half.TOP, Door.Hinge.LEFT, Material.DARK_OAK_DOOR), DETALLES);
        set(32, 1, z0, puerta(BlockFace.SOUTH, Bisected.Half.BOTTOM, Door.Hinge.RIGHT, Material.DARK_OAK_DOOR), DETALLES);
        set(32, 2, z0, puerta(BlockFace.SOUTH, Bisected.Half.TOP, Door.Hinge.RIGHT, Material.DARK_OAK_DOOR), DETALLES);
        // Escalinata y marquesina.
        for (int x = 27; x <= 35; x++) {
            set(x, 1, z0 - 1, stairs(Material.QUARTZ_STAIRS, BlockFace.SOUTH), DETALLES);
        }
        fill(27, 5, z0 - 2, 35, 5, z0 - 1, slab(Material.SMOOTH_QUARTZ_SLAB, Slab.Type.BOTTOM), EDIFICIOS);
        fill(26, techo - 1, z0, 36, techo - 1, z0, Material.ORANGE_CONCRETE.createBlockData(), EDIFICIOS);
        // Piso, entrepiso y hall con alfombra.
        fill(x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, Material.POLISHED_DEEPSLATE.createBlockData(), TERRENO);
        fill(x0 + 1, 6, z0 + 1, x1 - 1, 6, z1 - 1, Material.DARK_OAK_PLANKS.createBlockData(), EDIFICIOS);
        fill(29, 6, z0 + 1, 33, 6, z0 + 5, Material.AIR.createBlockData(), EDIFICIOS); // doble altura en la entrada
        fill(30, 1, z0 + 1, 32, 1, z1 - 3, Material.RED_CARPET.createBlockData(), DETALLES);
        // Mostrador de recepcion.
        fill(27, 1, 54, 35, 1, 54, Material.POLISHED_BLACKSTONE_BRICKS.createBlockData(), DETALLES);
        fill(27, 2, 54, 35, 2, 54, slab(Material.SMOOTH_QUARTZ_SLAB, Slab.Type.BOTTOM), DETALLES);
        // Mastil con bandera de lana naranja en el techo.
        for (int y = techo + 1; y <= techo + 6; y++) set(31, y, 51, Material.OAK_FENCE.createBlockData(), DETALLES);
        fill(32, techo + 4, 51, 34, techo + 6, 51, Material.ORANGE_WOOL.createBlockData(), DETALLES);
        luces(x0, z0, x1, z1, techo);
        luces(x0, z0, x1, z1, 6);
    }

    /** Plaza con fuente y bancos, entre la calle y el edificio central. */
    private void plaza() {
        fill(22, 0, 27, 40, 0, 42, Material.POLISHED_ANDESITE.createBlockData(), TERRENO);
        int cx = 31, cz = 35;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean borde = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                if (borde) set(cx + dx, 1, cz + dz, Material.STONE_BRICKS.createBlockData(), EDIFICIOS);
                else set(cx + dx, 0, cz + dz, Material.PRISMARINE_BRICKS.createBlockData(), TERRENO);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                set(cx + dx, 1, cz + dz, Material.WATER.createBlockData(), DETALLES);
            }
        }
        for (int y = 1; y <= 3; y++) set(cx, y, cz, Material.QUARTZ_PILLAR.createBlockData(), EDIFICIOS);
        set(cx, 4, cz, Material.SEA_LANTERN.createBlockData(), DETALLES);
        // Bancos mirando a la fuente.
        for (int d = -1; d <= 1; d++) {
            // El respaldo (la parte alta) queda del lado opuesto a la fuente.
            set(cx + d, 1, cz - 6, stairs(Material.SPRUCE_STAIRS, BlockFace.NORTH), DETALLES);
            set(cx + d, 1, cz + 6, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH), DETALLES);
            set(cx - 6, 1, cz + d, stairs(Material.SPRUCE_STAIRS, BlockFace.WEST), DETALLES);
            set(cx + 6, 1, cz + d, stairs(Material.SPRUCE_STAIRS, BlockFace.EAST), DETALLES);
        }
        // Canteros con arbustos en las esquinas de la plaza.
        for (int[] e : new int[][]{{23, 28}, {39, 28}, {23, 41}, {39, 41}}) {
            set(e[0], 0, e[1], Material.GRASS_BLOCK.createBlockData(), TERRENO);
            set(e[0], 1, e[1], hojas(Material.AZALEA_LEAVES), DETALLES);
        }
        marcadores.add(new Marcador("spawn", "", 31, 1, 4));
    }

    /** Bosque de roble, abedul y abeto para talar. Las hojas no se rompen, solo los troncos. */
    private void bosque() {
        Material[][] tipos = {
                {Material.OAK_LOG, Material.OAK_LEAVES},
                {Material.BIRCH_LOG, Material.BIRCH_LEAVES},
                {Material.SPRUCE_LOG, Material.SPRUCE_LEAVES},
                {Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES},
        };
        int i = 0;
        for (int x = 4; x <= 18; x += 5) {
            for (int z = 45; z <= 59; z += 5) {
                Material[] t = tipos[i++ % tipos.length];
                int alto = 5 + random.nextInt(2);
                for (int y = 1; y <= alto; y++) set(x, y, z, t[0].createBlockData(), NATURAL);
                // Copa redondeada.
                for (int y = alto - 2; y <= alto + 1; y++) {
                    int r = y >= alto ? 1 : 2;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx == 0 && dz == 0 && y <= alto) continue;
                            if (Math.abs(dx) == r && Math.abs(dz) == r && random.nextBoolean()) continue;
                            setSiVacio(x + dx, y, z + dz, hojas(t[1]), NATURAL);
                        }
                    }
                }
            }
        }
        marcadores.add(new Marcador("bosque", "", 1, 1, 42));
        marcadores.add(new Marcador("bosque", "", 20, 9, 61));
    }

    /** Cerro de piedra con vetas y un tunel apuntalado con madera. */
    private void mina() {
        int x0 = 43, x1 = 60, z0 = 31, z1 = 60;
        double cx = (x0 + x1) / 2.0, cz = (z0 + z1) / 2.0, rx = (x1 - x0) / 2.0, rz = (z1 - z0) / 2.0;
        int maxAlto = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double d = Math.hypot((x - cx) / rx, (z - cz) / rz);
                if (d > 1) continue;
                int alto = 4 + (int) Math.round(7 * (1 - d)) + (random.nextInt(4) == 0 ? 1 : 0);
                maxAlto = Math.max(maxAlto, alto);
                for (int y = 1; y <= alto; y++) set(x, y, z, roca(), NATURAL);
            }
        }
        // Tunel que entra desde el lado de la plaza (oeste del cerro), 3 de ancho y 3 de alto.
        int tz0 = 44, tz1 = 46;
        for (int x = x0; x <= 54; x++) {
            for (int z = tz0; z <= tz1; z++) for (int y = 1; y <= 3; y++) quitar(x, y, z);
            if ((x - x0) % 3 == 0) {
                // Marco de madera: dos postes y una viga.
                set(x, 1, tz0 - 1, log(Material.OAK_LOG, Axis.Y), NATURAL);
                set(x, 2, tz0 - 1, log(Material.OAK_LOG, Axis.Y), NATURAL);
                set(x, 3, tz0 - 1, log(Material.OAK_LOG, Axis.Y), NATURAL);
                set(x, 1, tz1 + 1, log(Material.OAK_LOG, Axis.Y), NATURAL);
                set(x, 2, tz1 + 1, log(Material.OAK_LOG, Axis.Y), NATURAL);
                set(x, 3, tz1 + 1, log(Material.OAK_LOG, Axis.Y), NATURAL);
                for (int z = tz0 - 1; z <= tz1 + 1; z++) set(x, 4, z, log(Material.STRIPPED_OAK_LOG, Axis.Z), NATURAL);
                Lantern l = (Lantern) Material.LANTERN.createBlockData();
                l.setHanging(true);
                set(x, 3, tz0 + 1, l, DETALLES);
            }
        }
        // Vias que salen del tunel.
        for (int x = x0 - 3; x <= 54; x++) {
            Rail r = (Rail) Material.RAIL.createBlockData();
            r.setShape(Rail.Shape.EAST_WEST);
            set(x, 1, tz0 + 1, r, DETALLES);
        }
        // Barriles de herramientas en la entrada.
        set(x0 - 2, 1, tz0 - 2, Material.BARREL.createBlockData(), DETALLES);
        set(x0 - 2, 1, tz1 + 2, Material.BARREL.createBlockData(), DETALLES);
        marcadores.add(new Marcador("mina", "", x0, 1, z0));
        marcadores.add(new Marcador("mina", "", x1, maxAlto + 1, z1));
    }

    private void faroles() {
        for (int z = 3; z <= 21; z += 6) {
            farol(27, z);
            farol(35, z);
        }
        for (int x = 4; x <= 58; x += 9) {
            if (x >= 26 && x <= 36) continue;
            farol(x, 21);
            farol(x, 27);
        }
    }

    // ---------------------------------------------------------------
    // Ayudas de construccion
    // ---------------------------------------------------------------

    /** Paredes en el borde del rectangulo desde y=1 hasta techo-1, y techo plano en y=techo. */
    private void caja(int x0, int z0, int x1, int z1, int techo, Material pared, Material cubierta) {
        BlockData p = pared.createBlockData();
        BlockData c = cubierta.createBlockData();
        if (c instanceof Slab s) s.setType(Slab.Type.BOTTOM);
        for (int y = 1; y < techo; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, p, EDIFICIOS);
                set(x, y, z1, p, EDIFICIOS);
            }
            for (int z = z0; z <= z1; z++) {
                set(x0, y, z, p, EDIFICIOS);
                set(x1, y, z, p, EDIFICIOS);
            }
        }
        fill(x0, techo, z0, x1, techo, z1, c, EDIFICIOS);
        // Cornisa.
        for (int x = x0; x <= x1; x++) {
            set(x, techo, z0, Material.SMOOTH_STONE.createBlockData(), EDIFICIOS);
            set(x, techo, z1, Material.SMOOTH_STONE.createBlockData(), EDIFICIOS);
        }
    }

    /** Linternas colgando del techo, una cada 4 bloques. */
    private void luces(int x0, int z0, int x1, int z1, int techo) {
        for (int x = x0 + 2; x < x1; x += 4) {
            for (int z = z0 + 2; z < z1; z += 4) {
                Lantern l = (Lantern) Material.LANTERN.createBlockData();
                l.setHanging(true);
                setSiVacio(x, techo - 1, z, l, DETALLES);
            }
        }
    }

    private void farol(int x, int z) {
        set(x, 1, z, Material.STONE_BRICK_WALL.createBlockData(), DETALLES);
        set(x, 2, z, Material.DARK_OAK_FENCE.createBlockData(), DETALLES);
        set(x, 3, z, Material.DARK_OAK_FENCE.createBlockData(), DETALLES);
        set(x, 4, z, Material.LANTERN.createBlockData(), DETALLES);
    }

    private void npc(String tienda, int x, int y, int z) {
        marcadores.add(new Marcador("npc", tienda, x, y, z));
    }

    private BlockData roca() {
        int r = random.nextInt(100);
        Material m;
        if (r < 8) m = Material.COAL_ORE;
        else if (r < 12) m = Material.COPPER_ORE;
        else if (r < 15) m = Material.IRON_ORE;
        else if (r < 16) m = Material.GOLD_ORE;
        else if (r < 22) m = Material.ANDESITE;
        else m = Material.STONE;
        return m.createBlockData();
    }

    private void set(int x, int y, int z, BlockData data, int etapa) {
        if (x < 0 || x >= N || z < 0 || z >= N || y < 0) return;
        long k = key(x, y, z);
        if (data.getMaterial().isAir()) {
            piezas.remove(k);
            return;
        }
        piezas.put(k, new PlanoGranSede.Pieza(x, y, z, data.clone(), etapa));
    }

    private void setSiVacio(int x, int y, int z, BlockData data, int etapa) {
        if (!piezas.containsKey(key(x, y, z))) set(x, y, z, data, etapa);
    }

    private void quitar(int x, int y, int z) {
        piezas.remove(key(x, y, z));
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockData data, int etapa) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                    set(x, y, z, data, etapa);
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) | ((long) (y & 0xFFFF) << 20) | (z & 0xFFFFF);
    }

    private static BlockData slab(Material m, Slab.Type tipo) {
        Slab s = (Slab) m.createBlockData();
        s.setType(tipo);
        return s;
    }

    private static BlockData stairs(Material m, BlockFace sube) {
        Stairs s = (Stairs) m.createBlockData();
        s.setFacing(sube);
        s.setHalf(Bisected.Half.BOTTOM);
        return s;
    }

    private static BlockData log(Material m, Axis eje) {
        Orientable o = (Orientable) m.createBlockData();
        o.setAxis(eje);
        return o;
    }

    private static BlockData hojas(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof Leaves l) l.setPersistent(true); // que no se pudran
        return d;
    }

    /** Vidrio que une en el eje del frente (este-oeste) o de los costados (norte-sur). */
    private static BlockData panel(boolean esteOeste) {
        MultipleFacing p = (MultipleFacing) Material.GLASS_PANE.createBlockData();
        if (esteOeste) {
            p.setFace(BlockFace.EAST, true);
            p.setFace(BlockFace.WEST, true);
        } else {
            p.setFace(BlockFace.NORTH, true);
            p.setFace(BlockFace.SOUTH, true);
        }
        return p;
    }

    private static BlockData barras(boolean esteOeste) {
        MultipleFacing p = (MultipleFacing) Material.IRON_BARS.createBlockData();
        if (esteOeste) {
            p.setFace(BlockFace.EAST, true);
            p.setFace(BlockFace.WEST, true);
        } else {
            p.setFace(BlockFace.NORTH, true);
            p.setFace(BlockFace.SOUTH, true);
        }
        return p;
    }

    private static BlockData puerta(BlockFace mira, Bisected.Half mitad, Door.Hinge bisagra) {
        return puerta(mira, mitad, bisagra, Material.SPRUCE_DOOR);
    }

    private static BlockData puerta(BlockFace mira, Bisected.Half mitad, Door.Hinge bisagra, Material m) {
        Door d = (Door) m.createBlockData();
        d.setFacing(mira);
        d.setHalf(mitad);
        d.setHinge(bisagra);
        return d;
    }
}
