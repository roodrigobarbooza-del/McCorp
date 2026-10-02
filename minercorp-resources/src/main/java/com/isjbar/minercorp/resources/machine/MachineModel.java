package com.isjbar.minercorp.resources.machine;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se ve de una maquina arriba de su bloque: un modelo armado con
 * BlockDisplay (bloques vanilla, sin resourcepack) y un cartel con el nombre
 * y el estado. Solo dibuja; la logica esta en {@link Machine}.
 *
 * Las piezas no se guardan con el mundo (persistent = false): se arman cuando
 * se carga el chunk de la maquina y se borran cuando se descarga, asi nunca
 * quedan modelos huerfanos.
 *
 * Sistema local: el origen es la esquina minima del bloque de la maquina, +Y
 * arriba, y el modelo se gira de a 90 grados sobre el centro del bloque segun
 * hacia donde miraba el jugador al colocarla ("frente" = -Z local).
 */
public final class MachineModel {

    private static final int ROCK_TICKS = 20;
    private static final float ROCK_DEG = 18f;
    private static final int SPIN_TICKS = 10;

    private final Plugin plugin;
    private final String model;
    private final Location base;
    private final Quaternionf facing;
    private final List<Display> parts = new ArrayList<>();
    /** Piezas que se animan (balancin de la bomba, barra de la perforadora). */
    private final List<BlockDisplay> moving = new ArrayList<>();
    private final List<float[]> movingBoxes = new ArrayList<>();
    private BlockDisplay lamp;
    private TextDisplay label;

    private boolean running;
    private int ticks;
    private float angle;

    private MachineModel(Plugin plugin, String model, Location base, int quarterTurns) {
        this.plugin = plugin;
        this.model = model;
        this.base = base;
        this.facing = new Quaternionf().rotateY((float) Math.toRadians(-90.0 * quarterTurns));
    }

