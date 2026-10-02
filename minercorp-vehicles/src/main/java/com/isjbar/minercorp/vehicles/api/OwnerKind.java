package com.isjbar.minercorp.vehicles.api;

/** Quien es dueno de un tipo de vehiculo. */
public enum OwnerKind {
    /** Lo paga el jugador con su billetera y solo el lo maneja. */
    JUGADOR,
    /** Lo paga la empresa con su balance y lo maneja cualquier miembro. */
    EMPRESA
}
