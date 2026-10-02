package com.isjbar.minercorp.resources.command;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.resources.ResourcesPlugin;
import com.isjbar.minercorp.resources.machine.MachineType;
import com.isjbar.minercorp.resources.resource.ResourceType;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import com.isjbar.minercorp.territory.api.VeinSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * /recursos sondear | vender [todo] | lista | dar &lt;jugador&gt; &lt;id&gt; [cantidad] | recargar
 */
public class RecursosCommand implements TabExecutor {

    private final ResourcesPlugin plugin;

    public RecursosCommand(ResourcesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "ayuda" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sondear" -> sondear(sender);
            case "vender" -> vender(sender, args.length > 1 && args[1].equalsIgnoreCase("todo"));
            case "lista" -> lista(sender);
            case "dar" -> dar(sender, args);
            case "recargar" -> recargar(sender);
            default -> ayuda(sender);
        }
        return true;
    }

    private void ayuda(CommandSender sender) {
        sender.sendMessage(Component.text("--- Recursos MinerCorp ---", NamedTextColor.DARK_AQUA));
        linea(sender, "/recursos sondear", "que hay bajo el chunk donde estas");
        linea(sender, "/recursos vender [todo]", "vende lo que tenes en la mano (o todo)");
        linea(sender, "/recursos lista", "recursos, precios y maquinas");
        if (sender.hasPermission("minercorp.admin")) {
            linea(sender, "/recursos dar <jugador> <id> [n]", "da un recurso o una maquina");
            linea(sender, "/recursos recargar", "relee el config.yml");
        }
    }

    private void sondear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return;
        }
        Chunk chunk = player.getLocation().getChunk();
        TerritoryAPI territory = plugin.territory();
        msg(player, NamedTextColor.AQUA, "Sondeo del chunk " + chunk.getX() + ", " + chunk.getZ() + ":");
        Optional<VeinSnapshot> vein = territory.getVein(chunk);
        if (vein.isPresent()) {
            linea(player, "Carbon", round(vein.get().reservaActual()) + " / " + round(vein.get().reservaMaxima())
                    + " (" + Math.round(vein.get().porcentaje()) + "%)");
        } else {
            linea(player, "Carbon", territory.getOwner(chunk).isPresent() ? "sin veta" : "se mide al reclamar el chunk");
        }
        int capacity = plugin.oil().capacity(chunk);
        if (capacity <= 0) {
            linea(player, "Petroleo", "no hay");
        } else {
            linea(player, "Petroleo", round(plugin.oil().remaining(chunk)) + " de " + capacity + " barriles");
        }
    }

    private void vender(CommandSender sender, boolean todo) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return;
        }
        PlayerInventory inv = player.getInventory();
        double total = 0;
        int unidades = 0;
        if (todo) {
            for (int i = 0; i < inv.getSize(); i++) {
                double value = value(inv.getItem(i));
                if (value <= 0) continue;
                total += value;
                unidades += inv.getItem(i).getAmount();
                inv.setItem(i, null);
            }
        } else {
            ItemStack hand = inv.getItemInMainHand();
            double value = value(hand);
            if (value > 0) {
                total = value;
                unidades = hand.getAmount();
                inv.setItemInMainHand(null);
            }
        }
        if (total <= 0) {
            msg(player, NamedTextColor.RED, todo ? "No tenes recursos para vender." : "Agarra en la mano el recurso que queres vender.");
            return;
        }
        // Si estas en el territorio de tu empresa la plata va a la empresa; si no, a vos.
        UUID cuenta = player.getUniqueId();
        String destino = "tu billetera";
        Optional<UUID> owner = plugin.territory().getOwner(player.getLocation().getChunk());
        if (owner.isPresent() && plugin.territory().isAuthorized(owner.get(), player.getUniqueId())) {
            cuenta = owner.get();
            destino = "la cuenta de tu empresa";
        }
        EconomyAPI economy = plugin.economy();
        economy.deposit(cuenta, total);
        msg(player, NamedTextColor.GREEN, "Vendiste " + unidades + " unidades por " + round(total) + ". Fue a " + destino + ".");
    }

    private double value(ItemStack item) {
        return plugin.resources().identify(item)
                .flatMap(id -> plugin.resources().get(id))
                .map(type -> type.price() * item.getAmount())
                .orElse(0.0);
    }

    private void lista(CommandSender sender) {
        sender.sendMessage(Component.text("--- Recursos ---", NamedTextColor.DARK_AQUA));
        for (ResourceType type : plugin.resources().all()) {
            sender.sendMessage(Component.text(" " + type.name(), type.color())
                    .append(Component.text(" (" + type.id() + ") ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(type.price() + " c/u", NamedTextColor.GREEN)));
        }
        sender.sendMessage(Component.text("--- Maquinas ---", NamedTextColor.DARK_AQUA));
        for (MachineType type : plugin.machines().types()) {
            String detalle = switch (type.kind()) {
                case VETA_CARBON -> "veta de carbon -> " + plugin.resources().displayName(type.outputs().get(0).resource());
                case PETROLEO -> "yacimiento -> " + plugin.resources().displayName(type.outputs().get(0).resource());
                case PROCESO -> String.join(" + ", type.input().keySet().stream().map(plugin.resources()::displayName).toList())
                        + " -> " + String.join(", ", type.outputs().stream().map(o -> plugin.resources().displayName(o.resource())).toList());
            };
            sender.sendMessage(Component.text(" " + type.name(), NamedTextColor.AQUA)
                    .append(Component.text(" (" + type.id() + ") ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(detalle, NamedTextColor.GRAY)));
        }
    }

    private void dar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minercorp.admin")) {
            sender.sendMessage(Component.text("No tenes permiso.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Uso: /recursos dar <jugador> <id> [cantidad]", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Ese jugador no esta conectado.", NamedTextColor.RED));
            return;
        }
        int cantidad = 1;
        if (args.length > 3) {
            try {
                cantidad = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Cantidad invalida.", NamedTextColor.RED));
                return;
            }
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        List<ItemStack> items = new ArrayList<>();
        Optional<MachineType> machine = plugin.machines().type(id);
        if (machine.isPresent()) {
            for (int i = 0; i < cantidad; i++) items.add(plugin.machines().createItem(machine.get()));
        } else if (plugin.resources().get(id).isPresent()) {
            int left = cantidad;
            while (left > 0) {
                ItemStack stack = plugin.resources().create(id, left).orElseThrow();
                left -= stack.getAmount();
                items.add(stack);
            }
        } else {
            sender.sendMessage(Component.text("No existe el recurso o maquina '" + id + "'. Mira /recursos lista.", NamedTextColor.RED));
            return;
        }
        for (ItemStack item : items) {
            target.getInventory().addItem(item).values()
                    .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
        sender.sendMessage(Component.text("Le diste " + cantidad + " x " + id + " a " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void recargar(CommandSender sender) {
        if (!sender.hasPermission("minercorp.admin")) {
            sender.sendMessage(Component.text("No tenes permiso.", NamedTextColor.RED));
            return;
        }
        plugin.reload();
        sender.sendMessage(Component.text("Config de MinerCorp-Recursos recargado. Los cambios de maquinas ya colocadas se aplican al reiniciar.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("sondear", "vender", "lista"));
            if (sender.hasPermission("minercorp.admin")) options.addAll(List.of("dar", "recargar"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("vender")) {
            options.add("todo");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar")) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            plugin.resources().all().forEach(t -> options.add(t.id()));
            plugin.machines().types().forEach(t -> options.add(t.id()));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    private static void msg(CommandSender sender, NamedTextColor color, String text) {
        sender.sendMessage(Component.text("[Recursos] ", NamedTextColor.DARK_AQUA).append(Component.text(text, color)));
    }

    private static void linea(CommandSender sender, String key, String value) {
        sender.sendMessage(Component.text(" " + key + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE)));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
