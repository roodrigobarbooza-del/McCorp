package com.isjbar.minercorp.gransede;

import com.isjbar.minercorp.gransede.tienda.Tienda;
import com.isjbar.minercorp.gransede.zona.Zona;
import com.isjbar.minercorp.gransede.zona.ZonaProteccionListener;
import com.isjbar.minercorp.gransede.zona.ZonaTipo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /gransede                         -> info y como llegar
 * /gransede ir                      -> teletransporte (si tp-permitido)
 * Admin:
 * /gransede pos1 | pos2             -> esquinas para la proxima zona
 * /gransede zona crear <nombre> <SEDE|MINA|BOSQUE>
 * /gransede zona borrar <nombre> | lista | spawn
 * /gransede npc crear <tienda> | borrar
 * /gransede tienda <id>             -> abrir una tienda sin vendedor (probar)
 * /gransede recargar
 */
public class GranSedeCommand implements TabExecutor {

    private final GranSedePlugin plugin;
    private final Map<UUID, Location[]> seleccion = new HashMap<>();

    public GranSedeCommand(GranSedePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission(ZonaProteccionListener.PERMISO_ADMIN);

        switch (sub) {
            case "" -> info(sender, admin);
            case "ir" -> ir(sender);
            case "pos1", "pos2" -> {
                if (!admin(sender, admin) || !(sender instanceof Player p)) return true;
                Location[] sel = seleccion.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
                sel[sub.equals("pos1") ? 0 : 1] = p.getLocation().toBlockLocation();
                ok(sender, "Marcada la " + sub + " en " + coords(p.getLocation()) + ".");
            }
            case "zona" -> {
                if (admin(sender, admin)) zona(sender, args);
            }
            case "npc" -> {
                if (admin(sender, admin)) npc(sender, args);
            }
            case "tienda" -> {
                if (!admin(sender, admin) || !(sender instanceof Player p)) return true;
                if (args.length < 2) return error(sender, "Uso: /gransede tienda <id>");
                plugin.tiendas().tienda(args[1]).ifPresentOrElse(t -> plugin.menu().abrirLista(p, t, 0),
                        () -> error(sender, "No existe la tienda " + args[1] + "."));
            }
            case "recargar" -> {
                if (!admin(sender, admin)) return true;
                plugin.recargar();
                ok(sender, "Config de la Gran Sede recargado.");
            }
            default -> error(sender, "Subcomando desconocido. Usa /gransede.");
        }
        return true;
    }

