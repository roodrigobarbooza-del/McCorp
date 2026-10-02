package com.isjbar.minercorp.gransede.zona;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Reglas generales de la Gran Sede. Nadie pone bloques en ninguna zona ni
 * rompe en la zona SEDE (lo que se puede romper en la mina y el bosque lo
 * decide {@code TrabajoListener}). Un admin en creativo puede construir.
 */
public class ZonaProteccionListener implements Listener {

    public static final String PERMISO_ADMIN = "minercorp.gransede.admin";

    private final ZonaManager zonas;
    private final boolean sinPvp, sinMobs, sinExplosiones, sinFuego;

    public ZonaProteccionListener(ZonaManager zonas, ConfigurationSection proteccion) {
        this.zonas = zonas;
        this.sinPvp = proteccion == null || proteccion.getBoolean("sin-pvp", true);
        this.sinMobs = proteccion == null || proteccion.getBoolean("sin-mobs-hostiles", true);
        this.sinExplosiones = proteccion == null || proteccion.getBoolean("sin-explosiones", true);
        this.sinFuego = proteccion == null || proteccion.getBoolean("sin-fuego", true);
    }

    public static boolean puedeConstruir(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission(PERMISO_ADMIN);
    }

    private boolean enGranSede(Location loc) {
        return zonas.at(loc).isPresent();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (enGranSede(e.getBlock().getLocation()) && !puedeConstruir(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        zonas.at(e.getBlock().getLocation()).ifPresent(z -> {
            if (z.tipo() == ZonaTipo.SEDE && !puedeConstruir(e.getPlayer())) e.setCancelled(true);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (enGranSede(e.getBlock().getLocation()) && !puedeConstruir(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (enGranSede(e.getBlock().getLocation()) && !puedeConstruir(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!sinPvp || !(e.getEntity() instanceof Player)) return;
        Entity atacante = e.getDamager();
        if (atacante instanceof Projectile proj && proj.getShooter() instanceof Entity tirador) atacante = tirador;
        if (atacante instanceof Player && enGranSede(e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!sinMobs || !(e.getEntity() instanceof Enemy)) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.COMMAND || r == CreatureSpawnEvent.SpawnReason.CUSTOM
                || r == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        if (enGranSede(e.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (sinExplosiones) e.blockList().removeIf(b -> enGranSede(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (sinExplosiones) e.blockList().removeIf(b -> enGranSede(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!sinFuego || !enGranSede(e.getBlock().getLocation())) return;
        if (e.getPlayer() != null && puedeConstruir(e.getPlayer())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (sinFuego && enGranSede(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        if (sinFuego && enGranSede(e.getBlock().getLocation())
                && e.getNewState().getType() == org.bukkit.Material.FIRE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!(e.getEntity() instanceof Player) && enGranSede(e.getBlock().getLocation())) e.setCancelled(true);
    }
}
