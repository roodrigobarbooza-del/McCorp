package com.isjbar.minercorp.gransede.tienda;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.gransede.api.Producto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pantallas de las tiendas: la lista de productos y la confirmacion de compra. */
public class TiendaMenu {

    /** Huecos donde van los productos en el cofre de 6 filas (borde libre). */
    static final int[] HUECOS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int ANTERIOR = 45, SALDO = 49, SIGUIENTE = 53;
    static final int CONFIRMAR = 11, CANCELAR = 15;

    private final NamespacedKey productoKey;
    private final TiendaManager tiendas;
    private final EconomyAPI economy;

    public TiendaMenu(Plugin plugin, TiendaManager tiendas, EconomyAPI economy) {
        this.productoKey = new NamespacedKey(plugin, "producto");
        this.tiendas = tiendas;
        this.economy = economy;
    }

    public void abrirLista(Player player, Tienda tienda, int pagina) {
        List<Producto> productos = tiendas.productos(tienda.id());
        int paginas = Math.max(1, (productos.size() + HUECOS.length - 1) / HUECOS.length);
        pagina = Math.max(0, Math.min(pagina, paginas - 1));

        TiendaHolder holder = new TiendaHolder(TiendaHolder.Pantalla.LISTA, tienda.id(), pagina, null);
        Inventory inv = Bukkit.createInventory(holder, 54, legacy(tienda.titulo()).colorIfAbsent(NamedTextColor.DARK_GRAY));
        holder.setInventory(inv);

        ItemStack vidrio = icono(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 54; i++) inv.setItem(i, vidrio);

        double saldo = economy.getBalance(player.getUniqueId());
        int desde = pagina * HUECOS.length;
        for (int i = 0; i < HUECOS.length && desde + i < productos.size(); i++) {
            inv.setItem(HUECOS[i], iconoProducto(productos.get(desde + i), saldo));
        }
        if (productos.isEmpty()) {
            inv.setItem(22, icono(Material.BARRIER, Component.text("Todavia no hay nada a la venta", NamedTextColor.GRAY), List.of()));
        }

        inv.setItem(SALDO, icono(Material.GOLD_INGOT, Component.text("Tu saldo: " + dinero(saldo), NamedTextColor.GOLD), List.of()));
        if (pagina > 0) inv.setItem(ANTERIOR, icono(Material.ARROW, Component.text("Pagina anterior", NamedTextColor.YELLOW), List.of()));
        if (pagina < paginas - 1) inv.setItem(SIGUIENTE, icono(Material.ARROW, Component.text("Pagina siguiente", NamedTextColor.YELLOW), List.of()));
        player.openInventory(inv);
    }

    public void abrirConfirmar(Player player, Tienda tienda, int pagina, Producto p) {
        TiendaHolder holder = new TiendaHolder(TiendaHolder.Pantalla.CONFIRMAR, tienda.id(), pagina, p.id());
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Comprar " + p.nombre() + "?", NamedTextColor.DARK_GRAY));
        holder.setInventory(inv);
        inv.setItem(CONFIRMAR, icono(Material.LIME_CONCRETE, Component.text("Comprar por " + dinero(p.precio()), NamedTextColor.GREEN), List.of()));
        inv.setItem(13, iconoProducto(p, economy.getBalance(player.getUniqueId())));
        inv.setItem(CANCELAR, icono(Material.RED_CONCRETE, Component.text("Volver", NamedTextColor.RED), List.of()));
        player.openInventory(inv);
    }

    private ItemStack iconoProducto(Producto p, double saldo) {
        ItemStack stack = p.icono() == null ? new ItemStack(Material.CHEST) : p.icono().clone();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(legacy(p.nombre()).colorIfAbsent(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String l : p.lore()) lore.add(legacy(l).colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        // Si cobra el plugin del producto (ej. de la cuenta de la empresa), la billetera no dice si alcanza.
        boolean alcanza = p.cobraPropio() || saldo >= p.precio();
        lore.add(Component.text("Precio: " + dinero(p.precio()), alcanza ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(alcanza ? "Click para comprar" : "No te alcanza", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(productoKey, PersistentDataType.STRING, p.id());
        stack.setItemMeta(meta);
        return stack;
    }

    String productoDe(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(productoKey, PersistentDataType.STRING);
    }

    private static ItemStack icono(Material m, Component nombre, List<Component> lore) {
        ItemStack stack = new ItemStack(m);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(nombre.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static Component legacy(String texto) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(texto == null ? "" : texto);
    }

    public static String dinero(double d) {
        return "$" + String.format(Locale.ROOT, "%,.2f", d);
    }
}
