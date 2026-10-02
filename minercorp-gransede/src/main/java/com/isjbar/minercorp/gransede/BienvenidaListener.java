package com.isjbar.minercorp.gransede;

import com.isjbar.minercorp.gransede.tienda.TiendaMenu;
import com.isjbar.minercorp.gransede.zona.ZonaManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Supplier;

/** Lleva a los jugadores nuevos a la Gran Sede y les explica como empezar. */
public class BienvenidaListener implements Listener {

    private final Plugin plugin;
    private final ZonaManager zonas;
    private final Supplier<Boolean> activo;
    private final Supplier<List<String>> mensaje;

    public BienvenidaListener(Plugin plugin, ZonaManager zonas, Supplier<Boolean> activo, Supplier<List<String>> mensaje) {
        this.plugin = plugin;
        this.zonas = zonas;
        this.activo = activo;
        this.mensaje = mensaje;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.hasPlayedBefore() || !activo.get()) return;
        // Un tick despues, para que no lo pise el spawn por defecto del mundo.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            zonas.spawn().ifPresent(p::teleport);
            for (String l : mensaje.get()) p.sendMessage(TiendaMenu.legacy(l));
        });
    }
}
