package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountInfo;
import com.isjbar.minercorp.economy.api.AccountType;
import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.economy.api.Reason;
import com.isjbar.minercorp.economy.api.Transaction;
import com.isjbar.minercorp.economy.api.TransactionRecord;
import com.isjbar.minercorp.economy.api.TransactionResult;
import com.isjbar.minercorp.economy.api.TransactionResult.Status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * El libro de cuentas en memoria. Todas las lecturas y escrituras pasan por un
 * unico lock, asi que cada transaccion es atomica aunque la llamen desde
 * varios hilos. El guardado a disco lo hace {@link LedgerStorage}.
 */
final class Ledger {

    /** Una transaccion ya aplicada, para lanzar el evento y los avisos. */
    record Committed(String id, Reason reason, List<TransactionRecord> records) {}

    /** Copia de una cuenta para guardar a disco sin tener el lock. */
    record AccountSnapshot(UUID id, AccountType type, String name, UUID owner, long balance, long createdAt,
                           List<TransactionRecord> history) {}

    record Snapshot(long minted, long burned, long lastTxNumber, List<AccountSnapshot> accounts) {}

    private final Object lock = new Object();
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Money money;
    private final int historyLimit;

    private long minted;
    private long burned;
    private long lastTxNumber;
    private boolean dirty;

    private final Queue<String> auditLines = new ConcurrentLinkedQueue<>();
    private Consumer<Committed> onCommit = c -> {};

    Ledger(Money money, int historyLimit) {
        this.money = money;
        this.historyLimit = Math.max(10, historyLimit);
    }

    void onCommit(Consumer<Committed> listener) {
        this.onCommit = Objects.requireNonNull(listener);
    }

    // ------------------------------------------------------------- transacciones

    TransactionResult execute(Transaction tx) {
        // 1) Validar montos y calcular el cambio neto de cada cuenta, sin lock.
        Map<UUID, Long> deltas = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> contrapartes = new HashMap<>();
        for (Transaction.Move move : tx.moves()) {
            if (move.from().equals(move.to())) {
                return fail(Status.SAME_ACCOUNT, null, "No puedes mover dinero a la misma cuenta.");
            }
            long cents;
            try {
                cents = Money.toCents(move.amount());
            } catch (IllegalArgumentException e) {
                return fail(Status.INVALID_AMOUNT, null, "El monto no es valido.");
            }
            if (cents < 0) return fail(Status.INVALID_AMOUNT, null, "El monto no puede ser negativo.");
            if (cents == 0) continue;
            try {
                deltas.merge(move.from(), -cents, Math::addExact);
                deltas.merge(move.to(), cents, Math::addExact);
            } catch (ArithmeticException e) {
                return fail(Status.INVALID_AMOUNT, null, "El monto es demasiado grande.");
            }
            contrapartes.computeIfAbsent(move.from(), k -> new LinkedHashSet<>()).add(move.to());
            contrapartes.computeIfAbsent(move.to(), k -> new LinkedHashSet<>()).add(move.from());
        }
        deltas.values().removeIf(d -> d == 0);
        if (deltas.isEmpty()) {
            return new TransactionResult(Status.OK, null, null, "No habia nada que mover.");
        }

        Committed committed;
        synchronized (lock) {
            // 2) Chequear que nadie quede en negativo antes de tocar nada.
            for (Map.Entry<UUID, Long> e : deltas.entrySet()) {
                if (e.getKey().equals(EconomyAPI.SERVER)) continue;
                long saldo = balanceLocked(e.getKey());
                long despues;
                try {
                    despues = Math.addExact(saldo, e.getValue());
                } catch (ArithmeticException ex) {
                    return fail(Status.INVALID_AMOUNT, null, "El monto es demasiado grande.");
                }
                if (despues < 0) {
                    return fail(Status.INSUFFICIENT_FUNDS, e.getKey(),
                            "No hay saldo suficiente: faltan " + money.format(-despues) + ".");
                }
            }

            // 3) Aplicar.
            String txId = "T" + (++lastTxNumber);
            long now = System.currentTimeMillis();
            List<TransactionRecord> records = new ArrayList<>();
            for (Map.Entry<UUID, Long> e : deltas.entrySet()) {
                UUID id = e.getKey();
                long delta = e.getValue();
                if (id.equals(EconomyAPI.SERVER)) {
                    if (delta < 0) minted += -delta;
                    else burned += delta;
                    continue;
                }
                Account acc = accounts.computeIfAbsent(id, k -> new Account(k, AccountType.OTHER, now));
                acc.balance += delta;
                Set<UUID> otros = contrapartes.getOrDefault(id, Set.of());
                UUID contraparte = otros.size() == 1 ? otros.iterator().next() : null;
                TransactionRecord rec = new TransactionRecord(txId, now, id, contraparte,
                        Money.toDouble(delta), Money.toDouble(acc.balance), tx.reason());
                acc.history.addFirst(rec);
                while (acc.history.size() > historyLimit) acc.history.removeLast();
                records.add(rec);
                auditLines.add(auditLine(rec, delta, acc.balance));
            }
            dirty = true;
            committed = new Committed(txId, tx.reason(), records);
        }

        onCommit.accept(committed);
        return new TransactionResult(Status.OK, committed.id(), null, "Listo.");
    }

    private static TransactionResult fail(Status status, UUID account, String message) {
        return new TransactionResult(status, null, account, message);
    }

