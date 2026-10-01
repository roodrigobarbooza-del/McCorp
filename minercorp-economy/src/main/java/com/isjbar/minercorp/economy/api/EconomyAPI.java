package com.isjbar.minercorp.economy.api;

import java.util.UUID;

/**
 * API publica de MinerCorp-Economy, pensada para ser consumida por otros
 * plugins (como MinerCorp-Mining) via el ServicesManager de Bukkit.
 *
 * Es un ledger generico por cuenta: la misma API sirve tanto para la
 * billetera personal de un jugador (accountId = UUID del jugador) como para
 * el balance de una empresa (accountId = UUID de la empresa) u otra entidad
 * que un plugin consumidor quiera manejar.
 *
 * El dia que el server instale Vault + un plugin de economia real, esta
 * interfaz se puede re-implementar como un puente a Vault sin tener que
 * tocar ningun otro plugin de MinerCorp.
 */
public interface EconomyAPI {

    /** Balance actual de la cuenta. Una cuenta nunca vista devuelve 0.0. */
    double getBalance(UUID accountId);

    /** Deposita fondos en la cuenta (la crea si no existia). */
    void deposit(UUID accountId, double amount);

    /** Retira fondos de la cuenta. Devuelve false (sin efecto) si no hay saldo suficiente. */
    boolean withdraw(UUID accountId, double amount);

    /** Mueve fondos de una cuenta a otra de forma atomica. Devuelve false si el origen no tenia saldo suficiente. */
    boolean transfer(UUID fromAccountId, UUID toAccountId, double amount);
}
