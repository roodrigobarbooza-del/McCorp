package com.isjbar.minercorp.territory.api;

/**
 * Foto inmutable del estado de una veta en un instante dado. Se devuelve por
 * valor (nunca una referencia mutable) para que otros plugins puedan leerla
 * sin acoplarse a la representación interna de Territory.
 */
public record VeinSnapshot(int bloquesDetectados, double reservaActual, double reservaMaxima) {

    public boolean agotada() {
        return reservaActual <= 0.0;
    }

    public double porcentaje() {
        if (reservaMaxima <= 0) return 0;
        return (reservaActual / reservaMaxima) * 100.0;
    }
}
