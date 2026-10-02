package com.isjbar.minercorp.gransede.npc;

import com.isjbar.minercorp.gransede.tienda.TiendaManager;
import com.isjbar.minercorp.gransede.tienda.TiendaMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Click derecho a un vendedor abre su tienda; nada lo lastima ni le cambia el oficio. */
public class VendedorListener implements Listener {

    private final VendedorManager vendedores;
    private final TiendaManager tiendas;
    private final TiendaMenu menu;

    public VendedorListener(VendedorManager vendedores, TiendaManager tiendas, TiendaMenu menu) {
        this.vendedores = vendedores;
        this.tiendas = tiendas;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent e) {
        vendedores.tiendaDe(e.getRightClicked()).ifPresent(id -> {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            tiendas.tienda(id).ifPresentOrElse(
                    t -> menu.abrirLista(e.getPlayer(), t, 0),
                    () -> e.getPlayer().sendMessage(Component.text("Esta tienda esta cerrada.", NamedTextColor.GRAY)));
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent e) {
        if (vendedores.esVendedor(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTransform(EntityTransformEvent e) {
        if (vendedores.esVendedor(e.getEntity())) e.setCancelled(true); // rayo -> bruja
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCareer(VillagerCareerChangeEvent e) {
        if (vendedores.esVendedor(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTrade(VillagerAcquireTradeEvent e) {
        if (vendedores.esVendedor(e.getEntity())) e.setCancelled(true);
    }

    /** Si cambio el config (nombre u oficio), los vendedores se actualizan al cargarse su chunk. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (var ent : e.getEntities()) {
            if (ent instanceof Villager v) {
                vendedores.tiendaDe(v).flatMap(tiendas::tienda).ifPresent(t -> vendedores.aplicar(v, t));
            }
        }
    }
}
