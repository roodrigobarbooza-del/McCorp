package com.isjbar.minercorp.gransede.obra;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.Structure;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.structure.UsageMode;
import org.bukkit.structure.Palette;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * El plano de la Gran Sede: bloques en coordenadas locales repartidos en
 * etapas, mas los marcadores (spawn, vendedores, mina, bosque).
 *
 * Mismas coordenadas que la sede de cada empresa (SedeBlueprint en Mining):
 * x de 0 a ancho-1, z de 0 a fondo-1, y=0 es el piso y va al nivel del
 * suelo, el frente (la entrada) es el lado z=0.
 *
 * Se puede reemplazar por un {@code .nbt} guardado con un bloque de
 * estructura vanilla. Los marcadores van como bloques de estructura en modo
 * DATA con metadata {@code spawn}, {@code npc:<tienda>}, {@code mina} o
 * {@code bosque} (la mina y el bosque llevan dos, uno en cada esquina).
 */
public final class PlanoGranSede {

    public static final String[] ETAPAS = {"Terreno", "Edificios", "Mina y bosque", "Detalles"};

    public record Pieza(int x, int y, int z, BlockData data, int etapa) {
    }

    private final int ancho, alto, fondo;
    private final List<Pieza> piezas;
    private final int[] finDeEtapa;
    private final List<Marcador> marcadores;

    PlanoGranSede(int ancho, int alto, int fondo, List<Pieza> piezas, List<Marcador> marcadores) {
        this.ancho = ancho;
        this.alto = alto;
        this.fondo = fondo;
        List<Pieza> ordenadas = new ArrayList<>(piezas);
        // Por etapa y de abajo hacia arriba, asi se ve crecer cada parte.
        ordenadas.sort(Comparator.comparingInt(Pieza::etapa)
                .thenComparingInt(Pieza::y)
                .thenComparingInt(Pieza::z)
                .thenComparingInt(Pieza::x));
        this.piezas = List.copyOf(ordenadas);
        this.finDeEtapa = new int[ETAPAS.length];
        for (int i = 0; i < this.piezas.size(); i++) finDeEtapa[this.piezas.get(i).etapa()] = i + 1;
        for (int i = 1; i < finDeEtapa.length; i++) finDeEtapa[i] = Math.max(finDeEtapa[i], finDeEtapa[i - 1]);
        this.marcadores = List.copyOf(marcadores);
    }

    public int ancho() { return ancho; }
    public int alto() { return alto; }
    public int fondo() { return fondo; }
    public List<Pieza> piezas() { return piezas; }
    public List<Marcador> marcadores() { return marcadores; }

    /** Etapa a la que pertenece la pieza numero {@code indice} del orden de construccion. */
    public int etapaDe(int indice) {
        for (int e = 0; e < finDeEtapa.length; e++) if (indice < finDeEtapa[e]) return e;
        return finDeEtapa.length - 1;
    }

    public List<Marcador> marcadores(String tipo) {
        return marcadores.stream().filter(m -> m.tipo().equals(tipo)).toList();
    }

    // ---------------------------------------------------------------
    // Carga
    // ---------------------------------------------------------------

    /** Usa el {@code .nbt} si existe; si no, la Gran Sede que trae el plugin. */
    public static PlanoGranSede cargar(File archivo, Logger log) {
        if (archivo.isFile()) {
            try {
                org.bukkit.structure.Structure estructura = Bukkit.getStructureManager().loadStructure(archivo);
                if (estructura.getPalettes().isEmpty()) throw new IOException("la estructura no tiene bloques");
                Palette palette = estructura.getPalettes().get(0);
                List<Pieza> piezas = new ArrayList<>();
                List<Marcador> marcadores = new ArrayList<>();
                for (BlockState state : palette.getBlocks()) {
                    int x = state.getX(), y = state.getY(), z = state.getZ();
                    if (state instanceof Structure s && s.getUsageMode() == UsageMode.DATA) {
                        marcador(s.getMetadata(), x, y, z).ifPresent(marcadores::add);
                        continue;
                    }
                    BlockData data = state.getBlockData();
                    Material m = data.getMaterial();
                    if (m.isAir() || m == Material.STRUCTURE_VOID || m == Material.STRUCTURE_BLOCK) continue;
                    piezas.add(new Pieza(x, y, z, data, etapaPorMaterial(data, y)));
                }
                BlockVector size = estructura.getSize();
                log.info("Gran Sede cargada desde " + archivo.getName() + " (" + piezas.size() + " bloques, "
                        + marcadores.size() + " marcadores).");
                return new PlanoGranSede(size.getBlockX(), size.getBlockY(), size.getBlockZ(), piezas, marcadores);
            } catch (IOException | RuntimeException e) {
                log.warning("No se pudo leer " + archivo.getName() + " (" + e.getMessage() + "); se usa la Gran Sede por defecto.");
            }
        }
        return PlanoPorDefecto.crear();
    }

    private static java.util.Optional<Marcador> marcador(String metadata, int x, int y, int z) {
        String m = metadata == null ? "" : metadata.trim().toLowerCase(Locale.ROOT);
        if (m.equals("spawn") || m.equals("mina") || m.equals("bosque")) return java.util.Optional.of(new Marcador(m, "", x, y, z));
        if (m.startsWith("npc:") && m.length() > 4) return java.util.Optional.of(new Marcador("npc", m.substring(4), x, y, z));
        return java.util.Optional.empty();
    }

    /** Para planos .nbt, que no traen etapas: se deducen del material (como la sede de empresa). */
    static int etapaPorMaterial(BlockData data, int y) {
        Material m = data.getMaterial();
        if (y == 0) return 0;
        if (Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || m == Material.STONE || m.name().endsWith("_ORE")) return 2;
        if (!m.isOccluding() || m.isInteractable() || data instanceof Directional) return 3;
        return 1;
    }
}
