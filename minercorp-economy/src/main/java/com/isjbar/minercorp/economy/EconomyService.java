package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountInfo;
import com.isjbar.minercorp.economy.api.AccountType;
import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.economy.api.MoneyTransactionEvent;
import com.isjbar.minercorp.economy.api.Reason;
import com.isjbar.minercorp.economy.api.Transaction;
import com.isjbar.minercorp.economy.api.TransactionRecord;
import com.isjbar.minercorp.economy.api.TransactionResult;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Implementacion de {@link EconomyAPI} sobre el {@link Ledger}. */
final class EconomyService implements EconomyAPI {

    private final Plugin plugin;
    private final Ledger ledger;
    private final Money money;

    EconomyService(Plugin plugin, Ledger ledger, Money money) {
        this.plugin = plugin;
        this.ledger = ledger;
        this.money = money;
        ledger.onCommit(this::fireEvent);
    }

    private void fireEvent(Ledger.Committed c) {
        if (!plugin.isEnabled()) return;
        Runnable call = () -> Bukkit.getPluginManager().callEvent(new MoneyTransactionEvent(c.id(), c.reason(), c.records()));
        if (Bukkit.isPrimaryThread()) call.run();
        else Bukkit.getScheduler().runTask(plugin, call);
    }

    @Override
    public void registerAccount(UUID id, AccountType type, String displayName, UUID owner) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (id.equals(SERVER)) return;
        ledger.register(id, type, displayName, owner);
    }

    @Override
    public Optional<AccountInfo> getAccount(UUID id) {
        return ledger.info(id);
    }

    @Override
    public boolean hasAccount(UUID id) {
        return ledger.exists(id);
    }

    @Override
    public double getBalance(UUID id) {
        return Money.toDouble(ledger.balance(id));
    }

    @Override
    public boolean has(UUID id, double amount) {
        if (SERVER.equals(id)) return true;
        try {
            return ledger.balance(id) >= Money.toCents(amount);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public List<AccountInfo> getAccountsOwnedBy(UUID playerId) {
        return ledger.ownedBy(playerId);
    }

    @Override
    public Optional<AccountInfo> findByName(String name) {
        return name == null ? Optional.empty() : ledger.findByName(name);
    }

    @Override
    public TransactionResult deposit(UUID to, double amount, Reason reason) {
        return execute(Transaction.single(SERVER, to, amount, reason));
    }

    @Override
    public TransactionResult withdraw(UUID from, double amount, Reason reason) {
        return execute(Transaction.single(from, SERVER, amount, reason));
    }

    @Override
    public TransactionResult transfer(UUID from, UUID to, double amount, Reason reason) {
        return execute(Transaction.single(from, to, amount, reason));
    }

    @Override
    public TransactionResult execute(Transaction transaction) {
        return ledger.execute(Objects.requireNonNull(transaction, "transaction"));
    }

    @Override
    public List<TransactionRecord> getHistory(UUID id, int limit) {
        return ledger.history(id, limit);
    }

    @Override
    public List<AccountInfo> getTop(AccountType type, int limit) {
        return ledger.top(type, limit);
    }

    @Override
    public String format(double amount) {
        try {
            return money.format(amount);
        } catch (IllegalArgumentException e) {
            return String.valueOf(amount);
        }
    }
}
