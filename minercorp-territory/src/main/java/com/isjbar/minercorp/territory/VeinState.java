package com.isjbar.minercorp.territory;

import com.isjbar.minercorp.territory.api.VeinSnapshot;

/**
 * Estado mutable interno de la reserva de carbon de un chunk reclamado.
 * Se calcula escaneando el chunk al momento de reclamarlo y se va agotando
 * a medida que se extrae. No se expone fuera de este modulo: los consumidores
 * de la API reciben un {@link VeinSnapshot} inmutable.
 */
class VeinState {

    private final int bloquesDetectados;
    private double reservaActual;
    private final double reservaMaxima;

    VeinState(int bloquesDetectados, double unidadesPorBloque) {
        this.bloquesDetectados = bloquesDetectados;
        this.reservaMaxima = bloquesDetectados * unidadesPorBloque;
        this.reservaActual = reservaMaxima;
    }

    VeinState(int bloquesDetectados, double reservaActual, double reservaMaxima) {
        this.bloquesDetectados = bloquesDetectados;
        this.reservaActual = reservaActual;
        this.reservaMaxima = reservaMaxima;
    }

    double extraer(double cantidad) {
        double disponible = Math.min(cantidad, reservaActual);
        reservaActual -= disponible;
        if (reservaActual < 0) reservaActual = 0;
        return disponible;
    }

    int getBloquesDetectados() {
        return bloquesDetectados;
    }

    double getReservaActual() {
        return reservaActual;
    }

    double getReservaMaxima() {
        return reservaMaxima;
    }

    VeinSnapshot toSnapshot() {
        return new VeinSnapshot(bloquesDetectados, reservaActual, reservaMaxima);
    }
}
