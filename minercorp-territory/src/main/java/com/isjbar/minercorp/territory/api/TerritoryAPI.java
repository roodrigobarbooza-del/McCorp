package com.isjbar.minercorp.territory.api;

import com.isjbar.minercorp.territory.util.ChunkKey;
import org.bukkit.Chunk;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API publica de MinerCorp-Territory, pensada para ser consumida por otros
 * plugins (como MinerCorp-Mining) via el ServicesManager de Bukkit, igual
 * que Vault expone su Economy.
 *
 * Territory no sabe nada de "empresas": todo se identifica con un
 * {@code ownerId} generico (UUID), para que cualquier plugin de trabajo
 * (mineria, pesca, tala, etc) pueda reclamar territorio sin que este modulo
 * tenga que conocer esos conceptos.
 */
public interface TerritoryAPI {

    /**
     * Intenta reclamar el chunk para el dueno indicado. Escanea el chunk en
     * busca de mineral de carbon para calcular la veta inicial.
     *
     * @param ownerId    identificador generico del dueno (ej: el UUID de una empresa)
     * @param ownerLabel nombre legible del dueno, solo para mensajes/logs
     * @param purpose    para que se usa este territorio, legible (ej: "Mineria de carbon"),
     *                   se muestra en el aviso de entrada al territorio
     * @param chunk      el chunk a reclamar
     */
    ClaimResult claim(UUID ownerId, String ownerLabel, String purpose, Chunk chunk);

    /** Libera un chunk reclamado por ese dueno. No hace nada si no le pertenece. */
    void unclaim(UUID ownerId, Chunk chunk);

    /** Reemplaza el set completo de jugadores autorizados a romper bloques en el territorio de ese dueno. */
    void setAuthorizedUsers(UUID ownerId, Set<UUID> authorized);

    /** Devuelve el dueno de un chunk, si esta reclamado. */
    Optional<UUID> getOwner(Chunk chunk);

    /** True si el jugador puede romper bloques en territorio de ese dueno (es el dueno o esta en su set de autorizados). */
    boolean isAuthorized(UUID ownerId, UUID playerId);

    /** Estado actual de la veta de un chunk, si esta reclamado y tiene una. */
    Optional<VeinSnapshot> getVein(Chunk chunk);

    /**
     * Extrae hasta {@code amount} unidades de la veta del chunk indicado.
     *
     * @return la cantidad realmente extraida (puede ser menor si la veta casi se agota, o 0 si no hay veta ahi)
     */
    double extractFromVein(Chunk chunk, double amount);

    /** Cantidad de chunks reclamados actualmente por ese dueno. */
    int countClaims(UUID ownerId);

    /** Lista de chunks reclamados por ese dueno. */
    List<ChunkKey> getClaims(UUID ownerId);
}
