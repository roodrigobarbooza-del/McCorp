package com.isjbar.minercorp.economy.api;


import java.util.Locale;
import java.util.Objects;

/**
 * Motivo de un movimiento de dinero: una categoria corta (para agrupar y elegir
 * icono/color) y un detalle legible que ve el jugador en su historial.
 *
 * <pre>{@code Reason.of(Reason.VENTA, "64 de carbon refinado")}</pre>
 *
 * Las categorias son strings para que cualquier plugin pueda inventar las
 * suyas; las constantes de abajo son las estandar y tienen nombre y color
 * propios en los menus.
 */
public record Reason(String category, String detail) {

    public static final String VENTA = "venta";
    public static final String COMPRA = "compra";
    public static final String PAGO = "pago";
    public static final String SUELDO = "sueldo";
    public static final String IMPUESTO = "impuesto";
    public static final String MANTENIMIENTO = "mantenimiento";
    public static final String PRODUCCION = "produccion";
    public static final String BONO = "bono";
    public static final String ADMIN = "admin";
    public static final String OTRO = "otro";

    /** Motivo de los metodos viejos de la API que no reciben uno. */
    public static final Reason SIN_DETALLE = new Reason(OTRO, null);

    public Reason {
        Objects.requireNonNull(category, "category");
        category = category.trim().toLowerCase(Locale.ROOT);
        if (category.isEmpty()) category = OTRO;
        if (detail != null) {
            detail = detail.strip();
            if (detail.isEmpty()) detail = null;
            else if (detail.length() > 120) detail = detail.substring(0, 120);
        }
    }

    public static Reason of(String category, String detail) {
        return new Reason(category, detail);
    }

    public static Reason of(String category) {
        return new Reason(category, null);
    }

    /** Nombre de la categoria en espanol, con mayuscula ("Venta", "Mantenimiento"). */
    public String categoryLabel() {
        return switch (category) {
            case VENTA -> "Venta";
            case COMPRA -> "Compra";
            case PAGO -> "Pago";
            case SUELDO -> "Sueldo";
            case IMPUESTO -> "Impuesto";
            case MANTENIMIENTO -> "Mantenimiento";
            case PRODUCCION -> "Produccion";
            case BONO -> "Bono";
            case ADMIN -> "Ajuste del staff";
            case OTRO -> "Movimiento";
            default -> Character.toUpperCase(category.charAt(0)) + category.substring(1);
        };
    }

    /** Texto completo para mostrar: el detalle si hay, si no el nombre de la categoria. */
    public String describe() {
        return detail != null ? detail : categoryLabel();
    }
}
