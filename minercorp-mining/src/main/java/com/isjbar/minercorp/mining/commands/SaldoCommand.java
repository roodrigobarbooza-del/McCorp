package com.isjbar.minercorp.mining.commands;

import com.isjbar.minercorp.mining.MiningPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class SaldoCommand implements CommandExecutor, TabCompleter {

    private final MiningPlugin plugin;

    public SaldoCommand(MiningPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("dar")) {
            return darSaldo(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando.");
            return true;
        }
        double saldo = plugin.economy().getBalance(player.getUniqueId());
        player.sendMessage(Component.text("Tu saldo personal: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.2f", saldo), NamedTextColor.GREEN)));
        return true;
    }

    private boolean darSaldo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minercorp.admin")) {
            sender.sendMessage(Component.text("No tenes permiso para hacer esto.", NamedTextColor.RED));
            return true;
        }

        double monto;
        try {
            monto = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Monto invalido.", NamedTextColor.RED));
            return true;
        }
        if (monto <= 0) {
            sender.sendMessage(Component.text("El monto debe ser mayor a 0.", NamedTextColor.RED));
            return true;
        }

        OfflinePlayer target;
        if (args.length >= 3) {
            target = Bukkit.getOfflinePlayer(args[2]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Uso: /saldo dar <monto> <jugador>", NamedTextColor.YELLOW));
            return true;
        }

        plugin.economy().deposit(target.getUniqueId(), monto);
        sender.sendMessage(Component.text("Le diste " + monto + " a " + target.getName() + ".", NamedTextColor.GREEN));
        if (target instanceof Player onlineTarget && !onlineTarget.equals(sender)) {
            onlineTarget.sendMessage(Component.text("Recibiste " + monto + " en tu saldo personal.", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("dar");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
