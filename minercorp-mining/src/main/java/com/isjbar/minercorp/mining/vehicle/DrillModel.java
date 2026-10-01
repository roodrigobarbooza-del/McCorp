package com.isjbar.minercorp.mining.vehicle;

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
import org.bukkit.configuration.file.FileConfiguration;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Carroceria del taladro-vehiculo: solo dibuja, no mueve ni mina.
 *
 * Es un modelo de varias piezas armado con BlockDisplay (chasis, orugas,
 * motor, parabrisas, faros, brida y una punta conica de 3 piezas que gira)
 * mas un TextDisplay con el nombre. Todo con bloques vanilla, sin
 * resourcepack.
 *
 * Sistema local del modelo: el origen es la posicion del vehiculo a ras del
 * piso, +Z es el frente (un display con yaw 0 mira hacia +Z, igual que
 * cualquier entidad), +Y arriba. Todas las piezas se teletransportan juntas
 * a la posicion y yaw del vehiculo en {@link #moveTo(Location)}, con
 * teleportDuration para que el cliente interpole y no se vea a saltos.
 *
 * Uso esperado desde el vehiculo:
 * <pre>
 *   DrillModel model = DrillModel.spawn(plugin, loc, tier, nombre);
 *   // guardar model.rootId() en el PDC del vehiculo
 *   // cada tick, despues de mover el vehiculo:
 *   model.moveTo(vehiculo.getLocation());
 *   model.setDrilling(estaPerforando);
 *   // por cada bloque que rompe:
 *   model.onBlockBroken(block);       // ANTES de ponerlo en AIR
 *   // al desarmar:
 *   model.remove();
 *   // tras un reinicio:
 *   DrillModel.find(plugin, rootId)
 * </pre>
 */
public final class DrillModel {

    private static final int TELEPORT_TICKS = 2;
    private static final int SPIN_TICKS = 2;
    private static final float SPIN_STEP_DEG = 50f;

    /** Eje de la punta conica (x, y) en coordenadas locales. */
    private static final float BIT_AXIS_X = 0f;
    private static final float BIT_AXIS_Y = 0.65f;
    /** Piezas de la punta: lado, inicio en z, largo en z. De la mas ancha a la mas fina. */
    private static final float[][] BIT_PIECES = {
            {0.70f, 1.15f, 0.40f},
            {0.48f, 1.50f, 0.38f},
            {0.26f, 1.82f, 0.36f},
    };
    private static final float BIT_TIP_Z = 2.2f;

    private static final String ROLE_CHASIS = "chasis";
    private static final String ROLE_MOTOR = "motor";
    private static final String ROLE_PUNTA = "punta-";
    private static final String ROLE_NOMBRE = "nombre";

    private final Plugin plugin;
    private final BlockDisplay root;
    private final List<Display> parts;
    private final BlockDisplay[] bits;
    private final BlockDisplay motor;
    private final TextDisplay label;

    private Location last;
    private boolean drilling;
    private float spinDeg;
    private int ticks;

    private DrillModel(Plugin plugin, BlockDisplay root, List<Display> parts, BlockDisplay[] bits,
                       BlockDisplay motor, TextDisplay label) {
        this.plugin = plugin;
        this.root = root;
        this.parts = parts;
        this.bits = bits;
        this.motor = motor;
        this.label = label;
        this.last = root.getLocation();
    }

    // ------------------------------------------------------------------ armado

    /** Arma el modelo del tier dado en la posicion (y yaw) indicada. */
    public static DrillModel spawn(Plugin plugin, Location location, int tier, Component nombre) {
        Palette palette = Palette.load(plugin.getConfig(), tier);
        Location base = location.clone();
        base.setPitch(0);
        World world = base.getWorld();

        List<Display> parts = new ArrayList<>();

        BlockDisplay root = block(plugin, world, base, ROLE_CHASIS, palette.cuerpo().createBlockData(),
                box(-0.70f, 0.25f, -0.95f, 1.40f, 0.40f, 1.90f));
        parts.add(root);

        // Orugas a los costados, un poco mas largas que el chasis.
        BlockData oruga = palette.oruga().createBlockData();
        parts.add(block(plugin, world, base, "oruga-izq", oruga, box(0.62f, 0f, -1.05f, 0.30f, 0.45f, 2.10f)));
        parts.add(block(plugin, world, base, "oruga-der", oruga, box(-0.92f, 0f, -1.05f, 0.30f, 0.45f, 2.10f)));

        // Motor atras, detras del asiento. Alto horno con la boca hacia atras.
        BlockDisplay motor = block(plugin, world, base, ROLE_MOTOR, motorData(false),
                box(-0.45f, 0.65f, -1.00f, 0.90f, 0.60f, 0.50f));
        parts.add(motor);

        // Parabrisas delante del jugador.
        parts.add(block(plugin, world, base, "cabina", palette.vidrio().createBlockData(),
                box(-0.50f, 0.65f, 0.45f, 1.00f, 0.50f, 0.06f)));

        // Faros: brillan aunque el tunel este oscuro.
        BlockData faro = Material.SEA_LANTERN.createBlockData();
        parts.add(glow(block(plugin, world, base, "faro-izq", faro, box(0.40f, 0.45f, 0.95f, 0.20f, 0.18f, 0.10f))));
        parts.add(glow(block(plugin, world, base, "faro-der", faro, box(-0.60f, 0.45f, 0.95f, 0.20f, 0.18f, 0.10f))));

        // Brida donde se monta la punta.
        parts.add(block(plugin, world, base, "brida", palette.detalle().createBlockData(),
                box(-0.45f, 0.20f, 0.85f, 0.90f, 0.90f, 0.30f)));

        // Punta conica: tres prismas cuadrados girados 45 grados sobre el eje Z.
        BlockDisplay[] bits = new BlockDisplay[BIT_PIECES.length];
        BlockData punta = palette.punta().createBlockData();
        for (int i = 0; i < BIT_PIECES.length; i++) {
            bits[i] = block(plugin, world, base, ROLE_PUNTA + i, punta, bitTransform(i, 0f));
            parts.add(bits[i]);
        }

        TextDisplay label = world.spawn(base, TextDisplay.class, d -> {
            setup(plugin, d, ROLE_NOMBRE);
            d.text(nombre);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 2.35f, 0f), new Quaternionf(),
                    new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
        });
        parts.add(label);

        // El chasis guarda los UUID de todas las piezas para poder recuperarlas tras un reinicio.
        StringBuilder ids = new StringBuilder();
        for (Display part : parts) {
            if (part == root) continue;
            if (!ids.isEmpty()) ids.append(',');
            ids.append(part.getUniqueId());
        }
        root.getPersistentDataContainer().set(partsKey(plugin), PersistentDataType.STRING, ids.toString());

        return new DrillModel(plugin, root, parts, bits, motor, label);
    }

    /** Recupera un modelo ya armado a partir del UUID de su chasis ({@link #rootId()}). */
    public static Optional<DrillModel> find(Plugin plugin, UUID rootId) {
        if (!(plugin.getServer().getEntity(rootId) instanceof BlockDisplay root)) return Optional.empty();
        String ids = root.getPersistentDataContainer().get(partsKey(plugin), PersistentDataType.STRING);
        if (ids == null) return Optional.empty();

        List<Display> parts = new ArrayList<>();
        parts.add(root);
        BlockDisplay[] bits = new BlockDisplay[BIT_PIECES.length];
        BlockDisplay motor = null;
        TextDisplay label = null;
        for (String id : ids.split(",")) {
            if (id.isBlank()) continue;
            if (!(plugin.getServer().getEntity(UUID.fromString(id)) instanceof Display part)) continue;
            parts.add(part);
            String role = part.getPersistentDataContainer().get(partKey(plugin), PersistentDataType.STRING);
            if (role == null) continue;
            if (role.equals(ROLE_MOTOR) && part instanceof BlockDisplay b) motor = b;
            else if (role.equals(ROLE_NOMBRE) && part instanceof TextDisplay t) label = t;
            else if (role.startsWith(ROLE_PUNTA) && part instanceof BlockDisplay b) {
                int i = Integer.parseInt(role.substring(ROLE_PUNTA.length()));
                if (i >= 0 && i < bits.length) bits[i] = b;
            }
        }
        return Optional.of(new DrillModel(plugin, root, parts, bits, motor, label));
    }

    /** true si la entidad es una pieza de algun modelo de taladro (sirve para limpiar huerfanos). */
    public static boolean isModelPart(Plugin plugin, Entity entity) {
        return entity instanceof Display
                && entity.getPersistentDataContainer().has(partKey(plugin), PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- en uso

    public UUID rootId() {
        return root.getUniqueId();
    }

    public boolean isValid() {
        return root.isValid();
    }

    public void setName(Component nombre) {
        if (label != null && label.isValid()) label.text(nombre);
    }

    /**
     * Lleva todas las piezas a la posicion y yaw del vehiculo. Llamarlo una vez
     * por tick, despues de mover el vehiculo: tambien avanza la animacion.
     */
    public void moveTo(Location location) {
        Location target = location.clone();
        target.setPitch(0);
        last = target;
        for (Display part : parts) {
            if (part.isValid()) part.teleport(target);
        }
        animate();
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

    public void remove() {
        for (Display part : parts) {
            if (part.isValid()) part.remove();
        }
    }

    // ------------------------------------------------------------- animacion

    private void animate() {
        ticks++;
        if (!drilling) return;

        if (ticks % SPIN_TICKS == 0) {
            spinDeg = (spinDeg + SPIN_STEP_DEG) % 360f;
            for (int i = 0; i < bits.length; i++) {
                BlockDisplay bit = bits[i];
                if (bit == null || !bit.isValid()) continue;
                // Cada pieza gira un poco desfasada para que se note el giro.
                bit.setInterpolationDelay(0);
                bit.setInterpolationDuration(SPIN_TICKS);
                bit.setTransformation(bitTransform(i, spinDeg + i * 15f));
            }
            World world = last.getWorld();
            world.spawnParticle(Particle.ELECTRIC_SPARK, local(last, BIT_AXIS_X, BIT_AXIS_Y, BIT_TIP_Z),
                    4, 0.15, 0.15, 0.15, 0.05);
        }
        if (ticks % 4 == 0) {
            last.getWorld().spawnParticle(Particle.LARGE_SMOKE, local(last, 0f, 1.35f, -0.8f),
                    1, 0.05, 0.05, 0.05, 0.01);
        }
        if (ticks % 10 == 0) {
            last.getWorld().playSound(local(last, 0f, BIT_AXIS_Y, 1.5f), Sound.BLOCK_GRINDSTONE_USE,
                    SoundCategory.BLOCKS, 0.5f, 0.7f);
        }
    }

    // ----------------------------------------------------------------- helpers

    private static BlockDisplay block(Plugin plugin, World world, Location base, String role,
                                      BlockData data, Transformation transformation) {
        return world.spawn(base, BlockDisplay.class, d -> {
            setup(plugin, d, role);
            d.setBlock(data);
            d.setTransformation(transformation);
        });
    }

    private static void setup(Plugin plugin, Display display, String role) {
        display.setPersistent(true);
        display.setTeleportDuration(TELEPORT_TICKS);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(SPIN_TICKS);
        display.getPersistentDataContainer().set(partKey(plugin), PersistentDataType.STRING, role);
    }

    private static Display glow(Display display) {
        display.setBrightness(new Display.Brightness(15, 15));
        return display;
    }

    /** Caja alineada a los ejes: esquina minima (x, y, z) y tamano. */
    private static Transformation box(float x, float y, float z, float sx, float sy, float sz) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /** Pieza i de la punta, girada sobre su eje (paralelo a Z) 45 + spin grados. */
    private static Transformation bitTransform(int i, float spinDeg) {
        float side = BIT_PIECES[i][0], z = BIT_PIECES[i][1], depth = BIT_PIECES[i][2];
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.toRadians(45f + spinDeg));
        // El display aplica primero la escala y despues la rotacion; corremos la pieza
        // para que el centro de su cara quede sobre el eje de giro.
        Vector3f halfFace = rotation.transform(new Vector3f(side / 2f, side / 2f, 0f));
        Vector3f translation = new Vector3f(BIT_AXIS_X, BIT_AXIS_Y, z).sub(halfFace);
        return new Transformation(translation, rotation, new Vector3f(side, side, depth), new Quaternionf());
    }

    /** Pasa un punto del sistema local del modelo a coordenadas del mundo. */
    private static Location local(Location base, float x, float y, float z) {
        double yaw = Math.toRadians(base.getYaw());
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        // Frente (+Z local) = (-sin, 0, cos); izquierda (+X local) = (cos, 0, sin).
        return base.clone().add(x * cos - z * sin, y, x * sin + z * cos);
    }

    private static BlockData motorData(boolean lit) {
        BlockData data = Material.BLAST_FURNACE.createBlockData();
        if (data instanceof Furnace furnace) {
            furnace.setFacing(BlockFace.NORTH); // boca hacia -Z local, o sea hacia atras
            furnace.setLit(lit);
        }
        return data;
    }

    private static NamespacedKey partKey(Plugin plugin) {
        return new NamespacedKey(plugin, "drill_model_part");
    }

    private static NamespacedKey partsKey(Plugin plugin) {
        return new NamespacedKey(plugin, "drill_model_parts");
    }

    /** Materiales del modelo por tier (taladros.tier-N.modelo en config.yml), con defaults por tier. */
    private record Palette(Material cuerpo, Material punta, Material detalle, Material oruga, Material vidrio) {

        static Palette load(FileConfiguration config, int tier) {
            Palette def = switch (tier) {
                case 1 -> new Palette(Material.YELLOW_CONCRETE, Material.IRON_BLOCK, Material.GRAY_CONCRETE,
                        Material.BLACK_CONCRETE, Material.LIGHT_GRAY_STAINED_GLASS);
                case 2 -> new Palette(Material.WAXED_CUT_COPPER, Material.GOLD_BLOCK, Material.WAXED_COPPER_BLOCK,
                        Material.BLACK_CONCRETE, Material.LIGHT_BLUE_STAINED_GLASS);
                default -> new Palette(Material.NETHERITE_BLOCK, Material.DIAMOND_BLOCK, Material.POLISHED_BLACKSTONE,
                        Material.BLACKSTONE, Material.TINTED_GLASS);
            };
            String path = "taladros.tier-" + tier + ".modelo.";
            return new Palette(
                    material(config, path + "cuerpo", def.cuerpo()),
                    material(config, path + "punta", def.punta()),
                    material(config, path + "detalle", def.detalle()),
                    material(config, path + "oruga", def.oruga()),
                    material(config, path + "vidrio", def.vidrio()));
        }

        private static Material material(FileConfiguration config, String path, Material fallback) {
            String name = config.getString(path);
            if (name == null) return fallback;
            Material material = Material.matchMaterial(name);
            return material != null && material.isBlock() ? material : fallback;
        }
    }
}
