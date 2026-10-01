package com.isjbar.minercorp.territory;

import com.isjbar.minercorp.territory.util.ChunkKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Avisa por action bar cuando un jugador entra a un chunk reclamado, con el
 * nombre del dueno y el proposito del territorio. No es especifico de
 * mineria: vive aca porque Territory ya tiene toda la info necesaria
 * (ownerLabel + purpose) sin tener que conocer "empresas" ni "carbon".
 */
class TerritoryEntryListener implements Listener {

    private final TerritoryManager manager;
    /** Ultimo claim mostrado por jugador, para no repetir el aviso mientras siga adentro. No se persiste (se resetea solo). */
    private final Map<UUID, ChunkKey> ultimoMostrado = new HashMap<>();

    TerritoryEntryListener(TerritoryManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (mismoChunk(event.getFrom(), event.getTo())) return;
        check(event.getPlayer(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (mismoChunk(event.getFrom(), event.getTo())) return;
        check(event.getPlayer(), event.getTo());
    }

    private boolean mismoChunk(Location from, Location to) {
        if (to == null || from.getWorld() != to.getWorld()) return false;
        return (from.getBlockX() >> 4) == (to.getBlockX() >> 4) && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4);
    }

    private void check(Player player, Location destino) {
        ChunkKey key = ChunkKey.of(destino);
        Optional<Claim> claim = manager.claimAt(key);

        if (claim.isEmpty()) {
            ultimoMostrado.remove(player.getUniqueId());
            return;
        }

        if (key.equals(ultimoMostrado.get(player.getUniqueId()))) return;
        ultimoMostrado.put(player.getUniqueId(), key);

        Claim c = claim.get();
        String texto = "Territorio de " + c.getOwnerLabel()
                + (c.getPurpose() == null || c.getPurpose().isBlank() ? "" : " - " + c.getPurpose());
        player.sendActionBar(Component.text(texto, NamedTextColor.YELLOW));
    }
}
