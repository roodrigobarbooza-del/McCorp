package com.isjbar.minercorp.vehicles.command;

import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.api.OwnerKind;
import com.isjbar.minercorp.vehicles.gui.Menus;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** /vehiculo y /garaje. */
public class VehiculoCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("garaje", "tienda", "guardar", "cargar", "limpiar", "dar", "recargar");

    private final VehiclesPlugin plugin;

    public VehiculoCommand(VehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("garaje")) {
            if (sender instanceof Player player) Menus.openGarage(plugin, player);
            return true;
        }
        String sub = args.length == 0 ? "garaje" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("dar")) return dar(sender, args);
        if (sub.equals("recargar")) {
            if (!sender.hasPermission("minercorp.vehiculos.admin")) return noPerm(sender);
            plugin.reloadAll();
            sender.sendMessage(Component.text("Config de vehiculos recargado.", NamedTextColor.GREEN));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }

        switch (sub) {
            case "garaje" -> Menus.openGarage(plugin, player);
            case "tienda" -> {
                if (!plugin.getConfig().getBoolean("tienda-por-comando", true) && !player.hasPermission("minercorp.vehiculos.admin")) {
                    msg(player, NamedTextColor.YELLOW, "Los vehiculos se compran en el concesionario de la gran sede.");
                } else {
                    Menus.openShop(plugin, player);
                }
            }
            case "guardar" -> guardar(player);
            case "cargar" -> cargar(player, args);
            case "limpiar" -> limpiar(player);
            default -> ayuda(player);
        }
        return true;
    }

    private void guardar(Player player) {
        Optional<BlockDisplay> nearest = plugin.vehicles().nearestUsable(player, 6);
        if (nearest.isEmpty()) {
            msg(player, NamedTextColor.RED, "No hay ningun vehiculo tuyo cerca.");
            return;
        }
        if (!plugin.vehicles().store(nearest.get())) {
            msg(player, NamedTextColor.RED, "No se puede guardar con gente arriba.");
            return;
        }
        msg(player, NamedTextColor.GREEN, "Vehiculo guardado en el garaje. Sacalo con /garaje.");
    }

    /** Carga combustible al taladro cercano con carbon crudo de la empresa. */
    private void cargar(Player player, String[] args) {
        Optional<Company> company = plugin.mining().companies().getByMember(player.getUniqueId());
        if (company.isEmpty()) {
            msg(player, NamedTextColor.RED, "No perteneces a ninguna empresa.");
            return;
        }
        Optional<BlockDisplay> nearest = plugin.vehicles().nearestUsable(player, 6)
                .filter(root -> plugin.vehicles().ownerOf(root).map(company.get().getId()::equals).orElse(false));
        if (nearest.isEmpty()) {
            msg(player, NamedTextColor.RED, "No hay ningun vehiculo de tu empresa cerca.");
            return;
        }
        double maximo = company.get().getRawCoal();
        if (args.length >= 2) {
            try {
                maximo = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                msg(player, NamedTextColor.RED, "Cantidad invalida.");
                return;
            }
        }
        double usado = plugin.vehicles().refuelFromRawCoal(company.get(), nearest.get(), maximo);
        if (usado <= 0) {
            msg(player, NamedTextColor.RED, "No se pudo cargar: el tanque esta lleno, no usa carbon o la empresa no tiene carbon crudo.");
            return;
        }
        double tanque = plugin.vehicles().typeOf(nearest.get()).map(VehicleType::tanque).orElse(0.0);
        msg(player, NamedTextColor.GREEN, "Usaste " + Math.round(usado * 100.0) / 100.0 + " de carbon crudo. Combustible: "
                + (int) Math.ceil(plugin.vehicles().fuelOf(nearest.get())) + "/" + (int) tanque);
    }

    private void limpiar(Player player) {
        if (!player.hasPermission("minercorp.vehiculos.admin")) {
            noPerm(player);
            return;
        }
        int removed = plugin.vehicles().removeOrphanParts(player.getLocation(), 10)
                + plugin.legacy().removeOrphans(player.getLocation(), 10);
        msg(player, NamedTextColor.GREEN, "Se limpiaron " + removed + " restos de vehiculos rotos.");
    }

    /** /vehiculo dar &lt;jugador&gt; &lt;tipo&gt;: lo deja en el garaje del jugador, o de su empresa si el tipo es de empresa. */
    private boolean dar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("minercorp.vehiculos.admin")) return noPerm(sender);
        if (args.length < 3) {
            sender.sendMessage(Component.text("Uso: /vehiculo dar <jugador> <tipo>", NamedTextColor.YELLOW));
            return true;
        }
        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        Optional<VehicleType> type = plugin.types().get(args[2]);
        if (type.isEmpty()) {
            sender.sendMessage(Component.text("Ese tipo no existe.", NamedTextColor.RED));
            return true;
        }
        UUID owner = target.getUniqueId();
        if (type.get().dueno() == OwnerKind.EMPRESA) {
            Optional<Company> company = plugin.mining().companies().getByMember(owner);
            if (company.isEmpty()) {
                sender.sendMessage(Component.text("Ese vehiculo es de empresa y el jugador no tiene empresa.", NamedTextColor.RED));
                return true;
            }
            owner = company.get().getId();
        }
        plugin.service().giveToGarage(owner, type.get().id());
        sender.sendMessage(Component.text(type.get().nombre() + " dado a " + args[1] + ".", NamedTextColor.GREEN));
        return true;
    }

    private void ayuda(Player player) {
        msg(player, NamedTextColor.GOLD, "===== Vehiculos =====");
        for (String line : List.of(
                "/garaje - tus vehiculos y los de tu empresa: sacarlos y ver donde quedaron",
                "/vehiculo tienda - concesionario",
                "/vehiculo guardar - guarda el vehiculo cercano (o golpealo)",
                "/vehiculo cargar [cantidad] - combustible con carbon crudo de la empresa",
                "Click derecho: subirte. Con combustible en la mano: cargarlo. Shift + click derecho: carga")) {
            msg(player, NamedTextColor.GRAY, line);
        }
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(Component.text("No tenes permiso para hacer esto.", NamedTextColor.RED));
        return true;
    }

    private void msg(Player player, NamedTextColor color, String text) {
        player.sendMessage(Component.text(text, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("garaje")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("dar")) {
            for (VehicleType t : plugin.types().all()) if (t.id().startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(t.id());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("dar")) {
            return null; // nombres de jugadores
        }
        return out;
    }
}
