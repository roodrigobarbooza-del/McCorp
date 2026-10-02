package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountInfo;
import com.isjbar.minercorp.economy.api.AccountType;
import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.economy.api.Reason;
import com.isjbar.minercorp.economy.api.TransactionRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menu /billetera: saldo arriba, 28 movimientos por pagina en el medio, y
 * abajo botones para paginar, cambiar de cuenta (personal / empresas) y ver
 * el ranking.
 */
final class WalletMenu implements InventoryHolder {

    private enum Vista { MOVIMIENTOS, RANKING }

    private static final int[] CONTENIDO = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int SLOT_CUENTA = 4;
    private static final int SLOT_ANTERIOR = 45;
    private static final int SLOT_CAMBIAR_CUENTA = 47;
    private static final int SLOT_CERRAR = 49;
    private static final int SLOT_VISTA = 51;
    private static final int SLOT_SIGUIENTE = 53;
    private static final int SLOT_FILTRO_RANKING = 48;

    private final EconomyPlugin plugin;
    private final UUID viewer;
    private final Inventory inventory;
    private UUID cuenta;
    private Vista vista = Vista.MOVIMIENTOS;
    private AccountType filtroRanking = null;
    private int pagina = 0;

    private WalletMenu(EconomyPlugin plugin, UUID viewer, UUID cuenta) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.cuenta = cuenta;
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("Billetera", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
    }

