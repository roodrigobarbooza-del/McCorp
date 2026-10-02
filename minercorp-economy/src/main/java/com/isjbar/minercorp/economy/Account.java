package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountInfo;
import com.isjbar.minercorp.economy.api.AccountType;
import com.isjbar.minercorp.economy.api.TransactionRecord;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Estado mutable de una cuenta. Solo se toca con el lock del {@link Ledger}. */
final class Account {

    final UUID id;
    AccountType type;
    String name;
    UUID owner;
    long balance;
    final long createdAt;
    /** Del mas nuevo (primero) al mas viejo. */
    final Deque<TransactionRecord> history = new ArrayDeque<>();

    Account(UUID id, AccountType type, long createdAt) {
        this.id = id;
        this.type = type;
        this.createdAt = createdAt;
    }

    AccountInfo info() {
        return new AccountInfo(id, type, name, owner, Money.toDouble(balance), createdAt);
    }
}
