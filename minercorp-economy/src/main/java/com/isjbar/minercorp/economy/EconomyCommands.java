package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountInfo;
import com.isjbar.minercorp.economy.api.AccountType;
import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.economy.api.Reason;
import com.isjbar.minercorp.economy.api.Transaction;
import com.isjbar.minercorp.economy.api.TransactionRecord;
import com.isjbar.minercorp.economy.api.TransactionResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.isjbar.minercorp.economy.Messages.text;

/** /saldo, /pagar, /movimientos, /top, /billetera y /eco. */
final class EconomyCommands implements CommandExecutor, TabCompleter {

    static final String PERM_USE = "minercorp.economy.use";
    static final String PERM_PAGAR = "minercorp.economy.pagar";
    static final String PERM_VER_OTROS = "minercorp.economy.ver-otros";
    static final String PERM_ADMIN = "minercorp.economy.admin";

    private static final int POR_PAGINA_HISTORIAL = 8;
    private static final int POR_PAGINA_TOP = 10;
    private static final long CONFIRMACION_MS = 60_000;

    /** Un /pagar grande esperando el clic en [Confirmar]. */
    private record PagoPendiente(UUID destino, long centavos, long comision, long venceEn) {}

    private final EconomyPlugin plugin;
    private final Map<UUID, PagoPendiente> pendientes = new HashMap<>();

    EconomyCommands(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    private EconomyAPI eco() {
        return plugin.economy();
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "saldo" -> saldo(sender, args);
            case "pagar" -> pagar(sender, args);
            case "movimientos" -> movimientos(sender, args);
            case "top" -> top(sender, args);
            case "billetera" -> billetera(sender);
            case "eco" -> eco(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    // --------------------------------------------------------------------- /saldo

    private void saldo(CommandSender sender, String[] args) {
        // Compatibilidad con el viejo "/saldo dar <monto> [jugador]" de MinerCorp-Mining.
        if (args.length >= 2 && args[0].equalsIgnoreCase("dar") && sender.hasPermission(PERM_ADMIN)) {
            String[] resto = args.length >= 3
                    ? new String[]{"dar", String.join(" ", Arrays.copyOfRange(args, 2, args.length)), args[1]}
                    : sender instanceof Player p ? new String[]{"dar", p.getName(), args[1]} : new String[0];
            if (resto.length == 0) {
                msg().send(sender, "solo-jugadores");
            } else {
                eco(sender, resto);
            }
            return;
        }

        if (args.length > 0) {
            if (!sender.hasPermission(PERM_VER_OTROS) && !sender.hasPermission(PERM_ADMIN)) {
                msg().send(sender, "sin-permiso");
                return;
            }
            String nombre = String.join(" ", args);
            Optional<UUID> cuenta = resolve(nombre);
            if (cuenta.isEmpty()) {
                msg().send(sender, "cuenta-no-encontrada", text("nombre", nombre));
                return;
            }
            msg().send(sender, "saldo-otro", text("nombre", msg().accountName(cuenta.get())),
                    text("monto", eco().format(eco().getBalance(cuenta.get()))));
            return;
        }

        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        UUID id = player.getUniqueId();
        msg().send(player, "saldo-propio", text("monto", eco().format(eco().getBalance(id))));
        for (AccountInfo cuenta : eco().getAccountsOwnedBy(id)) {
            if (cuenta.id().equals(id)) continue;
            player.sendMessage(msg().raw("saldo-cuenta", text("nombre", cuenta.nameOr("Cuenta")),
                    text("monto", eco().format(cuenta.balance()))));
        }
        player.sendMessage(Component.text("   ")
                .append(boton("Movimientos", "/movimientos", "Ver tus ultimos movimientos"))
                .append(Component.text("  "))
                .append(boton("Billetera", "/billetera", "Abrir el menu de tu billetera"))
                .append(Component.text("  "))
                .append(boton("Ranking", "/top", "Los mas ricos del server")));
    }

    // --------------------------------------------------------------------- /pagar

    private void pagar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        if (!player.hasPermission(PERM_PAGAR)) {
            msg().send(player, "sin-permiso");
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("confirmar")) {
            PagoPendiente p = pendientes.remove(player.getUniqueId());
            if (p == null || p.venceEn() < System.currentTimeMillis()) {
                msg().send(player, "pago-sin-pendiente");
                return;
            }
            ejecutarPago(player, p.destino(), p.centavos(), p.comision());
            return;
        }
        if (args.length < 2) {
            player.sendMessage(msg().prefix().append(Component.text("Uso: /pagar <jugador o empresa> <monto>", NamedTextColor.GRAY)));
            return;
        }

        String montoTexto = args[args.length - 1];
        long centavos = msg().moneyFormat().parse(montoTexto);
        if (centavos <= 0) {
            msg().send(player, "monto-invalido", text("monto", montoTexto));
            return;
        }
        long minimo = Money.toCents(Math.max(0.01, plugin.getConfig().getDouble("pagos.minimo", 1.0)));
        if (centavos < minimo) {
            msg().send(player, "pago-minimo", text("monto", msg().money(minimo)));
            return;
        }

        String nombre = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1));
        Optional<UUID> destino = resolve(nombre);
        if (destino.isEmpty() || destino.get().equals(EconomyAPI.SERVER)) {
            msg().send(player, "cuenta-no-encontrada", text("nombre", nombre));
            return;
        }
        if (destino.get().equals(player.getUniqueId())) {
            msg().send(player, "pago-a-si-mismo");
            return;
        }

        double porcentaje = Math.max(0, plugin.getConfig().getDouble("pagos.comision-porcentaje", 0));
        long comision = Math.round(centavos * porcentaje / 100.0);

        double confirmarDesde = plugin.getConfig().getDouble("pagos.confirmar-desde", 0);
        if (confirmarDesde > 0 && centavos >= Money.toCents(confirmarDesde)) {
            pendientes.put(player.getUniqueId(), new PagoPendiente(destino.get(), centavos, comision,
                    System.currentTimeMillis() + CONFIRMACION_MS));
            Component boton = msg().raw("pago-boton")
                    .clickEvent(ClickEvent.runCommand("/pagar confirmar"))
                    .hoverEvent(HoverEvent.showText(Component.text("Clic para enviar el pago", NamedTextColor.GRAY)));
            msg().send(player, "pago-confirmar",
                    text("monto", msg().money(centavos)),
                    text("nombre", msg().accountName(destino.get())),
                    Messages.component("comision", textoComision(comision)),
                    Messages.component("boton", boton));
            return;
        }
        ejecutarPago(player, destino.get(), centavos, comision);
    }