    static void open(EconomyPlugin plugin, Player player, UUID cuenta) {
        WalletMenu menu = new WalletMenu(plugin, player.getUniqueId(), cuenta);
        menu.render();
        player.openInventory(menu.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private EconomyAPI eco() {
        return plugin.economy();
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------- dibujo

    private void render() {
        inventory.clear();
        ItemStack borde = item(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        ItemStack lateral = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 9; i++) inventory.setItem(i, borde);
        for (int i = 45; i < 54; i++) inventory.setItem(i, borde);
        for (int fila = 1; fila <= 4; fila++) {
            inventory.setItem(fila * 9, lateral);
            inventory.setItem(fila * 9 + 8, lateral);
        }

        inventory.setItem(SLOT_CUENTA, iconoCuenta());
        int total = vista == Vista.MOVIMIENTOS ? renderMovimientos() : renderRanking();
        int paginas = Math.max(1, (total + CONTENIDO.length - 1) / CONTENIDO.length);

        if (pagina > 0) {
            inventory.setItem(SLOT_ANTERIOR, item(Material.ARROW, Component.text("« Pagina anterior", NamedTextColor.YELLOW),
                    List.of(gris("Pagina " + pagina + " de " + paginas))));
        }
        if (pagina + 1 < paginas) {
            inventory.setItem(SLOT_SIGUIENTE, item(Material.ARROW, Component.text("Pagina siguiente »", NamedTextColor.YELLOW),
                    List.of(gris("Pagina " + (pagina + 2) + " de " + paginas))));
        }

        List<AccountInfo> propias = eco().getAccountsOwnedBy(viewer);
        if (vista == Vista.MOVIMIENTOS && propias.size() > 1) {
            List<Component> lore = new ArrayList<>();
            for (AccountInfo a : propias) {
                boolean actual = a.id().equals(cuenta);
                lore.add(Component.text((actual ? "▶ " : "  ") + nombre(a) + "  ", actual ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                        .append(Component.text(eco().format(a.balance()), NamedTextColor.GREEN))
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(gris("Clic para cambiar de cuenta"));
            inventory.setItem(SLOT_CAMBIAR_CUENTA, item(Material.CHEST, Component.text("Tus cuentas", NamedTextColor.GOLD), lore));
        }
        if (vista == Vista.RANKING) {
            String filtro = filtroRanking == AccountType.PLAYER ? "Jugadores"
                    : filtroRanking == AccountType.COMPANY ? "Empresas" : "Todos";
            inventory.setItem(SLOT_FILTRO_RANKING, item(Material.HOPPER, Component.text("Mostrando: " + filtro, NamedTextColor.GOLD),
                    List.of(gris("Clic para cambiar entre todos,"), gris("jugadores y empresas"))));
        }

        inventory.setItem(SLOT_CERRAR, item(Material.BARRIER, Component.text("Cerrar", NamedTextColor.RED), List.of()));
        inventory.setItem(SLOT_VISTA, vista == Vista.MOVIMIENTOS
                ? item(Material.GOLD_INGOT, Component.text("Ranking de fortunas", NamedTextColor.GOLD),
                List.of(gris("Los mas ricos del server")))
                : item(Material.WRITABLE_BOOK, Component.text("Volver a mis movimientos", NamedTextColor.GOLD), List.of()));
    }

    private ItemStack iconoCuenta() {
        AccountInfo info = eco().getAccount(cuenta).orElse(null);
        double saldo = eco().getBalance(cuenta);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Saldo: ", NamedTextColor.GRAY).append(Component.text(eco().format(saldo), NamedTextColor.GREEN, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));

        // Resumen de lo que entro y salio en los movimientos que se recuerdan.
        double entro = 0, salio = 0;
        for (TransactionRecord r : eco().getHistory(cuenta, Integer.MAX_VALUE)) {
            if (r.isIncome()) entro += r.amount();
            else salio -= r.amount();
        }
        lore.add(Component.empty());
        lore.add(Component.text("Ingresos recientes: ", NamedTextColor.GRAY).append(Component.text("+" + eco().format(entro), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Gastos recientes: ", NamedTextColor.GRAY).append(Component.text("-" + eco().format(salio), NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));

        boolean esJugador = info == null || info.type() == AccountType.PLAYER;
        ItemStack icono = item(esJugador ? Material.PLAYER_HEAD : Material.GOLD_BLOCK,
                Component.text(info == null ? msg().accountName(cuenta) : nombre(info), NamedTextColor.GOLD, TextDecoration.BOLD), lore);
        if (esJugador) ponerCabeza(icono, cuenta);
        return icono;
    }

    private int renderMovimientos() {
        List<TransactionRecord> historial = eco().getHistory(cuenta, Integer.MAX_VALUE);
        if (historial.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text("Sin movimientos todavia", NamedTextColor.GRAY),
                    List.of(gris("Aca vas a ver cada venta, compra y pago."))));
            return 0;
        }
        int desde = pagina * CONTENIDO.length;
        for (int i = 0; i < CONTENIDO.length && desde + i < historial.size(); i++) {
            inventory.setItem(CONTENIDO[i], itemMovimiento(historial.get(desde + i)));
        }
        return historial.size();
    }

    private ItemStack itemMovimiento(TransactionRecord r) {
        boolean ingreso = r.isIncome();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(r.reason().categoryLabel(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        if (r.reason().detail() != null) lore.add(blanco(r.reason().detail()));
        lore.add(Component.empty());
        lore.add(par("Con", msg().accountName(r.counterparty())));
        lore.add(par("Saldo despues", eco().format(r.balanceAfter())));
        lore.add(par("Fecha", Messages.date(r.timestamp()) + " (" + Messages.ago(r.timestamp()) + ")"));
        lore.add(Component.text(r.transactionId(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        return item(material(r.reason(), ingreso),
                Component.text((ingreso ? "+" : "-") + eco().format(Math.abs(r.amount())),
                        ingreso ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD),
                lore);
    }

    private static Material material(Reason reason, boolean ingreso) {
        return switch (reason.category()) {
            case Reason.VENTA -> Material.EMERALD;
            case Reason.COMPRA -> Material.CHEST_MINECART;
            case Reason.PAGO -> Material.PAPER;
            case Reason.SUELDO -> Material.GOLD_NUGGET;
            case Reason.IMPUESTO -> Material.IRON_BARS;
            case Reason.MANTENIMIENTO -> Material.ANVIL;
            case Reason.PRODUCCION -> Material.BLAST_FURNACE;
            case Reason.BONO -> Material.CAKE;
            case Reason.ADMIN -> Material.NETHER_STAR;
            default -> ingreso ? Material.LIME_DYE : Material.RED_DYE;
        };
    }

    private int renderRanking() {
        List<AccountInfo> ranking = eco().getTop(filtroRanking, 100);
        if (ranking.isEmpty()) {
            inventory.setItem(22, item(Material.GRAY_DYE, Component.text("Todavia no hay nadie en el ranking", NamedTextColor.GRAY), List.of()));
            return 0;
        }
        int desde = pagina * CONTENIDO.length;
        for (int i = 0; i < CONTENIDO.length && desde + i < ranking.size(); i++) {
            int puesto = desde + i;
            AccountInfo a = ranking.get(puesto);
            TextColor color = switch (puesto) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.WHITE;
                case 2 -> TextColor.color(0xCD7F32);
                default -> NamedTextColor.YELLOW;
            };
            boolean esJugador = a.type() == AccountType.PLAYER;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(esJugador ? "Jugador" : "Empresa", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));
            lore.add(par("Fortuna", eco().format(a.balance())));
            if (!esJugador && a.owner() != null) lore.add(par("Dueno", msg().accountName(a.owner())));
            ItemStack icono = item(esJugador ? Material.PLAYER_HEAD : Material.GOLD_BLOCK,
                    Component.text("#" + (puesto + 1) + " " + nombre(a), color, TextDecoration.BOLD), lore);
            if (esJugador) ponerCabeza(icono, a.id());
            inventory.setItem(CONTENIDO[i], icono);
        }
        return ranking.size();
    }

    // -------------------------------------------------------------------- clics

    void click(Player player, int slot) {
        boolean cambio = true;
        switch (slot) {
            case SLOT_ANTERIOR -> pagina = Math.max(0, pagina - 1);
            case SLOT_SIGUIENTE -> pagina++;
            case SLOT_CERRAR -> {
                player.closeInventory();
                return;
            }
            case SLOT_VISTA -> {
                vista = vista == Vista.MOVIMIENTOS ? Vista.RANKING : Vista.MOVIMIENTOS;
                pagina = 0;
            }
            case SLOT_CAMBIAR_CUENTA -> {
                if (vista != Vista.MOVIMIENTOS) return;
                List<AccountInfo> propias = eco().getAccountsOwnedBy(viewer);
                if (propias.size() < 2) return;
                int actual = 0;
                for (int i = 0; i < propias.size(); i++) if (propias.get(i).id().equals(cuenta)) actual = i;
                cuenta = propias.get((actual + 1) % propias.size()).id();
                pagina = 0;
            }
            case SLOT_FILTRO_RANKING -> {
                if (vista != Vista.RANKING) return;
                filtroRanking = filtroRanking == null ? AccountType.PLAYER
                        : filtroRanking == AccountType.PLAYER ? AccountType.COMPANY : null;
                pagina = 0;
            }
            default -> cambio = false;
        }
        if (cambio) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            render();
        }
    }

    // ---------------------------------------------------------------- utilidades

    private String nombre(AccountInfo a) {
        return a.nameOr(msg().accountName(a.id()));
    }

    private static ItemStack item(Material material, Component nombre, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(nombre.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void ponerCabeza(ItemStack stack, UUID jugador) {
        if (stack.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(jugador));
            stack.setItemMeta(skull);
        }
    }

    private static Component gris(String texto) {
        return Component.text(texto, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private static Component blanco(String texto) {
        return Component.text(texto, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private static Component par(String clave, String valor) {
        return Component.text(clave + ": ", NamedTextColor.GRAY)
                .append(Component.text(valor, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Bloquea sacar o meter items en el menu y reenvia los clics. */
    static final class ClickListener implements org.bukkit.event.Listener {
        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof WalletMenu menu)) return;
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player
                    && event.getRawSlot() >= 0 && event.getRawSlot() < menu.inventory.getSize()) {
                menu.click(player, event.getRawSlot());
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder() instanceof WalletMenu) event.setCancelled(true);
        }
    }
}