    public static MachineModel spawn(Plugin plugin, Machine machine) {
        Location base = machine.block().getLocation();
        MachineModel m = new MachineModel(plugin, machine.type().model(), base, machine.facing());
        float labelY = switch (machine.type().model()) {
            case "perforadora" -> m.buildDrill();
            case "bomba" -> m.buildPumpjack();
            case "horno" -> m.buildCokeOven();
            default -> m.buildRefinery();
        };
        m.label = base.getWorld().spawn(base, TextDisplay.class, d -> {
            setup(plugin, d);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(0.5f, labelY, 0.5f), new Quaternionf(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
        });
        m.parts.add(m.label);
        return m;
    }

    /** true si la entidad es una pieza de un modelo de maquina (para limpiar restos). */
    public static boolean isModelPart(Plugin plugin, Entity entity) {
        return entity instanceof Display && entity.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- modelos

    /** Torre de perforacion: cuatro patas amarillas, motor arriba y la barra que gira y baja al pozo. */
    private float buildDrill() {
        BlockData frame = Material.YELLOW_CONCRETE.createBlockData();
        float[][] legs = {{0.05f, 0.05f}, {0.83f, 0.05f}, {0.05f, 0.83f}, {0.83f, 0.83f}};
        for (float[] leg : legs) part(frame, leg[0], 1.0f, leg[1], 0.12f, 1.7f, 0.12f);
        // Travesanos a media altura.
        part(frame, 0.05f, 1.75f, 0.05f, 0.9f, 0.08f, 0.08f);
        part(frame, 0.05f, 1.75f, 0.87f, 0.9f, 0.08f, 0.08f);
        part(Material.GRAY_CONCRETE.createBlockData(), 0.0f, 2.7f, 0.0f, 1.0f, 0.12f, 1.0f);
        lamp = part(furnace(false), 0.25f, 2.82f, 0.25f, 0.5f, 0.45f, 0.5f);
        // Barra y collar: giran sobre el eje vertical del centro.
        BlockData steel = Material.IRON_BLOCK.createBlockData();
        moving(steel, -0.09f, 1.0f, -0.09f, 0.18f, 1.7f, 0.18f);
        moving(Material.GRAY_CONCRETE.createBlockData(), -0.2f, 1.4f, -0.2f, 0.4f, 0.12f, 0.4f);
        moving(Material.GRAY_CONCRETE.createBlockData(), -0.2f, 2.2f, -0.2f, 0.4f, 0.12f, 0.4f);
        return 3.7f;
    }

    /** Bomba de balancin ("caballito"): poste en A, balancin que sube y baja, cabeza de caballo y contrapeso. */
    private float buildPumpjack() {
        BlockData frame = Material.GRAY_CONCRETE.createBlockData();
        part(Material.BLACK_CONCRETE.createBlockData(), 0.1f, 1.0f, -0.2f, 0.8f, 0.12f, 1.4f);
        part(frame, 0.2f, 1.12f, 0.42f, 0.12f, 1.25f, 0.16f);
        part(frame, 0.68f, 1.12f, 0.42f, 0.12f, 1.25f, 0.16f);
        part(frame, 0.2f, 2.3f, 0.42f, 0.6f, 0.1f, 0.16f);
        // Motor y manivela atras.
        lamp = part(furnace(false), 0.3f, 1.12f, 0.95f, 0.4f, 0.4f, 0.35f);
        // Cabezal del pozo adelante.
        part(Material.IRON_BLOCK.createBlockData(), 0.4f, 1.12f, -0.15f, 0.2f, 0.35f, 0.2f);
        // Piezas del balancin, relativas al pivote (0.5, 2.45, 0.5).
        moving(Material.ORANGE_CONCRETE.createBlockData(), -0.08f, -0.05f, -0.75f, 0.16f, 0.16f, 1.6f);
        moving(Material.BLACK_CONCRETE.createBlockData(), -0.14f, -0.45f, -0.95f, 0.28f, 0.6f, 0.2f);
        moving(Material.IRON_BLOCK.createBlockData(), -0.18f, -0.4f, 0.75f, 0.36f, 0.4f, 0.28f);
        return 3.3f;
    }

    /** Horno de coque "de colmena": domo de ladrillo con chimenea y una boca que brilla al trabajar. */
    private float buildCokeOven() {
        BlockData brick = Material.BRICKS.createBlockData();
        part(brick, 0.1f, 1.0f, 0.1f, 0.8f, 0.35f, 0.8f);
        part(brick, 0.25f, 1.35f, 0.25f, 0.5f, 0.25f, 0.5f);
        part(Material.MUD_BRICKS.createBlockData(), 0.6f, 1.35f, 0.6f, 0.25f, 1.1f, 0.25f);
        lamp = part(Material.BLACK_CONCRETE.createBlockData(), 0.25f, 0.1f, -0.02f, 0.5f, 0.45f, 0.04f);
        return 2.8f;
    }

    /** Refineria: columna de destilacion con anillos, tanque y antorcha de gas. */
    private float buildRefinery() {
        part(Material.LIGHT_GRAY_CONCRETE.createBlockData(), 0.2f, 1.0f, 0.2f, 0.6f, 2.6f, 0.6f);
        BlockData ring = Material.IRON_BLOCK.createBlockData();
        for (float y : new float[]{1.6f, 2.3f, 3.0f}) part(ring, 0.15f, y, 0.15f, 0.7f, 0.08f, 0.7f);
        part(Material.GRAY_CONCRETE.createBlockData(), 0.32f, 3.6f, 0.32f, 0.36f, 0.25f, 0.36f);
        part(Material.WHITE_CONCRETE.createBlockData(), -0.25f, 1.0f, -0.25f, 0.4f, 0.55f, 0.4f);
        part(Material.RED_CONCRETE.createBlockData(), -0.25f, 1.25f, -0.25f, 0.4f, 0.06f, 0.4f);
        part(Material.GRAY_CONCRETE.createBlockData(), 0.9f, 1.0f, 0.9f, 0.1f, 3.4f, 0.1f);
        lamp = part(Material.BLACK_CONCRETE.createBlockData(), 0.88f, 4.4f, 0.88f, 0.14f, 0.12f, 0.14f);
        return 4.5f;
    }

    // ---------------------------------------------------------------- uso

    public void setStatus(Component text, boolean running) {
        if (label != null && label.isValid()) label.text(text);
        if (this.running == running) return;
        this.running = running;
        if (lamp == null || !lamp.isValid()) return;
        if (model.equals("perforadora") || model.equals("bomba")) {
            lamp.setBlock(furnace(running));
        } else {
            // Boca del horno o llama de la antorcha de la refineria.
            lamp.setBlock((running ? Material.SHROOMLIGHT : Material.BLACK_CONCRETE).createBlockData());
            lamp.setBrightness(running ? new Display.Brightness(15, 15) : null);
        }
    }

    /** Llamarlo una vez por tick de la maquina; {@code step} = ticks que pasaron. */
    public void animate(int step, boolean particles, boolean sounds) {
        if (!running) return;
        int before = ticks;
        ticks += step;
        World world = base.getWorld();
        switch (model) {
            case "bomba" -> {
                if (before / ROCK_TICKS != ticks / ROCK_TICKS) {
                    angle = angle > 0 ? -ROCK_DEG : ROCK_DEG;
                    for (int i = 0; i < moving.size(); i++) {
                        animateTo(moving.get(i), rocked(movingBoxes.get(i), angle), ROCK_TICKS);
                    }
                    if (sounds) world.playSound(at(0.5f, 1.5f, 0.5f), Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.25f, 0.6f);
                }
            }
            case "perforadora" -> {
                if (before / SPIN_TICKS != ticks / SPIN_TICKS) {
                    angle = (angle + 90f) % 360f;
                    for (int i = 0; i < moving.size(); i++) {
                        animateTo(moving.get(i), spun(movingBoxes.get(i), angle), SPIN_TICKS);
                    }
                    if (particles) world.spawnParticle(Particle.BLOCK, at(0.5f, 1.05f, 0.5f), 4, 0.15, 0.02, 0.15, 0,
                            Material.COAL_ORE.createBlockData());
                    if (sounds) world.playSound(at(0.5f, 1.5f, 0.5f), Sound.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.3f, 0.6f);
                }
                if (particles && ticks % 20 < step) world.spawnParticle(Particle.LARGE_SMOKE, at(0.5f, 3.3f, 0.5f), 1, 0.05, 0.05, 0.05, 0.01);
            }
            case "horno" -> {
                if (particles && ticks % 20 < step) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at(0.725f, 2.5f, 0.725f), 1, 0.02, 0.1, 0.02, 0.01);
                    world.spawnParticle(Particle.FLAME, at(0.5f, 0.3f, -0.05f), 2, 0.15, 0.1, 0.02, 0.01);
                }
                if (sounds && ticks % 60 < step) world.playSound(at(0.5f, 0.5f, 0.5f), Sound.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.6f, 0.8f);
            }
            default -> {
                if (particles && ticks % 10 < step) {
                    world.spawnParticle(Particle.FLAME, at(0.95f, 4.6f, 0.95f), 3, 0.04, 0.08, 0.04, 0.01);
                    world.spawnParticle(Particle.SMOKE, at(0.5f, 3.9f, 0.5f), 1, 0.05, 0.05, 0.05, 0.01);
                }
                if (sounds && ticks % 80 < step) world.playSound(at(0.5f, 2f, 0.5f), Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.5f, 0.7f);
            }
        }
    }

