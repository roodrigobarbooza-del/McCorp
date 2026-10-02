package com.isjbar.minercorp.economy.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/**
 * Se lanza en el hilo principal despues de que una transaccion se aplico.
 * No se puede cancelar: el dinero ya se movio. Sirve para scoreboards,
 * logros, misiones, avisos, etc.
 */
public class MoneyTransactionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String transactionId;
    private final Reason reason;
    private final List<TransactionRecord> records;

    public MoneyTransactionEvent(String transactionId, Reason reason, List<TransactionRecord> records) {
        this.transactionId = transactionId;
        this.reason = reason;
        this.records = List.copyOf(records);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Reason getReason() {
        return reason;
    }

    /** Una linea por cada cuenta que cambio de saldo (sin incluir a SERVER). */
    public List<TransactionRecord> getRecords() {
        return records;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
