package com.isjbar.minercorp.vehicles.model;

import org.bukkit.Material;
import org.bukkit.util.Transformation;

/**
 * Una pieza del modelo: una caja de bloque vanilla o un modelo del resource pack.
 *
 * @param role      nombre unico de la pieza dentro del modelo (se guarda en el PDC)
 * @param slot      color del tipo que usa (tipos.&lt;id&gt;.colores.&lt;slot&gt;), o null para material fijo
 * @param material  material si no hay slot, o el de fabrica si el tipo no define ese color
 * @param transform caja en coordenadas locales (sin animar)
 * @param glow      si brilla aunque este oscuro (faros, luces)
 * @param kind      como se anima
 * @param index     rueda o pieza de punta a la que pertenece (segun kind)
 * @param itemModel modelo del resource pack (por ejemplo "mccorp:vehicles/camion"); si no es
 *                  null la pieza es un ItemDisplay con ese modelo en vez de un bloque
 */
public record PartDef(String role, String slot, Material material, Transformation transform, boolean glow,
                      Kind kind, int index, String itemModel) {

    public PartDef(String role, String slot, Material material, Transformation transform, boolean glow,
                   Kind kind, int index) {
        this(role, slot, material, transform, glow, kind, index, null);
    }

    public enum Kind {
        /** No se anima. */
        STATIC,
        /** Cubierta de una rueda: dobla con la direccion si la rueda es delantera. */
        TIRE,
        /** Llanta de una rueda: gira al andar y dobla con la direccion. */
        HUB,
        /** Rueda entera del resource pack: gira al andar y dobla con la direccion. */
        WHEEL,
        /** Pieza de la punta del taladro: gira al perforar. */
        BIT,
        /** Punta entera del resource pack: gira sobre su eje (Z) al perforar. */
        BIT_MODEL,
        /** Motor del taladro: alto horno que se enciende al perforar. */
        MOTOR
    }
}