    public void remove() {
        for (Display part : parts) {
            if (part.isValid()) part.remove();
        }
        parts.clear();
    }

    // ---------------------------------------------------------------- helpers

    private BlockDisplay part(BlockData data, float x, float y, float z, float sx, float sy, float sz) {
        BlockDisplay d = base.getWorld().spawn(base, BlockDisplay.class, e -> {
            setup(plugin, e);
            e.setBlock(data);
            e.setTransformation(oriented(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(sx, sy, sz)));
        });
        parts.add(d);
        return d;
    }

    /** Pieza animada: la caja es relativa al eje/pivote del modelo (ver rocked/spun). */
    private void moving(BlockData data, float x, float y, float z, float sx, float sy, float sz) {
        float[] box = {x, y, z, sx, sy, sz};
        Transformation start = model.equals("bomba") ? rocked(box, 0f) : spun(box, 0f);
        BlockDisplay d = base.getWorld().spawn(base, BlockDisplay.class, e -> {
            setup(plugin, e);
            e.setBlock(data);
            e.setTransformation(start);
        });
        parts.add(d);
        moving.add(d);
        movingBoxes.add(box);
    }

    /** Caja del balancin girada {@code deg} grados sobre el eje X que pasa por el pivote. */
    private Transformation rocked(float[] box, float deg) {
        Quaternionf rot = new Quaternionf().rotateX((float) Math.toRadians(deg));
        Vector3f corner = rot.transform(new Vector3f(box[0], box[1], box[2]));
        Vector3f translation = new Vector3f(0.5f, 2.45f, 0.5f).add(corner);
        return oriented(translation, rot, new Vector3f(box[3], box[4], box[5]));
    }

    /** Caja de la barra girada {@code deg} grados sobre el eje vertical del centro del bloque. */
    private Transformation spun(float[] box, float deg) {
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(deg));
        Vector3f corner = rot.transform(new Vector3f(box[0], 0f, box[2]));
        Vector3f translation = new Vector3f(0.5f + corner.x, box[1], 0.5f + corner.z);
        return oriented(translation, rot, new Vector3f(box[3], box[4], box[5]));
    }

    /**
     * Aplica la orientacion de la maquina: gira la pieza sobre el eje vertical
     * del centro del bloque. El display hace p -> T + R*(S*p).
     */
    private Transformation oriented(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        Vector3f center = new Vector3f(0.5f, 0f, 0.5f);
        Vector3f t = facing.transform(new Vector3f(translation).sub(center)).add(center);
        Quaternionf r = new Quaternionf(facing).mul(rotation);
        return new Transformation(t, r, scale, new Quaternionf());
    }

    private static void animateTo(BlockDisplay d, Transformation t, int ticks) {
        if (!d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(t);
    }

    private Location at(float x, float y, float z) {
        Vector3f center = new Vector3f(0.5f, 0f, 0.5f);
        Vector3f v = facing.transform(new Vector3f(x, y, z).sub(center)).add(center);
        return base.clone().add(v.x, v.y, v.z);
    }

    private static void setup(Plugin plugin, Display display) {
        display.setPersistent(false);
        display.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
    }

    private static BlockData furnace(boolean lit) {
        BlockData data = Material.BLAST_FURNACE.createBlockData();
        if (data instanceof Furnace f) {
            f.setFacing(BlockFace.SOUTH);
            f.setLit(lit);
        }
        return data;
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "modelo_maquina");
    }
}
