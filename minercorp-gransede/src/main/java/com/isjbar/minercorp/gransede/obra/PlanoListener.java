package com.isjbar.minercorp.gransede.obra;

import com.isjbar.minercorp.gransede.zona.ZonaProteccionListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Clic derecho con el Plano de la Gran Sede: elegir el lugar. */
public class PlanoListener implements Listener {

    private final ObraGranSedeManager obras;

    public PlanoListener(ObraGranSedeManager obras) {
        this.obras = obras;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!obras.esPlano(e.getItem())) return;
        e.setCancelled(true);
        if (!e.getPlayer().hasPermission(ZonaProteccionListener.PERMISO_ADMIN)) return;
        obras.elegir(e.getPlayer());
    }
}