    private void info(CommandSender sender, boolean admin) {
        sender.sendMessage(Component.text("La Gran Sede", NamedTextColor.GOLD));
        plugin.zonas().spawn().ifPresentOrElse(
                s -> sender.sendMessage(Component.text("Queda en " + s.getWorld().getName() + " " + coords(s) + ".", NamedTextColor.GRAY)),
                () -> sender.sendMessage(Component.text("Todavia no se marco donde queda.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Trabaja en la Mina o el Bosque para tus primeros fondos, y compra vehiculos y taladros con los vendedores.", NamedTextColor.GRAY));
        if (plugin.getConfig().getBoolean("tp-permitido", true)) {
            sender.sendMessage(Component.text("/gransede ir para ir.", NamedTextColor.YELLOW));
        }
        if (admin) {
            sender.sendMessage(Component.text("Admin: pos1, pos2, zona crear|borrar|lista|spawn, npc crear|borrar, tienda, recargar", NamedTextColor.DARK_GRAY));
        }
    }

    private void ir(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        if (!plugin.getConfig().getBoolean("tp-permitido", true) || !p.hasPermission("minercorp.gransede.ir")) {
            error(sender, "No se puede viajar a la Gran Sede con comando.");
            return;
        }
        plugin.zonas().spawn().ifPresentOrElse(s -> {
            p.teleport(s);
            ok(sender, "Bienvenido a la Gran Sede.");
        }, () -> error(sender, "Todavia no se marco donde queda la Gran Sede."));
    }

    private void zona(CommandSender sender, String[] args) {
        String accion = args.length < 2 ? "lista" : args[1].toLowerCase(Locale.ROOT);
        switch (accion) {
            case "lista" -> {
                List<Zona> todas = plugin.zonas().all();
                if (todas.isEmpty()) {
                    sender.sendMessage(Component.text("No hay zonas. Marca pos1 y pos2 y usa /gransede zona crear <nombre> <SEDE|MINA|BOSQUE>.", NamedTextColor.GRAY));
                }
                for (Zona z : todas) {
                    sender.sendMessage(Component.text(z.nombre() + " (" + z.tipo() + ") " + z.mundo() + " "
                            + z.minX() + "," + z.minY() + "," + z.minZ() + " a " + z.maxX() + "," + z.maxY() + "," + z.maxZ(), NamedTextColor.GRAY));
                }
            }
            case "crear" -> {
                if (!(sender instanceof Player p)) return;
                if (args.length < 4) {
                    error(sender, "Uso: /gransede zona crear <nombre> <SEDE|MINA|BOSQUE>");
                    return;
                }
                Location[] sel = seleccion.get(p.getUniqueId());
                if (sel == null || sel[0] == null || sel[1] == null) {
                    error(sender, "Primero marca las dos esquinas con /gransede pos1 y /gransede pos2.");
                    return;
                }
                if (!sel[0].getWorld().equals(sel[1].getWorld())) {
                    error(sender, "Las dos esquinas tienen que estar en el mismo mundo.");
                    return;
                }
                ZonaTipo tipo;
                try {
                    tipo = ZonaTipo.valueOf(args[3].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    error(sender, "Tipo invalido. Usa SEDE, MINA o BOSQUE.");
                    return;
                }
                Zona z = Zona.entre(args[2].toLowerCase(Locale.ROOT), tipo, sel[0], sel[1]);
                plugin.zonas().put(z);
                ok(sender, "Zona " + z.nombre() + " (" + tipo + ") guardada, " + z.volumen() + " bloques.");
            }
            case "borrar" -> {
                if (args.length < 3) {
                    error(sender, "Uso: /gransede zona borrar <nombre>");
                } else if (plugin.zonas().remove(args[2])) {
                    ok(sender, "Zona " + args[2] + " borrada.");
                } else {
                    error(sender, "No existe la zona " + args[2] + ".");
                }
            }
            case "spawn" -> {
                if (!(sender instanceof Player p)) return;
                plugin.zonas().setSpawn(p.getLocation());
                ok(sender, "Punto de llegada de la Gran Sede marcado aca.");
            }
            default -> error(sender, "Uso: /gransede zona <crear|borrar|lista|spawn>");
        }
    }

    private void npc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        String accion = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        if (accion.equals("crear")) {
            if (args.length < 3) {
                error(sender, "Uso: /gransede npc crear <tienda>");
                return;
            }
            Optional<Tienda> t = plugin.tiendas().tienda(args[2]);
            if (t.isEmpty()) {
                error(sender, "No existe la tienda " + args[2] + ". Tiendas: " + String.join(", ", idsTiendas()));
                return;
            }
            plugin.vendedores().crear(t.get(), p.getLocation());
            ok(sender, "Vendedor de " + t.get().id() + " puesto aca.");
        } else if (accion.equals("borrar")) {
            Entity mirando = p.getTargetEntity(6);
            if (mirando == null || !plugin.vendedores().esVendedor(mirando)) {
                error(sender, "Mira a un vendedor de la Gran Sede (a menos de 6 bloques).");
                return;
            }
            mirando.remove();
            ok(sender, "Vendedor borrado.");
        } else {
            error(sender, "Uso: /gransede npc <crear <tienda>|borrar>");
        }
    }

    private List<String> idsTiendas() {
        List<String> ids = new ArrayList<>();
        for (Tienda t : plugin.tiendas().tiendas()) ids.add(t.id());
        return ids;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ZonaProteccionListener.PERMISO_ADMIN);
        List<String> opciones = switch (args.length) {
            case 1 -> admin ? List.of("ir", "pos1", "pos2", "zona", "npc", "tienda", "recargar") : List.of("ir");
            case 2 -> !admin ? List.of() : switch (args[0].toLowerCase(Locale.ROOT)) {
                case "zona" -> List.of("crear", "borrar", "lista", "spawn");
                case "npc" -> List.of("crear", "borrar");
                case "tienda" -> idsTiendas();
                default -> List.of();
            };
            case 3 -> !admin ? List.of() : switch (args[0].toLowerCase(Locale.ROOT) + " " + args[1].toLowerCase(Locale.ROOT)) {
                case "npc crear" -> idsTiendas();
                case "zona borrar" -> plugin.zonas().all().stream().map(Zona::nombre).toList();
                default -> List.of();
            };
            case 4 -> admin && args[0].equalsIgnoreCase("zona") && args[1].equalsIgnoreCase("crear")
                    ? Arrays.stream(ZonaTipo.values()).map(Enum::name).toList() : List.of();
            default -> List.of();
        };
        String ultimo = args[args.length - 1].toLowerCase(Locale.ROOT);
        return opciones.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(ultimo)).toList();
    }

    private boolean admin(CommandSender sender, boolean admin) {
        if (!admin) error(sender, "No tenes permiso para eso.");
        return admin;
    }

    private static void ok(CommandSender s, String msg) {
        s.sendMessage(Component.text(msg, NamedTextColor.GREEN));
    }

    private static boolean error(CommandSender s, String msg) {
        s.sendMessage(Component.text(msg, NamedTextColor.RED));
        return true;
    }

    private static String coords(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }
}
