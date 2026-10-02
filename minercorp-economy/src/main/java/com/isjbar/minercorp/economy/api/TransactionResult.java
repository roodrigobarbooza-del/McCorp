package com.isjbar.minercorp.economy.api;


import java.util.UUID;

/**
 * Resultado de una operacion de dinero.
 *
 * @param status        que paso
 * @param transactionId id de la transaccion si se aplico (para el historial y los logs), si no null
 * @param failedAccount en {@link Status#INSUFFICIENT_FUNDS}, la cuenta a la que le falto saldo
 * @param message       explicacion en espanol, lista para mostrarle al jugador
 */
public record TransactionResult(Status status, String transactionId, UUID failedAccount,
                                String message) {

    public enum Status {
        /** Se aplico. Tambien es OK un monto 0: no se mueve nada ni queda en el historial. */
        OK,
        /** Alguna cuenta (que no sea SERVER) habria quedado en negativo. No se aplico nada. */
        INSUFFICIENT_FUNDS,
        /** Monto negativo, NaN o infinito. */
        INVALID_AMOUNT,
        /** Un movimiento tenia el mismo origen y destino. */
        SAME_ACCOUNT
    }

    public boolean success() {
        return status == Status.OK;
    }
}
