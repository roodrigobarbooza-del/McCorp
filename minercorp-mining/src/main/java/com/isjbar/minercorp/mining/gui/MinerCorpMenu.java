package com.isjbar.minercorp.mining.gui;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/** Construye las pantallas del menu HUD de MinerCorp (inventario tipo cofre). */
public class MinerCorpMenu {

    private static NamespacedKey actionKey(MiningPlugin plugin) {
        return new NamespacedKey(plugin, "menu_action");
    }

    /** Abre la pantalla que corresponda segun si el jugador ya tiene empresa o no. */
    public static void open(MiningPlugin plugin, Player player) {
        Optional<Company> company = plugin.companies().getByMember(player.getUniqueId());
        player.openInventory(buildMain(plugin, company.orElse(null)));
    }

    public static Inventory buildMain(MiningPlugin plugin, Company company) {
        MenuHolder holder = new MenuHolder(MenuHolder.Pantalla.PRINCIPAL);
        Inventory inv = org.bukkit.Bukkit.createInventory(holder, 27, Component.text("MinerCorp", NamedTextColor.GOLD));
        holder.setInventory(inv);

        if (company == null) {
            double costo = plugin.getConfig().getDouble("economia.costo-fundar-empresa", 50.0);
            inv.setItem(13, item(plugin, Material.EMERALD, "Fundar empresa", NamedTextColor.GREEN,
                    List.of("Costo: " + costo, "Click para escribir el nombre por chat"), MenuActions.FUNDAR));
            return inv;
        }

        inv.setItem(10, item(plugin, Material.BOOK, "Info de " + company.getName(), NamedTextColor.AQUA,
                List.of(
                        "Nivel " + company.getLevel(),
                        "Balance: " + plugin.economy().format(plugin.economy().getBalance(company.getId())),
                        "Carbon crudo: " + round(company.getRawCoal()),
                        "Carbon refinado: " + round(company.getRefinedCoal()),
                        "Territorios: " + plugin.territory().countClaims(company.getId()) + " / " + plugin.levels().chunksPermitidos(company.getLevel()),
                        "Minions: " + company.getMinions().size() + " / " + plugin.levels().minionsPermitidos(company.getLevel())
                ), MenuActions.INFO));

        double costoReclamar = plugin.getConfig().getDouble("economia.costo-reclamar-chunk", 100.0);
        inv.setItem(11, item(plugin, Material.GRASS_BLOCK, "Reclamar aqui", NamedTextColor.GREEN,
                List.of("Costo: " + costoReclamar, "Debe haber una veta de carbon en este chunk"), MenuActions.RECLAMAR));

        inv.setItem(12, item(plugin, Material.MINECART, "Garaje", NamedTextColor.AQUA,
                List.of("Taladros y vehiculos de la empresa", "(abre /garaje)"), MenuActions.GARAJE));

        inv.setItem(14, item(plugin, Material.VILLAGER_SPAWN_EGG, "Colocar minion", NamedTextColor.LIGHT_PURPLE,
                List.of("Extrae carbon solo con el tiempo", "Debe estar dentro de tu territorio"), MenuActions.MINION_COLOCAR));

        inv.setItem(15, item(plugin, Material.HOPPER, "Sacar carbon crudo", NamedTextColor.GOLD,
                List.of(round(company.getRawCoal()) + " en la empresa", "Saca 64 para llevar al Horno de coque"), MenuActions.SACAR_CARBON));

        double precioCrudo = plugin.getConfig().getDouble("economia.precio-carbon-crudo", 2.0);
        inv.setItem(16, item(plugin, Material.COAL, "Vender todo el crudo", NamedTextColor.YELLOW,
                List.of(round(company.getRawCoal()) + " disponibles", "Precio: " + precioCrudo + " c/u"), MenuActions.VENDER_CRUDO_TODO));

        double precioRefinado = plugin.getConfig().getDouble("economia.precio-carbon-refinado", 5.0);
        inv.setItem(17, item(plugin, Material.CHARCOAL, "Vender todo el refinado", NamedTextColor.YELLOW,
                List.of(round(company.getRefinedCoal()) + " disponibles", "Precio: " + precioRefinado + " c/u"), MenuActions.VENDER_REFINADO_TODO));

        inv.setItem(22, item(plugin, Material.BARRIER, "Cerrar", NamedTextColor.RED, List.of(), MenuActions.CERRAR));

        return inv;
    }

    private static ItemStack item(MiningPlugin plugin, Material material, String nombre, NamedTextColor color, List<String> lore, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(nombre, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        meta.getPersistentDataContainer().set(actionKey(plugin), PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    static String actionOf(MiningPlugin plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(actionKey(plugin), PersistentDataType.STRING);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
