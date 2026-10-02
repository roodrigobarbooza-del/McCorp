package com.isjbar.minercorp.vehicles.model;

import org.joml.Vector3f;

/**
 * Una rueda: centro en coordenadas locales, radio, ancho y si dobla con la
 * direccion (las delanteras).
 */
public record WheelDef(Vector3f center, float radius, float thickness, boolean steers) {
}
