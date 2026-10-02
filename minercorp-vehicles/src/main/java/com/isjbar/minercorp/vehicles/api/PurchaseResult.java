package com.isjbar.minercorp.vehicles.api;

/** Resultado de {@link VehiclesAPI#purchase}. */
public enum PurchaseResult {
    OK("Vehiculo comprado: ya esta en tu garaje (/garaje)."),
    TIPO_INVALIDO("Ese vehiculo no existe."),
    SIN_EMPRESA("Ese vehiculo lo compra una empresa y no perteneces a ninguna."),
    SEDE_SIN_TERMINAR("Primero termina la obra de la sede de tu empresa (/empresa obra)."),
    NIVEL_INSUFICIENTE("Tu empresa no tiene el nivel necesario para este vehiculo."),
    SIN_SALDO("No hay saldo suficiente.");

    private final String message;

    PurchaseResult(String message) {
        this.message = message;
    }

    /** Mensaje listo para mostrarle al jugador. */
    public String message() {
        return message;
    }
}
