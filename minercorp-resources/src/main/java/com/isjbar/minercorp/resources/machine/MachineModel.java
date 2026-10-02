package com.isjbar.minercorp.resources.machine;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se ve de una maquina: su cuerpo (detras del panel), las piezas que
 * se mueven y un cartel con el nombre y el estado. Solo dibuja; la logica esta
 * en {@link Machine}.
 *
 * Dos modos, con la misma geometria ({@link MachineGeometry}):
 * <ul>
 *   <li>sin resource pack: cada caja es un BlockDisplay de un bloque vanilla;</li>
 *   <li>con resource pack: cada pieza es un ItemDisplay con su modelo propio
 *       (texturas de chapa, ladrillo refractario, rejilla, etc).</li>
 * </ul>
 *
 * Las entidades no se guardan con el mundo (persistent = false): se arman al
 * cargar el chunk del panel y se borran al descargarlo.
 */
public final class MachineModel {

    private static final Vector3f ROT_CENTER = new Vector3f(0.5f, 0f, 0.5f);

    /** Una pieza armada: sus entidades y como se mueve. */
    private static final class Piece {
        final MachineGeometry.Part part;
        final List<Display> displays = new ArrayList<>();
        /** Para cada display de bloque, su caja; null en el modo pack. */
        final List<MachineGeometry.Box> boxes = new ArrayList<>();
        float angle;
        float slide;

        Piece(MachineGeometry.Part part) {
            this.part = part;
        }
    }

    private final Plugin plugin;
    private final MachineGeometry geometry;
    private final Location base;
    private final Quaternionf facing;
    private final boolean pack;
    private final Quaternionf packFix;
    private final List<Display> all = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();
    private TextDisplay label;

    private boolean running;
    private int ticks;

    private MachineModel(Plugin plugin, MachineGeometry geometry, Location base, int quarterTurns, boolean pack, float packFixDeg) {
        this.plugin = plugin;
        this.geometry = geometry;
        this.base = base;
        this.facing = facing(quarterTurns);
        this.pack = pack;
        this.packFix = new Quaternionf().rotateY((float) Math.toRadians(-packFixDeg));
    }

    /** Rotacion de la maquina segun hacia donde miraba el jugador al colocarla (de a 90 grados). */
    static Quaternionf facing(int quarterTurns) {
        return new Quaternionf().rotateY((float) Math.toRadians(-90.0 * quarterTurns));
    }

    /** Pasa un punto local (bloques, relativo al panel) a un offset desde la esquina del panel. */
    static Vector3f toWorld(Quaternionf facing, Vector3f local) {
        return facing.transform(new Vector3f(local).sub(ROT_CENTER)).add(ROT_CENTER);
    }

