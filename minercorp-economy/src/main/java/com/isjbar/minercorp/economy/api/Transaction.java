package com.isjbar.minercorp.economy.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uno o varios movimientos de dinero que se aplican todos juntos o ninguno.
 *
 * <pre>{@code
 * Transaction tx = Transaction.builder(Reason.of(Reason.VENTA, "64 barriles de petroleo"))
 *         .move(comprador, vendedor, 640.0)
 *         .move(comprador, EconomyAPI.SERVER, 32.0) // comision del mercado
 *         .build();
 * TransactionResult r = economy.execute(tx);
 * }</pre>
 *
 * Usa {@link EconomyAPI#SERVER} como origen para crear dinero (ventas al
 * sistema, premios) o como destino para sacarlo de la economia (compras en la
 * gran sede, impuestos).
 */
public final class Transaction {

    /** Un movimiento: {@code amount} pasa de {@code from} a {@code to}. */
    public record Move(UUID from, UUID to, double amount) {
        public Move {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    private final Reason reason;
    private final List<Move> moves;

    private Transaction(Reason reason, List<Move> moves) {
        this.reason = reason;
        this.moves = List.copyOf(moves);
    }

    public static Builder builder(Reason reason) {
        return new Builder(reason);
    }

    /** Atajo para una transaccion de un solo movimiento. */
    public static Transaction single(UUID from, UUID to, double amount, Reason reason) {
        return builder(reason).move(from, to, amount).build();
    }

    public Reason reason() {
        return reason;
    }

    public List<Move> moves() {
        return moves;
    }

    public static final class Builder {
        private final Reason reason;
        private final List<Move> moves = new ArrayList<>();

        private Builder(Reason reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        public Builder move(UUID from, UUID to, double amount) {
            moves.add(new Move(from, to, amount));
            return this;
        }

        public Transaction build() {
            if (moves.isEmpty()) throw new IllegalStateException("Una transaccion necesita al menos un movimiento");
            return new Transaction(reason, moves);
        }
    }
}