    private void ejecutarPago(Player player, UUID destino, long centavos, long comision) {
        String de = player.getName();
        String para = msg().accountName(destino);
        Transaction.Builder tx = Transaction.builder(Reason.of(Reason.PAGO, "Pago de " + de + " a " + para))
                .move(player.getUniqueId(), destino, Money.toDouble(centavos));
        if (comision > 0) tx.move(player.getUniqueId(), EconomyAPI.SERVER, Money.toDouble(comision));

        TransactionResult r = eco().execute(tx.build());
        if (r.status() == TransactionResult.Status.INSUFFICIENT_FUNDS) {
            msg().send(player, "fondos-insuficientes",
                    text("saldo", eco().format(eco().getBalance(player.getUniqueId()))),
                    text("monto", msg().money(centavos + comision)));
            return;
        }
        if (!r.success()) {
            player.sendMessage(msg().prefix().append(Component.text(r.message(), NamedTextColor.RED)));
            return;
        }
        msg().send(player, "pago-enviado", text("monto", msg().money(centavos)), text("nombre", para),
                Messages.component("comision", textoComision(comision)));
        Player receptor = Bukkit.getPlayer(destino);
        if (receptor != null) {
            msg().send(receptor, "pago-recibido", text("nombre", de), text("monto", msg().money(centavos)));
        }
    }

    private Component textoComision(long comision) {
        return comision > 0 ? msg().raw("pago-comision", text("comision", msg().money(comision))) : Component.empty();
    }

    // --------------------------------------------------------------- /movimientos