    public static MachineModel spawn(Plugin plugin, Machine machine, MachineGeometry geometry, boolean pack, float packFixDeg) {
        MachineModel m = new MachineModel(plugin, geometry, machine.block().getLocation(), machine.facing(), pack, packFixDeg);
        World world = m.base.getWorld();
        for (MachineGeometry.Part part : geometry.parts()) {
            Piece piece = new Piece(part);
            if (pack && part.model() != null) {
                ItemStack item = new ItemStack(org.bukkit.Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                meta.setItemModel(part.model());
                item.setItemMeta(meta);
                ItemDisplay d = world.spawn(m.base, ItemDisplay.class, e -> {
                    setup(plugin, e);
                    e.setItemStack(item);
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                });
                piece.displays.add(d);
                piece.boxes.add(null);
            } else {
                for (MachineGeometry.Box box : part.boxes()) {
                    BlockDisplay d = world.spawn(m.base, BlockDisplay.class, e -> {
                        setup(plugin, e);
                        e.setBlock(box.block().createBlockData());
                    });
                    piece.displays.add(d);
                    piece.boxes.add(box);
                }
            }
            if ("luz".equals(part.anim())) {
                for (Display d : piece.displays) d.setBrightness(new Display.Brightness(15, 15));
            }
            m.pieces.add(piece);
            m.all.addAll(piece.displays);
            m.place(piece, 0);
        }
        Vector3f labelPos = toWorld(m.facing, geometry.label());
        m.label = world.spawn(m.base, TextDisplay.class, d -> {
            setup(plugin, d);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x50, 0, 0, 0));
            d.setTransformation(new Transformation(labelPos, new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
        });
        m.all.add(m.label);
        return m;
    }

    /** true si la entidad es una pieza de un modelo de maquina (para limpiar restos). */
    public static boolean isModelPart(Plugin plugin, Entity entity) {
        return entity instanceof Display && entity.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- uso

    public void setStatus(Component text, boolean running) {
        if (label != null && label.isValid()) label.text(text);
        if (this.running == running) return;
        this.running = running;
        for (Piece piece : pieces) {
            if ("luz".equals(piece.part.anim())) place(piece, 0);
        }
    }

    /** Llamarlo en cada paso de la maquina; {@code step} = ticks que pasaron. */
    public void animate(int step, boolean particles, boolean sounds) {
        if (!running) return;
        int before = ticks;
        ticks += step;
        for (Piece piece : pieces) {
            MachineGeometry.Part part = piece.part;
            if (part.anim() == null || part.anim().equals("luz")) continue;
            int period = (int) part.arg("ticks", 20);
            if (before / period == ticks / period) continue;
            boolean phase = (ticks / period) % 2 == 0;
            switch (part.anim()) {
                case "giro-y", "giro-x" -> piece.angle = (piece.angle + (float) part.arg("paso", 45)) % 360f;
                case "balanceo-x" -> piece.angle = (float) (phase ? part.arg("angulo", 15) : -part.arg("angulo", 15));
                case "vaiven-y" -> piece.slide = (float) (phase ? part.arg("amplitud", 0.3) : -part.arg("amplitud", 0.3));
                default -> {
                }
            }
            place(piece, period);
        }
        World world = base.getWorld();
        if (particles && before / 10 != ticks / 10) {
            for (Vector3f p : geometry.smoke()) world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at(p), 1, 0.05, 0.05, 0.05, 0.01);
            for (Vector3f p : geometry.flames()) world.spawnParticle(Particle.FLAME, at(p), 3, 0.08, 0.1, 0.08, 0.01);
            for (Vector3f p : geometry.sparks()) world.spawnParticle(Particle.BLOCK, at(p), 3, 0.15, 0.02, 0.15, 0,
                    org.bukkit.Material.COAL_ORE.createBlockData());
        }
        int every = Math.max(1, geometry.soundEvery());
        if (sounds && before / every != ticks / every) {
            world.playSound(at(geometry.label()), geometry.sound(), SoundCategory.BLOCKS, geometry.soundVolume(), geometry.soundPitch());
        }
    }

    public void remove() {
        for (Display d : all) {
            if (d.isValid()) d.remove();
        }
        all.clear();
        pieces.clear();
    }

    // ---------------------------------------------------------------- dibujo

    /** Ubica las entidades de la pieza segun su animacion actual, interpolando {@code ticks}. */
    private void place(Piece piece, int ticks) {
        MachineGeometry.Part part = piece.part;
        Quaternionf anim = new Quaternionf();
        if ("giro-y".equals(part.anim())) anim.rotateY((float) Math.toRadians(piece.angle));
        else if ("giro-x".equals(part.anim()) || "balanceo-x".equals(part.anim())) anim.rotateX((float) Math.toRadians(piece.angle));
        Vector3f slide = new Vector3f(0f, piece.slide, 0f);
        boolean hidden = "luz".equals(part.anim()) && !running;
        Vector3f pivot = part.pivot();

        for (int i = 0; i < piece.displays.size(); i++) {
            Display d = piece.displays.get(i);
            if (!d.isValid()) continue;
            MachineGeometry.Box box = piece.boxes.get(i);
            Vector3f anchor;
            Quaternionf rot;
            Vector3f scale;
            if (box == null) {
                // Modelo del pack: centrado en part.center(), escalado por 1/k.
                anchor = new Vector3f(part.center());
                rot = new Quaternionf(anim).mul(packFix);
                scale = new Vector3f(1f / part.k());
            } else {
                Quaternionf boxRot = new Quaternionf();
                anchor = new Vector3f(box.from());
                if (box.axis() != 0) {
                    float a = (float) Math.toRadians(45);
                    if (box.axis() == 'x') boxRot.rotateX(a);
                    else if (box.axis() == 'y') boxRot.rotateY(a);
                    else boxRot.rotateZ(a);
                    anchor = boxRot.transform(new Vector3f(box.from()).sub(box.origin())).add(box.origin());
                }
                rot = new Quaternionf(anim).mul(boxRot);
                scale = new Vector3f(box.to()).sub(box.from());
            }
            Vector3f local = anim.transform(anchor.sub(pivot)).add(pivot).add(slide);
            Vector3f t = toWorld(facing, local);
            Quaternionf r = new Quaternionf(facing).mul(rot);
            if (hidden) scale.set(0f);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(new Transformation(t, r, scale, new Quaternionf()));
        }
    }

    private Location at(Vector3f local) {
        Vector3f v = toWorld(facing, local);
        return base.clone().add(v.x, v.y, v.z);
    }

    private static void setup(Plugin plugin, Display display) {
        display.setPersistent(false);
        display.setViewRange(2.0f);
        display.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "modelo_maquina");
    }
}