    private static String auditLine(TransactionRecord r, long delta, long saldo) {
        return Instant.ofEpochMilli(r.timestamp()) + ";" + r.transactionId() + ";" + r.account() + ";" + delta + ";"
                + saldo + ";" + (r.counterparty() == null ? "-" : r.counterparty()) + ";" + r.reason().category()
                + ";" + (r.reason().detail() == null ? "" : r.reason().detail().replace('\n', ' '));
    }

    // -------------------------------------------------------------------- cuentas

    /** Crea o actualiza la cuenta. Devuelve true si no existia. */
    boolean register(UUID id, AccountType type, String name, UUID owner) {
        synchronized (lock) {
            Account acc = accounts.get(id);
            boolean nueva = acc == null;
            if (nueva) {
                acc = new Account(id, type, System.currentTimeMillis());
                accounts.put(id, acc);
            }
            if (nueva || acc.type != type || !Objects.equals(acc.name, name) || !Objects.equals(acc.owner, owner)) {
                acc.type = type;
                acc.name = name;
                acc.owner = owner;
                dirty = true;
            }
            return nueva;
        }
    }

    boolean exists(UUID id) {
        synchronized (lock) {
            return accounts.containsKey(id);
        }
    }

    long balance(UUID id) {
        synchronized (lock) {
            return balanceLocked(id);
        }
    }

    private long balanceLocked(UUID id) {
        Account acc = accounts.get(id);
        return acc == null ? 0 : acc.balance;
    }

    Optional<AccountInfo> info(UUID id) {
        synchronized (lock) {
            Account acc = accounts.get(id);
            return acc == null ? Optional.empty() : Optional.of(acc.info());
        }
    }

    List<TransactionRecord> history(UUID id, int limit) {
        synchronized (lock) {
            Account acc = accounts.get(id);
            if (acc == null || limit <= 0) return List.of();
            return acc.history.stream().limit(limit).toList();
        }
    }

    List<AccountInfo> top(AccountType type, int limit) {
        synchronized (lock) {
            return accounts.values().stream()
                    .filter(a -> type == null ? (a.type == AccountType.PLAYER || a.type == AccountType.COMPANY) : a.type == type)
                    .filter(a -> a.balance > 0)
                    .sorted(Comparator.comparingLong((Account a) -> a.balance).reversed())
                    .limit(Math.max(0, limit))
                    .map(Account::info)
                    .toList();
        }
    }

    List<AccountInfo> ownedBy(UUID playerId) {
        synchronized (lock) {
            return accounts.values().stream()
                    .filter(a -> playerId.equals(a.owner) || a.id.equals(playerId))
                    .sorted(Comparator.comparing((Account a) -> a.type).thenComparing(a -> a.createdAt))
                    .map(Account::info)
                    .toList();
        }
    }

    Optional<AccountInfo> findByName(String name) {
        String buscado = name.trim().toLowerCase(Locale.ROOT);
        synchronized (lock) {
            // Primero jugadores y empresas, para que una cuenta OTHER con el mismo nombre no tape a nadie.
            return accounts.values().stream()
                    .filter(a -> a.name != null && a.name.toLowerCase(Locale.ROOT).equals(buscado))
                    .min(Comparator.comparing((Account a) -> a.type))
                    .map(Account::info);
        }
    }

    List<String> names(AccountType type) {
        synchronized (lock) {
            return accounts.values().stream()
                    .filter(a -> a.type == type && a.name != null)
                    .map(a -> a.name)
                    .toList();
        }
    }

    long minted() {
        synchronized (lock) {
            return minted;
        }
    }

    long burned() {
        synchronized (lock) {
            return burned;
        }
    }

    int accountCount() {
        synchronized (lock) {
            return accounts.size();
        }
    }

    /** Suma de los saldos de jugadores y empresas: el dinero en circulacion. */
    long circulating() {
        synchronized (lock) {
            return accounts.values().stream().mapToLong(a -> a.balance).sum();
        }
    }

    // --------------------------------------------------------------- persistencia

    boolean isDirty() {
        synchronized (lock) {
            return dirty;
        }
    }

    /** Copia todo para guardarlo y marca el ledger como limpio. */
    Snapshot snapshot() {
        synchronized (lock) {
            List<AccountSnapshot> lista = new ArrayList<>(accounts.size());
            for (Account a : accounts.values()) {
                lista.add(new AccountSnapshot(a.id, a.type, a.name, a.owner, a.balance, a.createdAt, List.copyOf(a.history)));
            }
            dirty = false;
            return new Snapshot(minted, burned, lastTxNumber, lista);
        }
    }

    /** Si guardar fallo, hay que volver a intentarlo en el proximo ciclo. */
    void markDirty() {
        synchronized (lock) {
            dirty = true;
        }
    }

    void restore(Snapshot snap) {
        synchronized (lock) {
            accounts.clear();
            for (AccountSnapshot s : snap.accounts()) {
                Account a = new Account(s.id(), s.type(), s.createdAt());
                a.name = s.name();
                a.owner = s.owner();
                a.balance = s.balance();
                a.history.addAll(s.history().stream().limit(historyLimit).toList());
                accounts.put(a.id, a);
            }
            minted = snap.minted();
            burned = snap.burned();
            lastTxNumber = snap.lastTxNumber();
            dirty = false;
        }
    }

    /** Saca las lineas del log de auditoria pendientes de escribir. */
    List<String> drainAudit() {
        List<String> lineas = new ArrayList<>();
        String l;
        while ((l = auditLines.poll()) != null) lineas.add(l);
        return lineas;
    }
}
