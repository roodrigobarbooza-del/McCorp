package com.isjbar.minercorp.vehicles.model;

import org.joml.Vector3f;

import java.util.List;

/**
 * Forma de un vehiculo: piezas, ruedas, asientos y medidas para la fisica.
 *
 * Sistema local: origen en el centro del vehiculo a ras del piso, +Z al
 * frente, +X a la izquierda, +Y arriba (igual que un display con yaw 0).
 *
 * @param signature    identifica la forma armada (modelo, bloques o pack, version); si un
 *                     vehiculo del mundo tiene otra, se vuelve a armar
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
 * @param clickRear    z local donde empiezan las cajas de click del cuerpo (lo de atras es el panel)
 * @param console      panel de control aparte (centro de su base), o null si no tiene
 */
public record Shape(String id, String signature, List<PartDef> parts, List<WheelDef> wheels, List<Vector3f> seats,
                    double halfWidth, double halfLength, int height, double contact, float labelHeight,
                    Vector3f exhaust, float interactionWidth, float interactionHeight,
                    Vector3f bitAxis, float bitTipZ, float clickRear, Vector3f console) {

    /** Cantidad de cajas de click a lo largo del cuerpo para cubrirlo entero. */
    public int interactionCount() {
        return Math.max(1, (int) Math.ceil((halfLength - clickRear) / interactionWidth));
    }
}
