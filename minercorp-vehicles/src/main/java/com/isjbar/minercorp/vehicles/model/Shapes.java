package com.isjbar.minercorp.vehicles.model;

import org.bukkit.Material;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Las formas de los vehiculos, armadas con cajas de bloques vanilla. Las
 * medidas estan en bloques, en el sistema local de {@link Shape}: +X es la
 * izquierda del vehiculo, +Z el frente.
 *
 * Inspiradas en las referencias de Isidro: el camion es de cabina adelantada
 * con caja de lona (tipo IFA), la camioneta es una pickup chica de dos tonos
 * con baca en el techo, y el taladro es el de siempre.
 */
public final class Shapes {

    private Shapes() {
    }

    public static Shape get(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "camion" -> camion();
            case "taladro" -> taladro();
            default -> camioneta();
        };
    }

    // ------------------------------------------------------------- camion

    private static Shape camion() {
        Builder b = new Builder();

        // Chasis y paragolpes
        b.box("chasis", "chasis", Material.GRAY_CONCRETE, -0.8f, 0.5f, -3.0f, 1.6f, 0.3f, 5.9f);
        b.box("paragolpes", "chasis", Material.GRAY_CONCRETE, -1.25f, 0.6f, 2.9f, 2.5f, 0.4f, 0.2f);
        b.box("paragolpes-tras", "chasis", Material.GRAY_CONCRETE, -1.2f, 0.7f, -3.1f, 2.4f, 0.3f, 0.15f);
        b.pair("baranda", "chasis", Material.GRAY_CONCRETE, 1.1f, 0.7f, -1.2f, 0.05f, 0.08f, 2.4f);

        // Cabina adelantada: parte baja pintada, parte alta vidriada, techo y visera
        b.box("cabina", "cabina", Material.LIGHT_BLUE_TERRACOTTA, -1.2f, 1.05f, 1.3f, 2.4f, 0.95f, 1.7f);
        b.box("cabina-vidrio", "vidrio", Material.LIGHT_GRAY_STAINED_GLASS, -1.15f, 2.0f, 1.4f, 2.3f, 0.75f, 1.55f);
        b.box("cabina-trasera", "cabina", Material.LIGHT_BLUE_TERRACOTTA, -1.2f, 2.0f, 1.3f, 2.4f, 0.75f, 0.12f);
        b.pair("parante", "cabina", Material.LIGHT_BLUE_TERRACOTTA, 1.05f, 2.0f, 2.85f, 0.15f, 0.75f, 0.15f);
        b.pair("parante-medio", "cabina", Material.LIGHT_BLUE_TERRACOTTA, 1.1f, 2.0f, 2.05f, 0.1f, 0.75f, 0.1f);
        b.box("techo", "cabina", Material.LIGHT_BLUE_TERRACOTTA, -1.2f, 2.75f, 1.3f, 2.4f, 0.18f, 1.7f);
        b.box("visera", "detalle", Material.LIGHT_GRAY_CONCRETE, -1.1f, 2.72f, 2.95f, 2.2f, 0.06f, 0.12f);

        // Frente: parrilla con rejillas, faros redondos sobre el paragolpes y giros
        b.box("parrilla", "detalle", Material.LIGHT_GRAY_CONCRETE, -0.5f, 1.15f, 2.99f, 1.0f, 0.7f, 0.04f);
        for (int i = 0; i < 4; i++) {
            b.box("rejilla-" + i, null, Material.BLACK_CONCRETE, -0.45f, 1.22f + i * 0.17f, 3.02f, 0.9f, 0.06f, 0.03f);
        }
        b.glowPair("faro", Material.SEA_LANTERN, 0.65f, 0.68f, 3.1f, 0.3f, 0.25f, 0.04f);
        b.glowPair("giro", Material.ORANGE_CONCRETE, 0.75f, 1.15f, 3.0f, 0.3f, 0.12f, 0.03f);

        // Espejos, estribos y escape vertical detras de la cabina
        b.pair("espejo-brazo", null, Material.BLACK_CONCRETE, 1.2f, 2.2f, 2.7f, 0.25f, 0.05f, 0.05f);
        b.pair("espejo", null, Material.BLACK_CONCRETE, 1.42f, 1.95f, 2.65f, 0.05f, 0.45f, 0.15f);
        b.pair("estribo", "detalle", Material.LIGHT_GRAY_CONCRETE, 1.1f, 0.85f, 1.6f, 0.15f, 0.08f, 0.6f);
        b.box("escape", "chasis", Material.GRAY_CONCRETE, 1.0f, 1.05f, 1.12f, 0.12f, 2.0f, 0.12f);
        b.box("tanque", "detalle", Material.LIGHT_GRAY_CONCRETE, -1.15f, 0.55f, -0.7f, 0.35f, 0.4f, 1.0f);

        // Caja con lona y franja
        b.box("caja", "caja", Material.STRIPPED_SPRUCE_WOOD, -1.2f, 1.05f, -3.0f, 2.4f, 0.2f, 4.2f);
        b.box("lona", "lona", Material.RED_WOOL, -1.2f, 1.25f, -2.98f, 2.4f, 1.75f, 4.1f);
        b.box("franja", "franja", Material.WHITE_CONCRETE, -1.21f, 1.25f, -2.99f, 2.42f, 0.18f, 4.12f);
        b.glowPair("luz-trasera", Material.REDSTONE_BLOCK, 0.75f, 0.75f, -3.13f, 0.3f, 0.18f, 0.04f);

        // Ruedas: una delantera que dobla y una trasera por lado
        b.wheelPair(1.0f, 0.5f, 1.9f, 0.5f, 0.35f, true);
        b.wheelPair(1.0f, 0.5f, -1.8f, 0.5f, 0.35f, false);

        b.seat(0.55f, 1.45f, 2.2f);
        b.seat(-0.55f, 1.45f, 2.2f);
        return b.build("camion", 1.2, 3.0, 3, 2.7, 3.4f, new Vector3f(1.06f, 3.1f, 1.18f), 2.4f, 3.0f);
    }

    // ---------------------------------------------------------- camioneta

    private static Shape camioneta() {
        Builder b = new Builder();

        // Carroceria de dos tonos: banda baja clara, cuerpo pintado
        b.box("bajo", "bajo", Material.LIGHT_GRAY_CONCRETE, -1.0f, 0.35f, -2.3f, 2.0f, 0.25f, 4.6f);
        b.box("cuerpo", "carroceria", Material.RED_CONCRETE, -1.0f, 0.6f, -0.45f, 2.0f, 0.55f, 2.75f);
        b.box("capo", "carroceria", Material.RED_CONCRETE, -0.9f, 1.15f, 1.05f, 1.8f, 0.06f, 1.2f);
        b.box("franja", "detalle", Material.IRON_BLOCK, -1.01f, 0.8f, -2.29f, 2.02f, 0.05f, 4.58f);

        // Cabina
        b.box("cabina-vidrio", "vidrio", Material.LIGHT_GRAY_STAINED_GLASS, -0.95f, 1.15f, -0.4f, 1.9f, 0.62f, 1.4f);
        b.box("cabina-trasera", "carroceria", Material.RED_CONCRETE, -0.98f, 1.15f, -0.45f, 1.96f, 0.62f, 0.1f);
        b.pair("parante", "carroceria", Material.RED_CONCRETE, 0.82f, 1.15f, 0.9f, 0.15f, 0.62f, 0.12f);
        b.box("techo", "techo", Material.WHITE_CONCRETE, -1.0f, 1.77f, -0.45f, 2.0f, 0.12f, 1.45f);

        // Baca en el techo
        b.pair("baca", "detalle", Material.IRON_BLOCK, 0.75f, 1.89f, -0.35f, 0.06f, 0.06f, 1.25f);
        b.box("baca-travesano-1", "detalle", Material.IRON_BLOCK, -0.81f, 1.9f, -0.3f, 1.62f, 0.05f, 0.06f);
        b.box("baca-travesano-2", "detalle", Material.IRON_BLOCK, -0.81f, 1.9f, 0.75f, 1.62f, 0.05f, 0.06f);

        // Caja de carga abierta
        b.box("caja-base", "carroceria", Material.RED_CONCRETE, -1.0f, 0.6f, -2.3f, 2.0f, 0.4f, 1.85f);
        b.box("caja-piso", "caja", Material.STRIPPED_SPRUCE_WOOD, -0.85f, 1.0f, -2.15f, 1.7f, 0.02f, 1.68f);
        b.pair("caja-lado", "carroceria", Material.RED_CONCRETE, 0.85f, 1.0f, -2.3f, 0.15f, 0.45f, 1.85f);
        b.box("porton", "carroceria", Material.RED_CONCRETE, -0.85f, 1.0f, -2.3f, 1.7f, 0.45f, 0.15f);

        // Frente y cola
        b.box("parrilla", "detalle", Material.IRON_BLOCK, -0.5f, 0.65f, 2.29f, 1.0f, 0.4f, 0.04f);
        for (int i = 0; i < 3; i++) {
            b.box("rejilla-" + i, null, Material.BLACK_CONCRETE, -0.45f, 0.72f + i * 0.13f, 2.32f, 0.9f, 0.05f, 0.03f);
        }
        b.glowPair("faro", Material.SEA_LANTERN, 0.58f, 0.72f, 2.3f, 0.3f, 0.26f, 0.05f);
        b.box("paragolpes", "detalle", Material.IRON_BLOCK, -1.05f, 0.33f, 2.25f, 2.1f, 0.2f, 0.15f);
        b.box("paragolpes-tras", "detalle", Material.IRON_BLOCK, -1.05f, 0.33f, -2.4f, 2.1f, 0.2f, 0.15f);
        b.glowPair("luz-trasera", Material.REDSTONE_BLOCK, 0.72f, 0.75f, -2.34f, 0.22f, 0.2f, 0.05f);
        b.pair("espejo-brazo", null, Material.BLACK_CONCRETE, 1.0f, 1.25f, 0.85f, 0.18f, 0.05f, 0.05f);
        b.pair("espejo", null, Material.BLACK_CONCRETE, 1.15f, 1.15f, 0.8f, 0.05f, 0.25f, 0.12f);
        b.box("escape", null, Material.GRAY_CONCRETE, -0.75f, 0.3f, -2.45f, 0.1f, 0.1f, 0.25f);

        b.wheelPair(0.85f, 0.4f, 1.45f, 0.4f, 0.3f, true);
        b.wheelPair(0.85f, 0.4f, -1.45f, 0.4f, 0.3f, false);

        b.seat(0.42f, 0.7f, 0.3f);
        b.seat(-0.42f, 0.7f, 0.3f);
        return b.build("camioneta", 1.0, 2.3, 2, 2.0, 2.5f, new Vector3f(-0.7f, 0.35f, -2.55f), 2.0f, 2.0f);
    }

    // ------------------------------------------------------------ taladro

    /** Eje de la punta conica (x, y) del taladro. */
    private static final Vector3f BIT_AXIS = new Vector3f(0f, 0.65f, 0f);
    /** Piezas de la punta: lado, inicio en z, largo en z. De la mas ancha a la mas fina. */
    static final float[][] BIT_PIECES = {
            {0.70f, 1.15f, 0.40f},
            {0.48f, 1.50f, 0.38f},
            {0.26f, 1.82f, 0.36f},
    };

    private static Shape taladro() {
        Builder b = new Builder();
        b.box("chasis", "cuerpo", Material.YELLOW_CONCRETE, -0.70f, 0.25f, -0.95f, 1.40f, 0.40f, 1.90f);
        // Orugas a los costados, un poco mas largas que el chasis.
        b.pair("oruga", "oruga", Material.BLACK_CONCRETE, 0.62f, 0f, -1.05f, 0.30f, 0.45f, 2.10f);
        // Motor atras: alto horno con la boca hacia atras, se enciende al perforar.
        b.parts.add(new PartDef("motor", null, Material.BLAST_FURNACE,
                box(-0.45f, 0.65f, -1.00f, 0.90f, 0.60f, 0.50f), false, PartDef.Kind.MOTOR, 0));
        b.box("cabina", "vidrio", Material.LIGHT_GRAY_STAINED_GLASS, -0.50f, 0.65f, 0.45f, 1.00f, 0.50f, 0.06f);
        b.glowPair("faro", Material.SEA_LANTERN, 0.40f, 0.45f, 0.95f, 0.20f, 0.18f, 0.10f);
        b.box("brida", "detalle", Material.GRAY_CONCRETE, -0.45f, 0.20f, 0.85f, 0.90f, 0.90f, 0.30f);
        for (int i = 0; i < BIT_PIECES.length; i++) {
            b.parts.add(new PartDef("punta-" + i, "punta", Material.IRON_BLOCK, bitTransform(i, 0f), false,
                    PartDef.Kind.BIT, i));
        }
        b.seat(0f, 0.6f, 0f);
        Shape base = b.build("taladro", 0.45, 0.9, 2, 0.6, 2.35f, new Vector3f(0f, 1.35f, -0.8f), 1.8f, 1.3f);
        return new Shape(base.id(), base.parts(), base.wheels(), base.seats(), base.halfWidth(), base.halfLength(),
                base.height(), base.contact(), base.labelHeight(), base.exhaust(), base.interactionWidth(),
                base.interactionHeight(), BIT_AXIS, 2.2f);
    }

    // ------------------------------------------------------------ helpers

    /** Caja alineada a los ejes: esquina minima (x, y, z) y tamano. */
    static Transformation box(float x, float y, float z, float sx, float sy, float sz) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf());
    }

    /** Caja de tamano {@code size} centrada en {@code center} y girada con {@code rotation} sobre su centro. */
    static Transformation centered(Vector3f center, Vector3f size, Quaternionf rotation) {
        // El display aplica primero la escala y despues la rotacion: corremos la
        // pieza para que su centro quede en su lugar.
        Vector3f half = rotation.transform(new Vector3f(size).mul(0.5f));
        return new Transformation(new Vector3f(center).sub(half), rotation, size, new Quaternionf());
    }

    /** Pieza i de la punta del taladro, girada sobre su eje (paralelo a Z) 45 + spin grados. */
    static Transformation bitTransform(int i, float spinDeg) {
        float side = BIT_PIECES[i][0], z = BIT_PIECES[i][1], depth = BIT_PIECES[i][2];
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.toRadians(45f + spinDeg));
        Vector3f halfFace = rotation.transform(new Vector3f(side / 2f, side / 2f, 0f));
        Vector3f translation = new Vector3f(BIT_AXIS.x, BIT_AXIS.y, z).sub(halfFace);
        return new Transformation(translation, rotation, new Vector3f(side, side, depth), new Quaternionf());
    }

    /** Cubierta de la rueda (cuadrada, como en Minecraft), doblada {@code steerRad}. */
    static Transformation tireTransform(WheelDef w, float steerRad) {
        Quaternionf rot = new Quaternionf().rotateY(w.steers() ? steerRad : 0f);
        float d = w.radius() * 2f;
        return centered(w.center(), new Vector3f(w.thickness(), d, d), rot);
    }

    /** Llanta de la rueda: un poco mas ancha que la cubierta para que asome, girada {@code spinRad} sobre el eje. */
    static Transformation hubTransform(WheelDef w, float steerRad, float spinRad) {
        Quaternionf rot = new Quaternionf().rotateY(w.steers() ? steerRad : 0f).rotateX(spinRad);
        float d = w.radius() * 1.05f;
        return centered(w.center(), new Vector3f(w.thickness() + 0.04f, d, d), rot);
    }

    private static final class Builder {
        final List<PartDef> parts = new ArrayList<>();
        final List<WheelDef> wheels = new ArrayList<>();
        final List<Vector3f> seats = new ArrayList<>();

        void box(String role, String slot, Material material, float x, float y, float z, float sx, float sy, float sz) {
            parts.add(new PartDef(role, slot, material, Shapes.box(x, y, z, sx, sy, sz), false, PartDef.Kind.STATIC, 0));
        }

        /** Dos piezas simetricas: la izquierda en x y la derecha espejada. */
        void pair(String role, String slot, Material material, float x, float y, float z, float sx, float sy, float sz) {
            box(role + "-izq", slot, material, x, y, z, sx, sy, sz);
            box(role + "-der", slot, material, -(x + sx), y, z, sx, sy, sz);
        }

        void glowPair(String role, Material material, float x, float y, float z, float sx, float sy, float sz) {
            parts.add(new PartDef(role + "-izq", null, material, Shapes.box(x, y, z, sx, sy, sz), true, PartDef.Kind.STATIC, 0));
            parts.add(new PartDef(role + "-der", null, material, Shapes.box(-(x + sx), y, z, sx, sy, sz), true, PartDef.Kind.STATIC, 0));
        }

        /** Dos ruedas en (+x, y, z) y (-x, y, z), centradas. */
        void wheelPair(float x, float y, float z, float radius, float thickness, boolean steers) {
            for (float sx : new float[]{x, -x}) {
                int i = wheels.size();
                WheelDef w = new WheelDef(new Vector3f(sx, y, z), radius, thickness, steers);
                wheels.add(w);
                parts.add(new PartDef("rueda-" + i, null, Material.BLACK_CONCRETE, tireTransform(w, 0f), false, PartDef.Kind.TIRE, i));
                parts.add(new PartDef("llanta-" + i, null, Material.LIGHT_GRAY_CONCRETE, hubTransform(w, 0f, 0f), false, PartDef.Kind.HUB, i));
            }
        }

        void seat(float x, float y, float z) {
            seats.add(new Vector3f(x, y, z));
        }

        Shape build(String id, double halfWidth, double halfLength, int height, double contact, float labelHeight,
                    Vector3f exhaust, float interactionWidth, float interactionHeight) {
            return new Shape(id, List.copyOf(parts), List.copyOf(wheels), List.copyOf(seats), halfWidth, halfLength,
                    height, contact, labelHeight, exhaust, interactionWidth, interactionHeight, null, 0f);
        }
    }
}
