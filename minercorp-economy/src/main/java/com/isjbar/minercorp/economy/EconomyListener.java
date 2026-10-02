package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountInfo;
import com.isjbar.minercorp.economy.api.AccountType;
import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.economy.api.MoneyTransactionEvent;
import com.isjbar.minercorp.economy.api.Reason;
import com.isjbar.minercorp.economy.api.TransactionRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

/** Registra a los jugadores al entrar, da el bono de bienvenida y avisa en la action bar. */
final class EconomyListener implements Listener {

    private final EconomyPlugin plugin;

    EconomyListener(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean nueva = plugin.ledger().register(id, AccountType.PLAYER, player.getName(), id);
        if (!nueva) return;

        double saldoInicial = plugin.getConfig().getDouble("saldo-inicial", 100.0);
        if (saldoInicial > 0 && plugin.economy().deposit(id, saldoInicial, Reason.of(Reason.BONO, "Bono de bienvenida")).success()) {
            plugin.messages().send(player, "bienvenida", Messages.text("monto", plugin.economy().format(saldoInicial)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTransaction(MoneyTransactionEvent event) {
        if (!plugin.getConfig().getBoolean("avisos.action-bar", true)) return;
        double minimo = plugin.getConfig().getDouble("avisos.minimo", 1.0);
        EconomyAPI eco = plugin.economy();

        for (TransactionRecord r : event.getRecords()) {
            if (Math.abs(r.amount()) < minimo) continue;
            UUID destinatario = eco.getAccount(r.account())
                    .map(AccountInfo::owner)
                    .orElse(null);
            if (destinatario == null) continue;
            Player player = Bukkit.getPlayer(destinatario);
            if (player == null) continue;

            String motivo = r.reason().describe();
            if (!destinatario.equals(r.account())) {
                motivo = plugin.messages().accountName(r.account()) + " · " + motivo;
            }
            player.sendActionBar(plugin.messages().raw(r.isIncome() ? "action-bar-ingreso" : "action-bar-gasto",
                    Messages.text("monto", eco.format(Math.abs(r.amount()))),
                    Messages.text("motivo", motivo)));
        }
    }
}