    private void movimientos(CommandSender sender, String[] args) {
        int pagina = 1;
        String[] resto = args;
        if (args.length > 0 && esNumero(args[0])) {
            pagina = Math.max(1, Integer.parseInt(args[0]));
            resto = Arrays.copyOfRange(args, 1, args.length);
        }

        UUID cuenta;
        if (resto.length > 0) {
            String nombre = String.join(" ", resto);
            Optional<UUID> encontrada = resolve(nombre);
            if (encontrada.isEmpty()) {
                msg().send(sender, "cuenta-no-encontrada", text("nombre", nombre));
                return;
            }
            cuenta = encontrada.get();
            if (!puedeVer(sender, cuenta)) {
                msg().send(sender, "sin-permiso");
                return;
            }
        } else if (sender instanceof Player player) {
            cuenta = player.getUniqueId();
        } else {
            msg().send(sender, "solo-jugadores");
            return;
        }

        List<TransactionRecord> historial = eco().getHistory(cuenta, Integer.MAX_VALUE);
        String nombreCuenta = msg().accountName(cuenta);
        if (historial.isEmpty()) {
            msg().send(sender, "historial-vacio");
            return;
        }
        int paginas = (historial.size() + POR_PAGINA_HISTORIAL - 1) / POR_PAGINA_HISTORIAL;
        pagina = Math.min(pagina, paginas);

        sender.sendMessage(titulo("Movimientos de " + nombreCuenta, pagina, paginas));
        int desde = (pagina - 1) * POR_PAGINA_HISTORIAL;
        for (TransactionRecord r : historial.subList(desde, Math.min(historial.size(), desde + POR_PAGINA_HISTORIAL))) {
            sender.sendMessage(lineaMovimiento(r));
        }
        String sufijo = resto.length > 0 ? " " + String.join(" ", resto) : "";
        sender.sendMessage(navegacion("/movimientos ", sufijo, pagina, paginas));
    }

