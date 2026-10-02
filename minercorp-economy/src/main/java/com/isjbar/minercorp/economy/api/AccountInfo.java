package com.isjbar.minercorp.economy.api;


import java.util.UUID;

/**
 * Foto de solo lectura de una cuenta en el momento en que se pidio.
 *
 * @param id          id de la cuenta
 * @param type        tipo de cuenta
 * @param displayName nombre visible (jugador o empresa); null si nadie la registro todavia
 * @param owner       jugador dueno (para empresas, quien recibe los avisos); null si no tiene
 * @param balance     saldo, redondeado a 2 decimales
 * @param createdAt   epoch millis de cuando se creo
 */
public record AccountInfo(UUID id, AccountType type, String displayName, UUID owner,
                          double balance, long createdAt) {

    /** Nombre para mostrar, con un texto de reemplazo si la cuenta no tiene nombre. */
    public String nameOr(String fallback) {
        return displayName == null || displayName.isBlank() ? fallback : displayName;
    }
}
