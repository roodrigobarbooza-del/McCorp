package com.isjbar.minercorp.economy.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API publica de MinerCorp-Economy, pensada para ser consumida por otros
 * plugins via el ServicesManager de Bukkit:
 *
 * <pre>{@code EconomyAPI eco = getServer().getServicesManager().load(EconomyAPI.class);}</pre>
 *
 * Es un ledger generico por cuenta: la misma API sirve para la billetera de
 * un jugador (id = UUID del jugador), el balance de una empresa (id = UUID de
 * la empresa) u otra entidad que un plugin quiera manejar.
 *
 * Reglas comunes:
 * <ul>
 *     <li>Los montos se redondean a 2 decimales. Negativos, NaN e infinitos se
 *     rechazan con {@link TransactionResult.Status#INVALID_AMOUNT}; un monto 0
 *     es OK y no mueve nada.</li>
 *     <li>Ninguna cuenta (salvo {@link #SERVER}) puede quedar en negativo.</li>
 *     <li>Todos los metodos son thread-safe. {@link MoneyTransactionEvent} se
 *     lanza siempre en el hilo principal.</li>
 * </ul>
 *
 * El dia que el server instale Vault, esta interfaz se puede re-implementar
 * como un puente sin tocar los plugins que la usan.
 */
public interface EconomyAPI {

    /**
     * Cuenta del servidor: de aca sale el dinero que entra a la economia
     * (ventas al sistema, premios, bonos) y aca va el que sale (compras en la
     * gran sede, impuestos, mantenimiento). Nunca se queda sin fondos.
     */
    UUID SERVER = new UUID(0L, 0L);

    // ------------------------------------------------------------------ cuentas

    /**
     * Crea la cuenta si no existe y actualiza su tipo, nombre y dueno. Llamalo
     * al crear, cargar y renombrar una empresa. Los jugadores se registran
     * solos al entrar al server.
     *
     * @param owner jugador que recibe los avisos de esta cuenta (puede ser null)
     */
    void registerAccount(UUID id, AccountType type, String displayName, UUID owner);

    /** Datos de la cuenta, o vacio si nunca existio. */
    Optional<AccountInfo> getAccount(UUID id);

    boolean hasAccount(UUID id);

    /** Saldo actual. Una cuenta que no existe devuelve 0. */
    double getBalance(UUID id);

    /** True si la cuenta tiene al menos {@code amount}. SERVER siempre tiene. */
    boolean has(UUID id, double amount);

    /** Cuentas cuyo dueno es este jugador (su billetera y las empresas que registro como suyas). */
    List<AccountInfo> getAccountsOwnedBy(UUID playerId);

    /** Busca una cuenta por nombre visible, sin importar mayusculas. */
    Optional<AccountInfo> findByName(String name);

    // ------------------------------------------------------------------- dinero

    /** Pone dinero nuevo en la cuenta (SERVER -> to). La crea si no existia. */
    TransactionResult deposit(UUID to, double amount, Reason reason);

    /** Saca dinero de la cuenta (from -> SERVER). Falla sin efecto si no alcanza. */
    TransactionResult withdraw(UUID from, double amount, Reason reason);

    /** Mueve dinero entre dos cuentas. Falla sin efecto si el origen no tiene suficiente. */
    TransactionResult transfer(UUID from, UUID to, double amount, Reason reason);

    /** Aplica todos los movimientos de la transaccion, o ninguno. */
    TransactionResult execute(Transaction transaction);

    // ---------------------------------------------------------------- consultas

    /** Ultimos movimientos de la cuenta, del mas nuevo al mas viejo. */
    List<TransactionRecord> getHistory(UUID id, int limit);

    /** Las cuentas mas ricas. {@code type} null = jugadores y empresas juntos. */
    List<AccountInfo> getTop(AccountType type, int limit);

    /** Formatea un monto con el estilo del server, por ejemplo "$1.234,50". */
    String format(double amount);

    // ------------------------------------------- compatibilidad con la API vieja

    /** Igual que {@link #deposit(UUID, double, Reason)} sin motivo. */
    default void deposit(UUID accountId, double amount) {
        deposit(accountId, amount, Reason.SIN_DETALLE);
    }

    /** Igual que {@link #withdraw(UUID, double, Reason)} sin motivo. Devuelve false si no alcanzo. */
    default boolean withdraw(UUID accountId, double amount) {
        return withdraw(accountId, amount, Reason.SIN_DETALLE).success();
    }

    /** Igual que {@link #transfer(UUID, UUID, double, Reason)} sin motivo. Devuelve false si no alcanzo. */
    default boolean transfer(UUID fromAccountId, UUID toAccountId, double amount) {
        return transfer(fromAccountId, toAccountId, amount, Reason.SIN_DETALLE).success();
    }
}
