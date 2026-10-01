package com.isjbar.minercorp.territory.listeners;

import com.isjbar.minercorp.territory.api.TerritoryAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;
import java.util.UUID;

/** Protege los chunks reclamados: solo los autorizados por el dueno pueden romper bloques ahi. */
public class ProtectionListener implements Listener {

    private final TerritoryAPI territory;

    public ProtectionListener(TerritoryAPI territory) {
        this.territory = territory;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<UUID> ownerOpt = territory.getOwner(event.getBlock().getChunk());
        if (ownerOpt.isEmpty()) return;

        UUID ownerId = ownerOpt.get();
        if (territory.isAuthorized(ownerId, player.getUniqueId()) || player.hasPermission("minercorp.admin")) {
            return;
        }

        event.setCancelled(true);
        player.sendActionBar(Component.text("Este territorio esta reclamado y no te pertenece", NamedTextColor.RED));
    }
}
