package com.isjbar.minercorp.gransede.tienda;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.gransede.api.Producto;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.Optional;
import java.util.logging.Logger;

/** Clicks en los menus de las tiendas: elegir producto, confirmar, cobrar y entregar. */
public class TiendaListener implements Listener {

    private final TiendaManager tiendas;
    private final TiendaMenu menu;
    private final EconomyAPI economy;
    private final Logger logger;

    public TiendaListener(TiendaManager tiendas, TiendaMenu menu, EconomyAPI economy, Logger logger) {
        this.tiendas = tiendas;
        this.menu = menu;
        this.economy = economy;
        this.logger = logger;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof TiendaHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof TiendaHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Optional<Tienda> tienda = tiendas.tienda(holder.tienda());
        if (tienda.isEmpty()) {
            player.closeInventory();
            return;
        }
        int slot = e.getRawSlot();

        if (holder.pantalla() == TiendaHolder.Pantalla.LISTA) {
            if (slot == TiendaMenu.ANTERIOR) menu.abrirLista(player, tienda.get(), holder.pagina() - 1);
            else if (slot == TiendaMenu.SIGUIENTE) menu.abrirLista(player, tienda.get(), holder.pagina() + 1);
            else {
                String id = menu.productoDe(e.getCurrentItem());
                if (id != null) tiendas.producto(id).ifPresent(p -> menu.abrirConfirmar(player, tienda.get(), holder.pagina(), p));
            }
            return;
        }

        if (slot == TiendaMenu.CANCELAR) {
            menu.abrirLista(player, tienda.get(), holder.pagina());
        } else if (slot == TiendaMenu.CONFIRMAR) {
            Optional<Producto> p = tiendas.producto(holder.producto());
            if (p.isEmpty()) {
                player.sendMessage(Component.text("Ese producto ya no esta a la venta.", NamedTextColor.RED));
                menu.abrirLista(player, tienda.get(), holder.pagina());
                return;
            }
            comprar(player, p.get());
            player.closeInventory();
        }
    }

    private void comprar(Player player, Producto p) {
        if (!economy.withdraw(player.getUniqueId(), p.precio())) {
            player.sendMessage(Component.text("No te alcanza: " + p.nombre() + " cuesta " + TiendaMenu.dinero(p.precio())
                    + " y tenes " + TiendaMenu.dinero(economy.getBalance(player.getUniqueId())) + ".", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (!TiendaManager.entregarSeguro(p, player, logger)) {
            economy.deposit(player.getUniqueId(), p.precio());
            player.sendMessage(Component.text("No se pudo entregar " + p.nombre() + ". Te devolvimos el dinero.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Compraste " + p.nombre() + " por " + TiendaMenu.dinero(p.precio()) + ".", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
    }
}