    private Component lineaMovimiento(TransactionRecord r) {
        boolean ingreso = r.isIncome();
        Component hover = Component.text()
                .append(Component.text(r.reason().categoryLabel(), NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text(r.reason().describe(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Con: ", NamedTextColor.GRAY)).append(Component.text(msg().accountName(r.counterparty()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Saldo despues: ", NamedTextColor.GRAY)).append(Component.text(eco().format(r.balanceAfter()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Fecha: ", NamedTextColor.GRAY)).append(Component.text(Messages.date(r.timestamp()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(r.transactionId(), NamedTextColor.DARK_GRAY))
                .build();
        return Component.text()
                .append(Component.text(" " + (ingreso ? "+" : "-") + eco().format(Math.abs(r.amount())),
                        ingreso ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("  " + r.reason().describe(), NamedTextColor.GRAY))
                .append(Component.text("  " + Messages.ago(r.timestamp()), NamedTextColor.DARK_GRAY))
                .hoverEvent(HoverEvent.showText(hover))
                .build();
    }

    // ----------------------------------------------------------------------- /top

    private void top(CommandSender sender, String[] args) {
        AccountType tipo = null;
        int pagina = 1;
        for (String a : args) {
            switch (a.toLowerCase(Locale.ROOT)) {
                case "jugadores" -> tipo = AccountType.PLAYER;
                case "empresas" -> tipo = AccountType.COMPANY;
                case "todos" -> tipo = null;
                default -> {
                    if (esNumero(a)) pagina = Math.max(1, Integer.parseInt(a));
                }
            }
        }

        List<AccountInfo> ranking = eco().getTop(tipo, 100);
        if (ranking.isEmpty()) {
            msg().send(sender, "top-vacio");
            return;
        }
        int paginas = (ranking.size() + POR_PAGINA_TOP - 1) / POR_PAGINA_TOP;
        pagina = Math.min(pagina, paginas);
        String titulo = tipo == AccountType.PLAYER ? "Jugadores mas ricos"
                : tipo == AccountType.COMPANY ? "Empresas mas ricas" : "Ranking de fortunas";
        sender.sendMessage(titulo(titulo, pagina, paginas));

        UUID propio = sender instanceof Player p ? p.getUniqueId() : null;
        int desde = (pagina - 1) * POR_PAGINA_TOP;
        for (int i = desde; i < Math.min(ranking.size(), desde + POR_PAGINA_TOP); i++) {
            AccountInfo a = ranking.get(i);
            boolean esMia = propio != null && (a.id().equals(propio) || propio.equals(a.owner()));
            NamedTextColor colorPuesto = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.WHITE;
                case 2 -> NamedTextColor.RED;
                default -> NamedTextColor.GRAY;
            };
            Component linea = Component.text(String.format(" #%d ", i + 1), colorPuesto, TextDecoration.BOLD)
                    .append(Component.text(a.type() == AccountType.COMPANY ? "[Empresa] " : "", NamedTextColor.DARK_AQUA)
                            .decoration(TextDecoration.BOLD, false))
                    .append(Component.text(a.nameOr(msg().accountName(a.id())), esMia ? NamedTextColor.YELLOW : NamedTextColor.WHITE)
                            .decoration(TextDecoration.BOLD, false))
                    .append(Component.text("  " + eco().format(a.balance()), NamedTextColor.GREEN)
                            .decoration(TextDecoration.BOLD, false));
            sender.sendMessage(linea);
        }
        String sufijo = tipo == null ? "" : tipo == AccountType.PLAYER ? " jugadores" : " empresas";
        sender.sendMessage(navegacion("/top ", sufijo, pagina, paginas)
                .append(Component.text("   "))
                .append(boton("Jugadores", "/top jugadores", "Solo jugadores"))
                .append(Component.text(" "))
                .append(boton("Empresas", "/top empresas", "Solo empresas")));
    }

    // ----------------------------------------------------------------- /billetera

    private void billetera(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg().send(sender, "solo-jugadores");
            return;
        }
        WalletMenu.open(plugin, player, player.getUniqueId());
    }

    // ----------------------------------------------------------------------- /eco

    private void eco(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            msg().send(sender, "sin-permiso");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "dar", "quitar", "fijar" -> ecoMonto(sender, sub, args);
            case "ver" -> ecoVer(sender, args);
            case "stats" -> ecoStats(sender);
            case "recargar" -> {
                plugin.reloadConfig();
                msg().send(sender, "admin-recargado");
            }
            default -> {
                sender.sendMessage(msg().prefix().append(Component.text("Comandos de admin:", NamedTextColor.GOLD)));
                for (String l : List.of(
                        "/eco dar <cuenta> <monto>", "/eco quitar <cuenta> <monto>", "/eco fijar <cuenta> <monto>",
                        "/eco ver <cuenta>", "/movimientos [pagina] <cuenta>", "/eco stats", "/eco recargar")) {
                    sender.sendMessage(Component.text(" " + l, NamedTextColor.GRAY));
                }
            }
        }
    }

    private void ecoMonto(CommandSender sender, String sub, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg().prefix().append(Component.text("Uso: /eco " + sub + " <cuenta> <monto>", NamedTextColor.GRAY)));
            return;
        }
        String montoTexto = args[args.length - 1];
        long centavos = sub.equals("fijar") && montoTexto.equals("0") ? 0 : msg().moneyFormat().parse(montoTexto);
        if (centavos < 0) {
            msg().send(sender, "monto-invalido", text("monto", montoTexto));
            return;
        }
        String nombre = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        Optional<UUID> cuenta = resolve(nombre);
        if (cuenta.isEmpty() || cuenta.get().equals(EconomyAPI.SERVER)) {
            msg().send(sender, "cuenta-no-encontrada", text("nombre", nombre));
            return;
        }
        UUID id = cuenta.get();
        Reason motivo = Reason.of(Reason.ADMIN, "Ajuste del staff (" + sender.getName() + ")");

        long delta = switch (sub) {
            case "dar" -> centavos;
            case "quitar" -> -centavos;
            default -> centavos - Money.toCents(eco().getBalance(id));
        };
        TransactionResult r = delta >= 0
                ? eco().deposit(id, Money.toDouble(delta), motivo)
                : eco().withdraw(id, Money.toDouble(-delta), motivo);
        if (!r.success()) {
            sender.sendMessage(msg().prefix().append(Component.text(r.message(), NamedTextColor.RED)));
            return;
        }
        String accion = switch (sub) {
            case "dar" -> "Diste";
            case "quitar" -> "Quitaste";
            default -> "Fijaste";
        };
        msg().send(sender, "admin-hecho", text("accion", accion), text("monto", msg().money(centavos)),
                text("nombre", msg().accountName(id)), text("saldo", eco().format(eco().getBalance(id))));
    }

    private void ecoVer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg().prefix().append(Component.text("Uso: /eco ver <cuenta>", NamedTextColor.GRAY)));
            return;
        }
        String nombre = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Optional<AccountInfo> info = resolve(nombre).flatMap(eco()::getAccount);
        if (info.isEmpty()) {
            msg().send(sender, "cuenta-no-encontrada", text("nombre", nombre));
            return;
        }
        AccountInfo a = info.get();
        sender.sendMessage(titulo("Cuenta " + msg().accountName(a.id()), 0, 0));
        sender.sendMessage(dato("Tipo", switch (a.type()) {
            case PLAYER -> "Jugador";
            case COMPANY -> "Empresa";
            case SYSTEM -> "Sistema";
            case OTHER -> "Otra";
        }));
        sender.sendMessage(dato("Saldo", eco().format(a.balance())));
        sender.sendMessage(dato("Dueno", a.owner() == null ? "-" : msg().accountName(a.owner())));
        sender.sendMessage(dato("Creada", Messages.date(a.createdAt())));
        sender.sendMessage(dato("Id", a.id().toString()));
    }

    private void ecoStats(CommandSender sender) {
        Ledger l = plugin.ledger();
        sender.sendMessage(titulo("Economia del server", 0, 0));
        sender.sendMessage(dato("Cuentas", String.valueOf(l.accountCount())));
        sender.sendMessage(dato("En circulacion", msg().money(l.circulating())));
        sender.sendMessage(dato("Creado por el sistema", msg().money(l.minted())));
        sender.sendMessage(dato("Retirado por el sistema", msg().money(l.burned())));
    }

    // ------------------------------------------------------------------ utilidades

    /** Jugador conectado, despues cualquier cuenta por nombre (jugadores y empresas), despues jugadores offline. */
    Optional<UUID> resolve(String nombre) {
        if (nombre == null || nombre.isBlank()) return Optional.empty();
        Player online = Bukkit.getPlayerExact(nombre);
        if (online != null) return Optional.of(online.getUniqueId());
        Optional<AccountInfo> cuenta = eco().findByName(nombre);
        if (cuenta.isPresent()) return Optional.of(cuenta.get().id());
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(nombre);
        return offline == null ? Optional.empty() : Optional.of(offline.getUniqueId());
    }

    private boolean puedeVer(CommandSender sender, UUID cuenta) {
        if (sender.hasPermission(PERM_ADMIN)) return true;
        if (!(sender instanceof Player player)) return false;
        UUID id = player.getUniqueId();
        return cuenta.equals(id) || eco().getAccount(cuenta).map(a -> id.equals(a.owner())).orElse(false);
    }

    private static boolean esNumero(String s) {
        return !s.isEmpty() && s.length() < 6 && s.chars().allMatch(Character::isDigit);
    }

    private Component titulo(String texto, int pagina, int paginas) {
        Component t = Component.text("──── ", NamedTextColor.DARK_GRAY)
                .append(Component.text(texto, NamedTextColor.GOLD, TextDecoration.BOLD));
        if (paginas > 1) t = t.append(Component.text(" (" + pagina + "/" + paginas + ")", NamedTextColor.GRAY));
        return t.append(Component.text(" ────", NamedTextColor.DARK_GRAY));
    }

    private static Component dato(String clave, String valor) {
        return Component.text(" " + clave + ": ", NamedTextColor.GRAY).append(Component.text(valor, NamedTextColor.WHITE));
    }

    private static Component boton(String texto, String comando, String hover) {
        return Component.text("[" + texto + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(comando))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    private static Component navegacion(String comando, String sufijo, int pagina, int paginas) {
        Component anterior = pagina > 1
                ? boton("« Anterior", comando + (pagina - 1) + sufijo, "Pagina " + (pagina - 1))
                : Component.text("« Anterior", NamedTextColor.DARK_GRAY);
        Component siguiente = pagina < paginas
                ? boton("Siguiente »", comando + (pagina + 1) + sufijo, "Pagina " + (pagina + 1))
                : Component.text("Siguiente »", NamedTextColor.DARK_GRAY);
        return Component.text(" ").append(anterior).append(Component.text("  ")).append(siguiente);
    }

    // -------------------------------------------------------------- autocompletado

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        String actual = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> opciones = switch (cmd) {
            case "pagar" -> args.length == 1 ? nombres(true) : List.of("100", "1k");
            case "saldo" -> args.length == 1 && (sender.hasPermission(PERM_VER_OTROS) || sender.hasPermission(PERM_ADMIN))
                    ? nombres(true) : List.of();
            case "top" -> args.length == 1 ? List.of("jugadores", "empresas", "todos") : List.of();
            case "movimientos" -> args.length == 2 && sender.hasPermission(PERM_ADMIN) ? nombres(true) : List.of();
            case "eco" -> !sender.hasPermission(PERM_ADMIN) ? List.of()
                    : args.length == 1 ? List.of("dar", "quitar", "fijar", "ver", "stats", "recargar")
                    : args.length == 2 ? nombres(true) : List.of();
            default -> List.of();
        };
        List<String> filtradas = new ArrayList<>();
        for (String o : opciones) {
            if (o.toLowerCase(Locale.ROOT).startsWith(actual)) filtradas.add(o);
        }
        return filtradas;
    }

    private List<String> nombres(boolean conEmpresas) {
        Stream<String> jugadores = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        if (!conEmpresas) return jugadores.toList();
        // Los nombres de empresa con espacios no se autocompletan bien: se ofrecen solo los de una palabra.
        Stream<String> empresas = plugin.ledger().names(AccountType.COMPANY).stream().filter(n -> !n.contains(" "));
        return Stream.concat(jugadores, empresas).distinct().toList();
    }
}
