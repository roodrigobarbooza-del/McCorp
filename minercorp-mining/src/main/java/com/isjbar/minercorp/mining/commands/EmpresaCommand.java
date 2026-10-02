package com.isjbar.minercorp.mining.commands;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.mining.company.CompanyManager;
import com.isjbar.minercorp.mining.company.MinionData;
import com.isjbar.minercorp.mining.gui.MinerCorpMenu;
import com.isjbar.minercorp.mining.minion.MinionManager;
import com.isjbar.minercorp.mining.sede.Obra;
import com.isjbar.minercorp.mining.sede.SedeBlueprint;
import com.isjbar.minercorp.mining.vehicle.DrillTier;
import com.isjbar.minercorp.mining.vehicle.DrillVehicleManager;
import com.isjbar.minercorp.territory.api.VeinSnapshot;
import com.isjbar.minercorp.territory.util.ChunkKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class EmpresaCommand implements CommandExecutor, TabCompleter {

    private final MiningPlugin plugin;
    /** Invitaciones pendientes: jugador invitado -> id de empresa. No se persisten (son efimeras). */
    private final Map<UUID, UUID> invites = new HashMap<>();

    private static final List<String> SUBCOMANDOS = List.of(
            "crear", "info", "reclamar", "liberar", "invitar", "aceptar", "rechazar",
            "expulsar", "salir", "disolver", "taladro", "minion", "refinar", "vender",
            "depositar", "retirar", "obra", "menu", "ayuda"
    );

    public EmpresaCommand(MiningPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un jugador puede usar este comando.");
            return true;
        }

        if (args.length == 0) {
            ayuda(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "crear" -> crear(player, rest);
            case "info" -> info(player, rest);
            case "reclamar" -> reclamar(player);
            case "liberar" -> liberar(player);
            case "invitar" -> invitar(player, rest);
            case "aceptar" -> aceptar(player);
            case "rechazar" -> rechazar(player);
            case "expulsar" -> expulsar(player, rest);
            case "salir" -> salir(player);
            case "disolver" -> disolver(player);
            case "taladro" -> taladro(player, rest);
            case "minion" -> minion(player, rest);
            case "refinar" -> refinar(player, rest);
            case "vender" -> vender(player, rest);
            case "depositar" -> depositar(player, rest);
            case "retirar" -> retirar(player, rest);
            case "obra" -> obra(player, rest);
            case "menu" -> MinerCorpMenu.open(plugin, player);
            default -> ayuda(player);
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Subcomandos
    // ---------------------------------------------------------------

    private void crear(Player player, String[] args) {
        if (args.length < 1) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa crear <nombre>");
            return;
        }
        if (plugin.companies().getByMember(player.getUniqueId()).isPresent()) {
            msg(player, NamedTextColor.RED, "Ya perteneces a una empresa.");
            return;
        }
        String nombre = String.join(" ", args);
        if (plugin.companies().getByName(nombre).isPresent()) {
            msg(player, NamedTextColor.RED, "Ya existe una empresa con ese nombre.");
            return;
        }

        double costo = plugin.getConfig().getDouble("economia.costo-fundar-empresa", 50.0);
        if (!plugin.economy().withdraw(player.getUniqueId(), costo)) {
            msg(player, NamedTextColor.RED, "Te faltan fondos. Fundar una empresa cuesta " + costo + ".");
            return;
        }

        Company company = plugin.companies().create(nombre, player.getUniqueId());
        msg(player, NamedTextColor.GREEN, "Fundaste la empresa minera '" + company.getName() + "'. Usa /empresa reclamar parado sobre una veta de carbon"
                + " y despues levanta la sede de tu empresa.");
    }

    private void info(Player player, String[] args) {
        Optional<Company> target;
        if (args.length > 0) {
            target = plugin.companies().getByName(String.join(" ", args));
        } else {
            target = plugin.companies().getByMember(player.getUniqueId());
        }
        if (target.isEmpty()) {
            msg(player, NamedTextColor.RED, "No se encontro esa empresa.");
            return;
        }
        Company c = target.get();
        double xpRequerida = c.getLevel() >= plugin.levels().maxNivel() ? -1 : plugin.levels().xpRequerida(c.getLevel());
        List<ChunkKey> claims = plugin.territory().getClaims(c.getId());

        player.sendMessage(Component.text("===== " + c.getName() + " =====", NamedTextColor.GOLD));
        player.sendMessage(linea("Dueno", nombreDe(c.getOwner())));
        player.sendMessage(linea("Colaboradores", c.getCollaborators().isEmpty() ? "ninguno" :
                c.getCollaborators().stream().map(this::nombreDe).collect(Collectors.joining(", "))));
        player.sendMessage(linea("Nivel", c.getLevel() + (xpRequerida < 0 ? " (maximo)" : " (xp " + round(c.getXp()) + "/" + round(xpRequerida) + ")")));
        player.sendMessage(linea("Balance", round(plugin.economy().getBalance(c.getId())) + ""));
        player.sendMessage(linea("Carbon crudo", round(c.getRawCoal()) + ""));
        player.sendMessage(linea("Carbon refinado", round(c.getRefinedCoal()) + ""));
        player.sendMessage(linea("Territorios", claims.size() + " / " + plugin.levels().chunksPermitidos(c.getLevel())));
        player.sendMessage(linea("Minions", c.getMinions().size() + " / " + plugin.levels().minionsPermitidos(c.getLevel())));

        for (ChunkKey key : claims) {
            resolveChunk(key).flatMap(plugin.territory()::getVein).ifPresent(vein ->
                    player.sendMessage(Component.text("  - " + key + ": veta al " + round(vein.porcentaje()) + "%"
                            + (vein.agotada() ? " (agotada)" : ""), NamedTextColor.GRAY)));
        }
    }

    private void reclamar(Player player) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwnerOrCollab(player, company)) return;

        double costo = plugin.getConfig().getDouble("economia.costo-reclamar-chunk", 100.0);
        Chunk chunk = player.getLocation().getChunk();

        if (plugin.economy().getBalance(company.getId()) < costo) {
            msg(player, NamedTextColor.RED, "La empresa necesita " + costo + " para reclamar este territorio.");
            return;
        }

        CompanyManager.ClaimOutcome result = plugin.companies().claim(company, chunk);
        switch (result) {
            case OK -> {
                plugin.economy().withdraw(company.getId(), costo);
                VeinSnapshot vein = plugin.territory().getVein(chunk).orElseThrow();
                msg(player, NamedTextColor.GREEN, "Territorio reclamado para " + company.getName()
                        + ". Se detectaron " + vein.bloquesDetectados() + " bloques de carbon (reserva: " + round(vein.reservaMaxima()) + ").");
                if (plugin.obras().darPlanoSiCorresponde(player, company)) {
                    msg(player, NamedTextColor.GOLD, "Recibiste el Plano de obra: sostenlo y haz clic derecho donde quieras levantar la sede.");
                }
            }
            case YA_RECLAMADO -> msg(player, NamedTextColor.RED, "Este chunk ya esta reclamado.");
            case SIN_VETA -> msg(player, NamedTextColor.RED, "No hay ninguna veta de carbon en este chunk. Busca otra zona.");
            case LIMITE_NIVEL -> msg(player, NamedTextColor.RED, "Tu empresa ya alcanzo el limite de territorios para su nivel ("
                    + plugin.levels().chunksPermitidos(company.getLevel()) + "). Sube de nivel para reclamar mas.");
        }
    }

    private void liberar(Player player) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwnerOrCollab(player, company)) return;

        Chunk chunk = player.getLocation().getChunk();
        Optional<UUID> owner = plugin.territory().getOwner(chunk);
        if (owner.isEmpty() || !owner.get().equals(company.getId())) {
            msg(player, NamedTextColor.RED, "Este chunk no pertenece a tu empresa.");
            return;
        }
        plugin.companies().unclaim(company, chunk);
        msg(player, NamedTextColor.GREEN, "Territorio liberado.");
    }

    private void invitar(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwner(player, company)) return;
        if (args.length < 1) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa invitar <jugador>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            msg(player, NamedTextColor.RED, "Ese jugador no esta conectado.");
            return;
        }
        if (company.isMember(target.getUniqueId())) {
            msg(player, NamedTextColor.RED, "Ese jugador ya es parte de la empresa.");
            return;
        }
        invites.put(target.getUniqueId(), company.getId());
        msg(player, NamedTextColor.GREEN, "Invitacion enviada a " + target.getName() + ".");
        msg(target, NamedTextColor.AQUA, player.getName() + " te invito a colaborar en '" + company.getName()
                + "'. Usa /empresa aceptar o /empresa rechazar.");
    }

    private void aceptar(Player player) {
        UUID companyId = invites.remove(player.getUniqueId());
        if (companyId == null) {
            msg(player, NamedTextColor.RED, "No tienes ninguna invitacion pendiente.");
            return;
        }
        if (plugin.companies().getByMember(player.getUniqueId()).isPresent()) {
            msg(player, NamedTextColor.RED, "Ya perteneces a una empresa.");
            return;
        }
        Optional<Company> companyOpt = plugin.companies().getById(companyId);
        if (companyOpt.isEmpty()) {
            msg(player, NamedTextColor.RED, "Esa empresa ya no existe.");
            return;
        }
        Company company = companyOpt.get();
        plugin.companies().addCollaborator(company, player.getUniqueId());
        msg(player, NamedTextColor.GREEN, "Ahora colaboras en '" + company.getName() + "'.");
        Player owner = Bukkit.getPlayer(company.getOwner());
        if (owner != null) msg(owner, NamedTextColor.AQUA, player.getName() + " acepto unirse a tu empresa.");
    }

    private void rechazar(Player player) {
        if (invites.remove(player.getUniqueId()) != null) {
            msg(player, NamedTextColor.GREEN, "Invitacion rechazada.");
        } else {
            msg(player, NamedTextColor.RED, "No tienes ninguna invitacion pendiente.");
        }
    }

    private void expulsar(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwner(player, company)) return;
        if (args.length < 1) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa expulsar <jugador>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!company.getCollaborators().contains(target.getUniqueId())) {
            msg(player, NamedTextColor.RED, "Ese jugador no es colaborador de tu empresa.");
            return;
        }
        plugin.companies().removeCollaborator(company, target.getUniqueId());
        msg(player, NamedTextColor.GREEN, "Expulsaste a " + args[0] + " de la empresa.");
    }

    private void salir(Player player) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (company.getOwner().equals(player.getUniqueId())) {
            msg(player, NamedTextColor.RED, "Eres el dueno, no puedes salir. Usa /empresa disolver.");
            return;
        }
        plugin.companies().removeCollaborator(company, player.getUniqueId());
        msg(player, NamedTextColor.GREEN, "Dejaste la empresa '" + company.getName() + "'.");
    }

    private void disolver(Player player) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwner(player, company)) return;
        plugin.companies().disband(company);
        msg(player, NamedTextColor.GREEN, "Disolviste la empresa '" + company.getName() + "'.");
    }

    private void taladro(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwnerOrCollab(player, company)) return;

        if (args.length < 1) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa taladro <comprar <tier>|cargar [cantidad]|quitar>");
            return;
        }

        if (args[0].equalsIgnoreCase("quitar")) {
            int huerfanos = plugin.vehicles().removeOrphanParts(player.getLocation(), 8);
            Optional<BlockDisplay> nearest = plugin.vehicles().nearestOwned(company, player.getLocation(), 5);
            if (nearest.isEmpty()) {
                if (huerfanos > 0) {
                    msg(player, NamedTextColor.GREEN, "Se limpiaron " + huerfanos + " restos de taladros rotos.");
                } else {
                    msg(player, NamedTextColor.RED, "No hay ningun taladro de tu empresa cerca.");
                }
                return;
            }
            plugin.vehicles().removeVehicle(nearest.get());
            msg(player, NamedTextColor.GREEN, "Taladro removido.");
            return;
        }

        if (args[0].equalsIgnoreCase("cargar")) {
            Optional<BlockDisplay> nearest = plugin.vehicles().nearestOwned(company, player.getLocation(), 5);
            if (nearest.isEmpty()) {
                msg(player, NamedTextColor.RED, "No hay ningun taladro de tu empresa cerca.");
                return;
            }
            double maximo = company.getRawCoal();
            if (args.length >= 2) {
                try {
                    maximo = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    msg(player, NamedTextColor.RED, "Cantidad invalida.");
                    return;
                }
            }
            double usado = plugin.vehicles().refuelFromRawCoal(company, nearest.get(), maximo);
            if (usado <= 0) {
                msg(player, NamedTextColor.RED, "No se pudo cargar: el tanque esta lleno o la empresa no tiene carbon crudo.");
                return;
            }
            DrillTier tier = plugin.vehicles().tierOf(nearest.get());
            msg(player, NamedTextColor.GREEN, "Usaste " + Math.round(usado * 100.0) / 100.0 + " de carbon crudo. Combustible: "
                    + (int) Math.ceil(plugin.vehicles().fuelOf(nearest.get())) + "/" + (int) tier.combustible());
            return;
        }

        if (!args[0].equalsIgnoreCase("comprar") || args.length < 2) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa taladro <comprar <tier>|cargar [cantidad]|quitar>");
            return;
        }
        int tier;
        try {
            tier = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            msg(player, NamedTextColor.RED, "Tier invalido.");
            return;
        }
        int maxTier = plugin.levels().tierMaximoDeTaladro(company.getLevel());
        if (tier < 1 || tier > maxTier) {
            msg(player, NamedTextColor.RED, "Tu empresa (nivel " + company.getLevel() + ") solo puede comprar hasta el tier " + maxTier + ".");
            return;
        }

        if (!plugin.obras().sedeLista(company)) {
            msg(player, NamedTextColor.RED, "Primero termina la obra de la sede de tu empresa (/empresa obra).");
            return;
        }
        DrillVehicleManager.PlacementResult result = plugin.vehicles().spawn(company, tier, player.getLocation());
        switch (result) {
            case OK -> msg(player, NamedTextColor.GREEN, "Taladro comprado y colocado. Cargale carbon (click derecho con carbon o /empresa taladro cargar) y subite con click derecho.");
            case TIER_INVALIDO -> msg(player, NamedTextColor.RED, "Ese tier no existe.");
            case FUERA_DE_TERRITORIO -> msg(player, NamedTextColor.RED, "Debes estar parado dentro de un territorio reclamado por tu empresa.");
            case SIN_SALDO -> msg(player, NamedTextColor.RED, "La empresa no tiene saldo suficiente para ese taladro.");
        }
    }

    private void minion(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwnerOrCollab(player, company)) return;

        if (args.length < 1) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa minion <colocar|quitar|lista>");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "colocar" -> {
                if (!plugin.obras().sedeLista(company)) {
                    msg(player, NamedTextColor.RED, "Primero termina la obra de la sede de tu empresa (/empresa obra).");
                    return;
                }
                MinionManager.MinionPlacement result = plugin.minions().place(company, player.getLocation());
                switch (result) {
                    case OK -> msg(player, NamedTextColor.GREEN, "Minion colocado. Extraera carbon automaticamente de la veta de este chunk.");
                    case FUERA_DE_TERRITORIO -> msg(player, NamedTextColor.RED, "Debes colocar el minion dentro de un territorio reclamado por tu empresa.");
                    case LIMITE_NIVEL -> msg(player, NamedTextColor.RED, "Alcanzaste el limite de minions para tu nivel ("
                            + plugin.levels().minionsPermitidos(company.getLevel()) + ").");
                    case SIN_SALDO -> msg(player, NamedTextColor.RED, "La empresa no tiene saldo suficiente para otro minion.");
                }
            }
            case "quitar" -> {
                Optional<MinionData> nearest = plugin.minions().nearest(company, player.getLocation(), 5);
                if (nearest.isEmpty()) {
                    msg(player, NamedTextColor.RED, "No hay ningun minion de tu empresa cerca.");
                    return;
                }
                plugin.minions().remove(company, nearest.get());
                msg(player, NamedTextColor.GREEN, "Minion removido.");
            }
            case "lista" -> {
                if (company.getMinions().isEmpty()) {
                    msg(player, NamedTextColor.GRAY, "Tu empresa no tiene minions todavia.");
                    return;
                }
                for (MinionData m : company.getMinions()) {
                    player.sendMessage(Component.text(" - " + m.getWorld() + " "
                            + Math.round(m.getX()) + "," + Math.round(m.getY()) + "," + Math.round(m.getZ()), NamedTextColor.GRAY));
                }
            }
            default -> msg(player, NamedTextColor.YELLOW, "Uso: /empresa minion <colocar|quitar|lista>");
        }
    }

    private void refinar(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwnerOrCollab(player, company)) return;

        double cantidad = parseCantidad(player, args);
        if (cantidad <= 0) return;

        double ratioBase = plugin.getConfig().getDouble("refineria.ratio", 2);
        double bonusPorNivel = plugin.getConfig().getDouble("refineria.bonus-por-nivel", 0.03);
        double ratio = Math.max(1.0, ratioBase - bonusPorNivel * (company.getLevel() - 1));
        double crudoNecesario = cantidad * ratio;

        if (!company.removeRawCoal(crudoNecesario)) {
            msg(player, NamedTextColor.RED, "No hay suficiente carbon crudo. Necesitas " + round(crudoNecesario)
                    + " para refinar " + round(cantidad) + ".");
            return;
        }
        company.addRefinedCoal(cantidad);
        plugin.companies().save();
        msg(player, NamedTextColor.GREEN, "Refinaste " + round(cantidad) + " de carbon usando " + round(crudoNecesario) + " crudo.");
    }

    private void vender(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwnerOrCollab(player, company)) return;

        if (args.length < 2) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa vender <crudo|refinado> <cantidad>");
            return;
        }
        boolean refinado = args[0].equalsIgnoreCase("refinado");
        if (!refinado && !args[0].equalsIgnoreCase("crudo")) {
            msg(player, NamedTextColor.YELLOW, "Uso: /empresa vender <crudo|refinado> <cantidad>");
            return;
        }

        double cantidad = parseCantidad(player, Arrays.copyOfRange(args, 1, args.length));
        if (cantidad <= 0) return;

        boolean ok = refinado ? company.removeRefinedCoal(cantidad) : company.removeRawCoal(cantidad);
        if (!ok) {
            msg(player, NamedTextColor.RED, "La empresa no tiene suficiente carbon " + (refinado ? "refinado" : "crudo") + ".");
            return;
        }
        double precio = plugin.getConfig().getDouble(refinado ? "economia.precio-carbon-refinado" : "economia.precio-carbon-crudo", 2.0);
        double total = cantidad * precio;
        plugin.economy().deposit(company.getId(), total);
        plugin.companies().save();
        msg(player, NamedTextColor.GREEN, "Vendiste " + round(cantidad) + " de carbon " + (refinado ? "refinado" : "crudo")
                + " por " + round(total) + ".");
    }

    private void depositar(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        double cantidad = parseCantidad(player, args);
        if (cantidad <= 0) return;
        if (!plugin.economy().withdraw(player.getUniqueId(), cantidad)) {
            msg(player, NamedTextColor.RED, "No tienes suficiente saldo personal.");
            return;
        }
        plugin.economy().deposit(company.getId(), cantidad);
        msg(player, NamedTextColor.GREEN, "Depositaste " + round(cantidad) + " en la empresa.");
    }

    private void retirar(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwner(player, company)) return;
        double cantidad = parseCantidad(player, args);
        if (cantidad <= 0) return;
        if (!plugin.economy().withdraw(company.getId(), cantidad)) {
            msg(player, NamedTextColor.RED, "La empresa no tiene suficiente balance.");
            return;
        }
        plugin.economy().deposit(player.getUniqueId(), cantidad);
        msg(player, NamedTextColor.GREEN, "Retiraste " + round(cantidad) + " de la empresa a tu saldo personal.");
    }

    private void obra(Player player, String[] args) {
        Company company = requireCompany(player);
        if (company == null) return;
        if (!requireOwnerOrCollab(player, company)) return;
        String accion = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        Optional<Obra> obra = plugin.obras().get(company.getId());

        switch (accion) {
            case "plano" -> {
                if (company.isSedeInaugurada()) {
                    msg(player, NamedTextColor.RED, "Tu empresa ya tiene su sede inaugurada.");
                } else if (obra.isPresent()) {
                    msg(player, NamedTextColor.RED, "Ya hay una obra en curso. Usa /empresa obra cancelar si quieres moverla.");
                } else if (plugin.territory().countClaims(company.getId()) == 0) {
                    msg(player, NamedTextColor.RED, "Primero reclama un territorio con /empresa reclamar.");
                } else if (plugin.obras().darPlanoSiCorresponde(player, company)) {
                    msg(player, NamedTextColor.GOLD, "Recibiste el Plano de obra: sostenlo y haz clic derecho donde quieras levantar la sede.");
                } else {
                    msg(player, NamedTextColor.YELLOW, "Ya tienes el Plano de obra en el inventario.");
                }
            }
            case "cancelar" -> {
                if (!requireOwner(player, company)) return;
                if (obra.isEmpty()) {
                    msg(player, NamedTextColor.RED, "Tu empresa no tiene ninguna obra en curso.");
                    return;
                }
                plugin.obras().cancelar(company.getId());
                msg(player, NamedTextColor.GREEN, "Obra cancelada. Los materiales sin usar quedaron junto al cofre. Usa /empresa obra plano para empezar de nuevo.");
            }
            default -> {
                if (company.isSedeInaugurada()) {
                    msg(player, NamedTextColor.GREEN, "La sede de " + company.getName() + " ya esta inaugurada.");
                    return;
                }
                if (obra.isEmpty()) {
                    msg(player, NamedTextColor.YELLOW, "Tu empresa todavia no empezo la obra de su sede. Reclama un territorio y usa el Plano de obra"
                            + " (si lo perdiste: /empresa obra plano).");
                    return;
                }
                Obra o = obra.get();
                SedeBlueprint plano = plugin.obras().plano();
                player.sendMessage(Component.text("===== Obra: Sede de " + company.getName() + " =====", NamedTextColor.GOLD));
                player.sendMessage(linea("Ubicacion", o.world() + " " + o.ax() + ", " + o.ay() + ", " + o.az()));
                player.sendMessage(linea("Limpieza", plugin.obras().porcentajeLimpieza(o) + "%"));
                player.sendMessage(linea("Etapas pagadas", o.etapasPagadas() + " / " + plano.etapas()));
                player.sendMessage(linea("Construido", o.colocados() * 100 / plano.piezas().size() + "%"));
                if (o.etapasPagadas() < plano.etapas()) {
                    player.sendMessage(Component.text("Faltan para " + SedeBlueprint.NOMBRES_ETAPAS[o.etapasPagadas()] + ":", NamedTextColor.GRAY));
                    plano.materiales(o.etapasPagadas()).forEach((m, n) -> {
                        int falta = n - o.entregado().getOrDefault(m, 0);
                        if (falta > 0) {
                            player.sendMessage(Component.text("  - " + falta + " x ", NamedTextColor.GRAY)
                                    .append(Component.translatable(m.translationKey(), NamedTextColor.WHITE)));
                        }
                    });
                }
            }
        }
    }

    private void ayuda(Player player) {
        player.sendMessage(Component.text("===== MinerCorp =====", NamedTextColor.GOLD));
        String[] lineas = {
                "/empresa crear <nombre> - funda tu empresa minera",
                "/empresa info [nombre] - ver datos de una empresa",
                "/empresa reclamar - reclama el chunk donde estas parado (debe tener veta de carbon)",
                "/empresa liberar - libera el chunk donde estas parado",
                "/empresa invitar|aceptar|rechazar|expulsar|salir - gestion de colaboradores",
                "/empresa disolver - disuelve tu empresa (solo el dueno)",
                "/empresa obra [plano|cancelar] - estado de la obra de la sede, pedir el plano o cancelarla",
                "/empresa taladro comprar <tier> - compra un taladro-vehiculo (parado en tu territorio)",
                "/empresa taladro cargar [cantidad] - carga combustible con carbon crudo de la empresa",
                "/empresa minion <colocar|quitar|lista> - gestiona tus minions",
                "/empresa refinar <cantidad> - convierte carbon crudo en refinado",
                "/empresa vender <crudo|refinado> <cantidad> - vende produccion",
                "/empresa depositar|retirar <monto> - mueve dinero entre tu saldo y la empresa",
                "/empresa menu - abre el menu con botones para las acciones mas comunes"
        };
        for (String l : lineas) player.sendMessage(Component.text(l, NamedTextColor.GRAY));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Company requireCompany(Player player) {
        Optional<Company> company = plugin.companies().getByMember(player.getUniqueId());
        if (company.isEmpty()) {
            msg(player, NamedTextColor.RED, "No perteneces a ninguna empresa. Usa /empresa crear <nombre>.");
            return null;
        }
        return company.get();
    }

    private boolean requireOwner(Player player, Company company) {
        if (!company.getOwner().equals(player.getUniqueId())) {
            msg(player, NamedTextColor.RED, "Solo el dueno de la empresa puede hacer esto.");
            return false;
        }
        return true;
    }

    private boolean requireOwnerOrCollab(Player player, Company company) {
        if (!company.isMember(player.getUniqueId())) {
            msg(player, NamedTextColor.RED, "No perteneces a esta empresa.");
            return false;
        }
        return true;
    }

    private double parseCantidad(Player player, String[] args) {
        if (args.length < 1) {
            msg(player, NamedTextColor.YELLOW, "Debes indicar una cantidad.");
            return -1;
        }
        try {
            double value = Double.parseDouble(args[0]);
            if (value <= 0) {
                msg(player, NamedTextColor.RED, "La cantidad debe ser mayor a 0.");
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            msg(player, NamedTextColor.RED, "Cantidad invalida.");
            return -1;
        }
    }

    private Optional<Chunk> resolveChunk(ChunkKey key) {
        var world = Bukkit.getWorld(key.world());
        if (world == null) return Optional.empty();
        return Optional.of(world.getChunkAt(key.x(), key.z()));
    }

    private String nombreDe(UUID id) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(id);
        String name = p.getName();
        return name != null ? name : id.toString();
    }

    private Component linea(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void msg(CommandSender sender, NamedTextColor color, String text) {
        sender.sendMessage(Component.text(text, color));
    }

    // ---------------------------------------------------------------
    // Tab completion
    // ---------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMANDOS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "taladro" -> List.of("comprar", "cargar", "quitar");
                case "minion" -> List.of("colocar", "quitar", "lista");
                case "obra" -> List.of("info", "plano", "cancelar");
                case "vender" -> List.of("crudo", "refinado");
                case "invitar", "expulsar" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("taladro") && args[1].equalsIgnoreCase("comprar")) {
            return List.of("1", "2", "3");
        }
        return List.of();
    }
}
