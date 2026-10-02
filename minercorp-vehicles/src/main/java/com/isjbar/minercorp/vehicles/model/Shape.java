package com.isjbar.minercorp.vehicles.model;

import org.joml.Vector3f;

import java.util.List;

/**
 * Forma de un vehiculo: piezas, ruedas, asientos y medidas para la fisica.
 *
 * Sistema local: origen en el centro del vehiculo a ras del piso, +Z al
 * frente, +X a la izquierda, +Y arriba (igual que un display con yaw 0).
 *
 * @param seats        asientos; el primero es el del conductor
 * @param halfWidth    medio ancho para colisiones
 * @param halfLength   medio largo para colisiones
 * @param height       alto en bloques (cuantos bloques libres necesita por encima del piso)
 * @param contact      distancia del centro a los puntos de apoyo delantero y trasero
 * @param labelHeight  altura del cartel con el nombre
 * @param exhaust      punto donde sale el humo
 * @param interactionWidth  ancho de cada caja para hacerle click
 * @param interactionHeight alto de cada caja para hacerle click
 * @param bitAxis      eje de la punta del taladro (null si no tiene)
 * @param bitTipZ      donde termina la punta (para las chispas)
 */
public record Shape(String id, List<PartDef> parts, List<WheelDef> wheels, List<Vector3f> seats,
                    double halfWidth, double halfLength, int height, double contact, float labelHeight,
                    Vector3f exhaust, float interactionWidth, float interactionHeight,
                    Vector3f bitAxis, float bitTipZ) {

    /** Cantidad de cajas de click a lo largo del vehiculo para cubrirlo entero. */
    public int interactionCount() {
        return Math.max(1, (int) Math.ceil(halfLength * 2 / interactionWidth));
    }
}
