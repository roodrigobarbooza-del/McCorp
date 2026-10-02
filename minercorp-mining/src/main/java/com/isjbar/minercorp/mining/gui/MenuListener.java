package com.isjbar.minercorp.mining.gui;

import com.isjbar.minercorp.economy.api.Reason;
import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.mining.company.CompanyManager;
import com.isjbar.minercorp.mining.minion.MinionManager;
import com.isjbar.minercorp.mining.vehicle.DrillVehicleManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Maneja los clicks del menu HUD y el prompt de chat para fundar una empresa desde ahi. */
public class MenuListener implements Listener {

    private final MiningPlugin plugin;
    /** Jugadores que clickearon "fundar empresa" y estan esperando que escriban el nombre por chat. */
    private final Set<UUID> esperandoNombre = new HashSet<>();

    public MenuListener(MiningPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof MenuHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String action = MinerCorpMenu.actionOf(plugin, event.getCurrentItem());
        if (action == null) return;

        handle(player, holder, action);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!esperandoNombre.remove(player.getUniqueId())) return;

        event.setCancelled(true);
        String nombre = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> crearEmpresa(player, nombre));
    }

    private void handle(Player player, MenuHolder holder, String action) {
        if (action.equals(MenuActions.FUNDAR)) {
            player.closeInventory();
            esperandoNombre.add(player.getUniqueId());
            msg(player, NamedTextColor.AQUA, "Escribi el nombre de tu empresa en el chat.");
            return;
        }
        if (action.equals(MenuActions.CERRAR)) {
            player.closeInventory();
            return;
        }
        if (action.equals(MenuActions.VOLVER)) {
            MinerCorpMenu.open(plugin, player);
            return;
        }

        Optional<Company> companyOpt = plugin.companies().getByMember(player.getUniqueId());
        if (companyOpt.isEmpty()) {
            msg(player, NamedTextColor.RED, "No perteneces a ninguna empresa.");
            player.closeInventory();
            return;
        }
        Company company = companyOpt.get();

        if (action.equals(MenuActions.TALADRO_MENU)) {
            MinerCorpMenu.openTaladroSubmenu(plugin, player, company);
            return;
        }
        if (action.equals(MenuActions.INFO)) {
            return;
        }
        if (action.equals(MenuActions.RECLAMAR)) {
            reclamar(player, company);
            MinerCorpMenu.open(plugin, player);
            return;
        }
        if (action.equals(MenuActions.MINION_COLOCAR)) {
            colocarMinion(player, company);
            MinerCorpMenu.open(plugin, player);
            return;
        }
        if (action.equals(MenuActions.REFINAR_TODO)) {
            refinarTodo(player, company);
            MinerCorpMenu.open(plugin, player);
            return;
        }
        if (action.equals(MenuActions.VENDER_CRUDO_TODO)) {
            venderTodo(player, company, false);
            MinerCorpMenu.open(plugin, player);
            return;
        }
        if (action.equals(MenuActions.VENDER_REFINADO_TODO)) {
            venderTodo(player, company, true);
            MinerCorpMenu.open(plugin, player);
            return;
        }
        if (action.startsWith(MenuActions.TALADRO_SACAR_PREFIX)) {
            int index = Integer.parseInt(action.substring(MenuActions.TALADRO_SACAR_PREFIX.length()));
            sacarTaladro(player, company, index);
            MinerCorpMenu.openTaladroSubmenu(plugin, player, company);
            return;
        }
        if (action.startsWith(MenuActions.TALADRO_TIER_PREFIX)) {
            int tier = Integer.parseInt(action.substring(MenuActions.TALADRO_TIER_PREFIX.length()));
            comprarTaladro(player, company, tier);
            MinerCorpMenu.openTaladroSubmenu(plugin, player, company);
        }
    }

    private void crearEmpresa(Player player, String nombre) {
        if (nombre.isBlank()) {
            msg(player, NamedTextColor.RED, "Nombre invalido.");
            return;
        }
        if (plugin.companies().getByMember(player.getUniqueId()).isPresent()) {
            msg(player, NamedTextColor.RED, "Ya perteneces a una empresa.");
            return;
        }
        if (plugin.companies().getByName(nombre).isPresent()) {
            msg(player, NamedTextColor.RED, "Ya existe una empresa con ese nombre.");
            return;
        }
        double costo = plugin.getConfig().getDouble("economia.costo-fundar-empresa", 50.0);
        if (!plugin.economy().withdraw(player.getUniqueId(), costo, Reason.of(Reason.COMPRA, "Fundar la empresa " + nombre)).success()) {
            msg(player, NamedTextColor.RED, "Te faltan fondos. Fundar una empresa cuesta " + costo + ".");
            return;
        }
        Company company = plugin.companies().create(nombre, player.getUniqueId());
        msg(player, NamedTextColor.GREEN, "Fundaste la empresa minera '" + company.getName() + "'.");
        MinerCorpMenu.open(plugin, player);
    }

    private void reclamar(Player player, Company company) {
        double costo = plugin.getConfig().getDouble("economia.costo-reclamar-chunk", 100.0);
        var chunk = player.getLocation().getChunk();

        if (plugin.economy().getBalance(company.getId()) < costo) {
            msg(player, NamedTextColor.RED, "La empresa necesita " + costo + " para reclamar este territorio.");
            return;
        }

        CompanyManager.ClaimOutcome result = plugin.companies().claim(company, chunk);
        switch (result) {
            case OK -> {
                plugin.economy().withdraw(company.getId(), costo, Reason.of(Reason.COMPRA, "Territorio en " + chunk.getX() + ", " + chunk.getZ()));
                msg(player, NamedTextColor.GREEN, "Territorio reclamado para " + company.getName() + ".");
                if (plugin.obras().darPlanoSiCorresponde(player, company)) {
                    msg(player, NamedTextColor.GOLD, "Recibiste el Plano de obra: sostenlo y haz clic derecho donde quieras levantar la sede.");
                }
            }
            case YA_RECLAMADO -> msg(player, NamedTextColor.RED, "Este chunk ya esta reclamado.");
            case SIN_VETA -> msg(player, NamedTextColor.RED, "No hay ninguna veta de carbon en este chunk.");
            case LIMITE_NIVEL -> msg(player, NamedTextColor.RED, "Tu empresa ya alcanzo el limite de territorios para su nivel.");
        }
    }

    private void colocarMinion(Player player, Company company) {
        if (!plugin.obras().sedeLista(company)) {
            msg(player, NamedTextColor.RED, "Primero termina la obra de la sede de tu empresa (/empresa obra).");
            return;
        }
        MinionManager.MinionPlacement result = plugin.minions().place(company, player.getLocation());
        switch (result) {
            case OK -> msg(player, NamedTextColor.GREEN, "Minion colocado.");
            case FUERA_DE_TERRITORIO -> msg(player, NamedTextColor.RED, "Debes estar dentro de un territorio reclamado por tu empresa.");
            case LIMITE_NIVEL -> msg(player, NamedTextColor.RED, "Alcanzaste el limite de minions para tu nivel.");
            case SIN_SALDO -> msg(player, NamedTextColor.RED, "La empresa no tiene saldo suficiente para otro minion.");
        }
    }

    private void refinarTodo(Player player, Company company) {
        double ratioBase = plugin.getConfig().getDouble("refineria.ratio", 2);
        double bonusPorNivel = plugin.getConfig().getDouble("refineria.bonus-por-nivel", 0.03);
        double ratio = Math.max(1.0, ratioBase - bonusPorNivel * (company.getLevel() - 1));
        double cantidad = company.getRawCoal() / ratio;

        if (cantidad <= 0) {
            msg(player, NamedTextColor.RED, "No tenes carbon crudo para refinar.");
            return;
        }
        double crudoNecesario = cantidad * ratio;
        company.removeRawCoal(crudoNecesario);
        company.addRefinedCoal(cantidad);
        plugin.companies().save();
        msg(player, NamedTextColor.GREEN, "Refinaste " + round(cantidad) + " de carbon.");
    }

    private void venderTodo(Player player, Company company, boolean refinado) {
        double cantidad = refinado ? company.getRefinedCoal() : company.getRawCoal();
        if (cantidad <= 0) {
            msg(player, NamedTextColor.RED, "No tenes carbon " + (refinado ? "refinado" : "crudo") + " para vender.");
            return;
        }
        boolean ok = refinado ? company.removeRefinedCoal(cantidad) : company.removeRawCoal(cantidad);
        if (!ok) return;

        double precio = plugin.getConfig().getDouble(refinado ? "economia.precio-carbon-refinado" : "economia.precio-carbon-crudo", 2.0);
        double total = cantidad * precio;
        plugin.economy().deposit(company.getId(), total,
                Reason.of(Reason.VENTA, round(cantidad) + " de carbon " + (refinado ? "refinado" : "crudo")));
        plugin.companies().save();
        msg(player, NamedTextColor.GREEN, "Vendiste " + round(cantidad) + " de carbon " + (refinado ? "refinado" : "crudo") + " por " + round(total) + ".");
    }

    private void sacarTaladro(Player player, Company company, int index) {
        switch (plugin.vehicles().deploy(company, index, player.getLocation())) {
            case OK -> msg(player, NamedTextColor.GREEN, "Sacaste el taladro del garaje. Subite con click derecho.");
            case FUERA_DE_TERRITORIO -> msg(player, NamedTextColor.RED, "Debes estar parado dentro de un territorio reclamado por tu empresa.");
            default -> msg(player, NamedTextColor.RED, "Ese taladro ya no esta en el garaje.");
        }
    }

    private void comprarTaladro(Player player, Company company, int tier) {
        if (!plugin.obras().sedeLista(company)) {
            msg(player, NamedTextColor.RED, "Primero termina la obra de la sede de tu empresa (/empresa obra).");
            return;
        }
        int maxTier = plugin.levels().tierMaximoDeTaladro(company.getLevel());
        if (tier > maxTier) {
            msg(player, NamedTextColor.RED, "Tu empresa (nivel " + company.getLevel() + ") solo puede comprar hasta el tier " + maxTier + ".");
            return;
        }
        DrillVehicleManager.PlacementResult result = plugin.vehicles().spawn(company, tier, player.getLocation());
        switch (result) {
            case OK -> msg(player, NamedTextColor.GREEN, "Taladro comprado y colocado. Cargale carbon y subite con click derecho.");
            case TIER_INVALIDO -> msg(player, NamedTextColor.RED, "Ese tier no existe.");
            case FUERA_DE_TERRITORIO -> msg(player, NamedTextColor.RED, "Debes estar parado dentro de un territorio reclamado por tu empresa.");
            case SIN_SALDO -> msg(player, NamedTextColor.RED, "La empresa no tiene saldo suficiente para ese taladro.");
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void msg(Player player, NamedTextColor color, String text) {
        player.sendMessage(Component.text(text, color));
    }
}
