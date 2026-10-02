package com.isjbar.minercorp.vehicles.model;

import com.isjbar.minercorp.vehicles.type.VehicleType;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Furnace;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Carroceria de un vehiculo: solo dibuja, no mueve ni colisiona.
 *
 * Es un {@link Display} raiz en el centro del vehiculo mas una pieza por
 * cada {@link PartDef} de su {@link Shape}, todas en la misma posicion y con
 * su caja en la transformacion. {@link #moveTo} las teletransporta juntas
 * (con teleportDuration para que el cliente interpole) y avanza las
 * animaciones: ruedas que giran y doblan, punta del taladro, motor.
 *
 * Las piezas con modelo de resource pack ({@link PartDef#itemModel()}) son
 * {@link ItemDisplay}; el resto, {@link BlockDisplay}. Si el tipo tiene
 * "item-model" en el config, en vez de las piezas se dibuja con un solo
 * ItemDisplay con ese modelo.
 */
public final class VehicleModel {

    private static final int TELEPORT_TICKS = 2;
    private static final int ANIM_TICKS = 2;
    /** Maximo que gira la llanta por actualizacion: mas que esto y el ojo lo ve girar al reves. */
    private static final float MAX_SPIN_STEP = (float) Math.toRadians(35);
    private static final float MAX_STEER = (float) Math.toRadians(25);
    private static final float BIT_SPIN_STEP = 50f;

    private final Shape shape;
    private final Display root;
    private final List<Display> parts;
    /** Piezas que se animan, con su definicion. */
    private final List<Animated> animated;
    private final BlockDisplay motor;

    private Location last;
    private boolean drilling;
    private float wheelSpin;
    private float steer;
    private float shownSteer;
    private float bitSpin;
    private int ticks;

    private record Animated(PartDef def, Display display) {
    }

    private VehicleModel(Shape shape, Display root, List<Display> parts, List<Animated> animated, BlockDisplay motor) {
        this.shape = shape;
        this.root = root;
        this.parts = parts;
        this.animated = animated;
        this.motor = motor;
        this.last = root.getLocation();
    }

    // ------------------------------------------------------------------ armado

    /** Arma el modelo del tipo en la posicion (y yaw) indicada. */
    public static VehicleModel spawn(Plugin plugin, Shape shape, VehicleType type, Location location, Component nombre) {
        Location base = location.clone();
        base.setPitch(0);
        World world = base.getWorld();
        List<Display> parts = new ArrayList<>();
        List<Animated> animated = new ArrayList<>();
        BlockDisplay motor = null;

        Display root = null;
        if (type.itemModel() != null) {
            float s = type.escalaItemModel();
            root = world.spawn(base, ItemDisplay.class, d -> {
                setup(plugin, d, "chasis");
                d.setItemStack(modelItem(type.itemModel()));
                d.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), new Quaternionf(),
                        new Vector3f(s, s, s), new Quaternionf()));
            });
            parts.add(root);
        } else {
            for (PartDef def : shape.parts()) {
                Display display;
                if (def.itemModel() != null) {
                    display = world.spawn(base, ItemDisplay.class, d -> {
                        setup(plugin, d, def.role());
                        d.setItemStack(modelItem(def.itemModel()));
                        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        d.setTransformation(def.transform());
                    });
                } else {
                    BlockData data = def.kind() == PartDef.Kind.MOTOR ? motorData(false)
                            : (def.slot() == null ? def.material() : type.color(def.slot(), def.material())).createBlockData();
                    display = world.spawn(base, BlockDisplay.class, d -> {
                        setup(plugin, d, def.role());
                        d.setBlock(data);
                        d.setTransformation(def.transform());
                        if (def.glow()) d.setBrightness(new Display.Brightness(15, 15));
                    });
                }
                parts.add(display);
                if (root == null) root = display;
                if (def.kind() == PartDef.Kind.MOTOR && display instanceof BlockDisplay b) motor = b;
                else if (def.kind() != PartDef.Kind.STATIC) animated.add(new Animated(def, display));
            }
        }

        TextDisplay label = world.spawn(base, TextDisplay.class, d -> {
            setup(plugin, d, "nombre");
            d.text(nombre);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, shape.labelHeight(), 0f), new Quaternionf(),
                    new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
        });
        parts.add(label);

        // La raiz guarda los UUID de todas las piezas para recuperarlas tras un reinicio.
        StringBuilder ids = new StringBuilder();
        for (Display part : parts) {
            if (part == root) continue;
            if (!ids.isEmpty()) ids.append(',');
            ids.append(part.getUniqueId());
        }
        root.getPersistentDataContainer().set(partsKey(plugin), PersistentDataType.STRING, ids.toString());
        return new VehicleModel(shape, root, parts, animated, motor);
    }

    /** Recupera un modelo ya armado a partir del UUID de su raiz ({@link #rootId()}). */
    public static Optional<VehicleModel> find(Plugin plugin, Shape shape, UUID rootId) {
        if (!(plugin.getServer().getEntity(rootId) instanceof Display root)) return Optional.empty();
        String ids = root.getPersistentDataContainer().get(partsKey(plugin), PersistentDataType.STRING);
        if (ids == null) return Optional.empty();

        List<Display> parts = new ArrayList<>();
        parts.add(root);
        for (String id : ids.split(",")) {
            if (id.isBlank()) continue;
            if (plugin.getServer().getEntity(UUID.fromString(id)) instanceof Display part) parts.add(part);
        }
        Map<String, PartDef> byRole = new HashMap<>();
        for (PartDef def : shape.parts()) byRole.put(def.role(), def);
        List<Animated> animated = new ArrayList<>();
        BlockDisplay motor = null;
        for (Display part : parts) {
            String role = part.getPersistentDataContainer().get(partKey(plugin), PersistentDataType.STRING);
            PartDef def = role == null ? null : byRole.get(role);
            if (def == null || def.kind() == PartDef.Kind.STATIC) continue;
            // Una pieza de bloque donde la forma espera un modelo (o al reves) no se anima.
            if ((def.itemModel() != null) != (part instanceof ItemDisplay)) continue;
            if (def.kind() == PartDef.Kind.MOTOR) {
                if (part instanceof BlockDisplay b) motor = b;
            } else {
                animated.add(new Animated(def, part));
            }
        }
        return Optional.of(new VehicleModel(shape, root, parts, animated, motor));
    }

    /** UUIDs de todas las piezas de un modelo (raiz incluida), aunque no esten cargadas. */
    public static List<UUID> partIds(Plugin plugin, Entity root) {
        List<UUID> ids = new ArrayList<>();
        ids.add(root.getUniqueId());
        String list = root.getPersistentDataContainer().get(partsKey(plugin), PersistentDataType.STRING);
        if (list == null) return ids;
        for (String id : list.split(",")) {
            if (!id.isBlank()) ids.add(UUID.fromString(id));
        }
        return ids;
    }

    /** true si la entidad es una pieza de algun modelo de vehiculo. */
    public static boolean isModelPart(Plugin plugin, Entity entity) {
        return entity instanceof Display
                && entity.getPersistentDataContainer().has(partKey(plugin), PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- en uso

    public UUID rootId() {
        return root.getUniqueId();
    }

    public Location location() {
        return root.getLocation();
    }

    public boolean isValid() {
        return root.isValid();
    }

    /**
     * Lleva todas las piezas a la posicion, yaw y pitch del vehiculo y avanza
     * las animaciones. Llamarlo una vez por tick mientras se maneja.
     *
     * @param speed      bloques por tick (negativo marcha atras), para girar las ruedas
     * @param steerInput -1 doblando a la izquierda, 1 a la derecha, 0 derecho
     */
    public void moveTo(Location location, double speed, int steerInput) {
        boolean moved = last.getWorld() != location.getWorld()
                || last.distanceSquared(location) > 1.0E-6
                || Math.abs(last.getYaw() - location.getYaw()) > 0.01f
                || Math.abs(last.getPitch() - location.getPitch()) > 0.01f;
        last = location.clone();
        if (moved) {
            for (Display part : parts) {
                if (part.isValid()) part.teleport(last);
            }
        }
        ticks++;
        steer = -steerInput * MAX_STEER;
        float step = (float) (speed / Math.max(0.1, wheelRadius()));
        wheelSpin += Math.max(-MAX_SPIN_STEP / ANIM_TICKS, Math.min(MAX_SPIN_STEP / ANIM_TICKS, step));
        if (ticks % ANIM_TICKS == 0) animate(Math.abs(speed) > 0.005);
    }

    /** Mientras perfora: la punta gira, el motor se enciende y salen chispas, humo y sonido. */
    public void setDrilling(boolean drilling) {
        if (this.drilling == drilling) return;
        this.drilling = drilling;
        if (motor != null && motor.isValid()) motor.setBlock(motorData(drilling));
    }

    /** Efecto de un bloque roto por la punta. Llamarlo antes de poner el bloque en AIR. */
    public void onBlockBroken(Block block) {
        BlockData data = block.getBlockData();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawnParticle(Particle.BLOCK, center, 12, 0.3, 0.3, 0.3, 0, data);
        block.getWorld().playSound(center, data.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 0.6f, 0.9f);
    }

    /** Humo del escape (al acelerar). */
    public void exhaust() {
        Vector3f e = shape.exhaust();
        if (e == null) return;
        last.getWorld().spawnParticle(Particle.SMOKE, local(last, e.x, e.y, e.z), 2, 0.03, 0.03, 0.03, 0.01);
    }

    public void remove() {
        for (Display part : parts) {
            if (part.isValid()) part.remove();
        }
    }

    // ------------------------------------------------------------- animacion

    private void animate(boolean rolling) {
        // Ruedas: doblan con la direccion y giran al andar. Punta: gira al perforar.
        boolean steerChanged = Math.abs(steer - shownSteer) > 1.0E-3;
        shownSteer = steer;
        boolean spinBit = drilling && shape.bitAxis() != null;
        if (spinBit) bitSpin = (bitSpin + BIT_SPIN_STEP) % 360f;
        for (Animated a : animated) {
            PartDef def = a.def();
            switch (def.kind()) {
                case TIRE -> {
                    WheelDef w = shape.wheels().get(def.index());
                    if (w.steers() && steerChanged) update(a.display(), Shapes.tireTransform(w, steer));
                }
                case HUB -> {
                    WheelDef w = shape.wheels().get(def.index());
                    if (rolling || (w.steers() && steerChanged)) {
                        update(a.display(), Shapes.hubTransform(w, steer, wheelSpin));
                    }
                }
                case WHEEL -> {
                    WheelDef w = shape.wheels().get(def.index());
                    if (rolling || (w.steers() && steerChanged)) {
                        update(a.display(), Shapes.wheelModelTransform(def, w, steer, wheelSpin));
                    }
                }
                // Cada pieza de bloques gira un poco desfasada para que se note el giro.
                case BIT -> {
                    if (spinBit) update(a.display(), Shapes.bitTransform(def.index(), bitSpin + def.index() * 15f));
                }
                case BIT_MODEL -> {
                    if (spinBit) update(a.display(), Shapes.bitModelTransform(def, bitSpin));
                }
                default -> { }
            }
        }

        if (!spinBit) return;
        World world = last.getWorld();
        Vector3f axis = shape.bitAxis();
        world.spawnParticle(Particle.ELECTRIC_SPARK, local(last, axis.x, axis.y, shape.bitTipZ()),
                4, 0.15, 0.15, 0.15, 0.05);
        if (ticks % 4 == 0) {
            Vector3f e = shape.exhaust();
            world.spawnParticle(Particle.LARGE_SMOKE, local(last, e.x, e.y, e.z), 1, 0.05, 0.05, 0.05, 0.01);
        }
        if (ticks % 10 == 0) {
            world.playSound(local(last, 0f, axis.y, shape.bitTipZ() - 0.5f), Sound.BLOCK_GRINDSTONE_USE,
                    SoundCategory.BLOCKS, 0.5f, 0.7f);
        }
    }

    private static void update(Display display, Transformation transformation) {
        if (display == null || !display.isValid()) return;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ANIM_TICKS);
        display.setTransformation(transformation);
    }

    private float wheelRadius() {
        return shape.wheels().isEmpty() ? 0.5f : shape.wheels().get(0).radius();
    }

    // ----------------------------------------------------------------- helpers

    private static void setup(Plugin plugin, Display display, String role) {
        display.setPersistent(true);
        display.setTeleportDuration(TELEPORT_TICKS);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ANIM_TICKS);
        display.getPersistentDataContainer().set(partKey(plugin), PersistentDataType.STRING, role);
    }

    /** Pasa un punto del sistema local del modelo a coordenadas del mundo (con yaw y pitch). */
    public static Location local(Location base, float x, float y, float z) {
        double pitch = Math.toRadians(base.getPitch());
        // Pitch: positivo es nariz abajo. Gira (y, z) alrededor del eje X local.
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double ly = y * cp - z * sp;
        double lz = y * sp + z * cp;
        double yaw = Math.toRadians(base.getYaw());
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        // Frente (+Z local) = (-sin, 0, cos); izquierda (+X local) = (cos, 0, sin).
        return base.clone().add(x * cos - lz * sin, ly, x * sin + lz * cos);
    }

    private static BlockData motorData(boolean lit) {
        BlockData data = Material.BLAST_FURNACE.createBlockData();
        if (data instanceof Furnace furnace) {
            furnace.setFacing(BlockFace.NORTH); // boca hacia -Z local, o sea hacia atras
            furnace.setLit(lit);
        }
        return data;
    }

    /** Un papel con el modelo del resource pack (el item no importa, solo su item_model). */
    private static ItemStack modelItem(String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        NamespacedKey key = NamespacedKey.fromString(model);
        if (key != null) {
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(key);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static NamespacedKey partKey(Plugin plugin) {
        return new NamespacedKey(plugin, "pieza");
    }

    private static NamespacedKey partsKey(Plugin plugin) {
        return new NamespacedKey(plugin, "piezas");
    }
}
