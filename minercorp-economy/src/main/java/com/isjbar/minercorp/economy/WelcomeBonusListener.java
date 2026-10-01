package com.isjbar.minercorp.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Le da el saldo inicial configurado a los jugadores que todavia no tienen cuenta. */
class WelcomeBonusListener implements Listener {

    private final AccountManager accounts;
    private final double saldoInicial;

    WelcomeBonusListener(AccountManager accounts, double saldoInicial) {
        this.accounts = accounts;
        this.saldoInicial = saldoInicial;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var id = event.getPlayer().getUniqueId();
        if (!accounts.hasAccount(id)) {
            accounts.deposit(id, saldoInicial);
        }
    }
}
