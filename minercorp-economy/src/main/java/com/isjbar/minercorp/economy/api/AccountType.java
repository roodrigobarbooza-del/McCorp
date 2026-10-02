package com.isjbar.minercorp.economy.api;

/** Que clase de entidad es duena de una cuenta. Sirve para rankings y para mostrar nombres. */
public enum AccountType {
    /** Billetera personal de un jugador (id = UUID del jugador). */
    PLAYER,
    /** Balance de una empresa (id = UUID de la empresa). */
    COMPANY,
    /** Cuentas internas del servidor, como {@link EconomyAPI#SERVER}. No aparecen en rankings. */
    SYSTEM,
    /** Cualquier otra cosa, o una cuenta que recibio dinero antes de que alguien la registrara. */
    OTHER
}
