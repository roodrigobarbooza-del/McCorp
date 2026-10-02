package com.isjbar.minercorp.economy.api;


import java.util.UUID;

/**
 * Una linea del historial de una cuenta.
 *
 * @param transactionId id de la transaccion (la misma en el historial de todas las cuentas que tocó)
 * @param timestamp     epoch millis
 * @param account       cuenta duena de esta linea
 * @param counterparty  la otra cuenta, si hubo una sola; null en transacciones con varias contrapartes
 * @param amount        cuanto cambio el saldo de {@code account}: positivo si entro, negativo si salio
 * @param balanceAfter  saldo de {@code account} justo despues
 * @param reason        motivo
 */
public record TransactionRecord(String transactionId, long timestamp, UUID account, UUID counterparty,
                                double amount, double balanceAfter, Reason reason) {

    public boolean isIncome() {
        return amount > 0;
    }
}
