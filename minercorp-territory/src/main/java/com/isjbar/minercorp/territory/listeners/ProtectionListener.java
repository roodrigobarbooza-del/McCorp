package com.isjbar.minercorp.territory.listeners;

import com.isjbar.minercorp.territory.api.TerritoryAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Protege los chunks reclamados de lo que hacen los jugadores: solo los
 * autorizados por el dueno (o quien tenga minercorp.admin) pueden romper o
 * poner bloques, usar cubos, abrir contenedores y puertas, prender fuego, o
 * tocar marcos, soportes de armadura y vehiculos dentro del territorio.
 *
 * Lo que pasa sin un jugador de por medio (explosiones, fuego, liquidos,
 * pistones, etc) lo cubre {@link EnvironmentProtectionListener}.
 */
public class ProtectionListener implements Listener {

    private static final String BYPASS = "minercorp.admin";

    private final TerritoryAPI territory;

    public ProtectionListener(TerritoryAPI territory) {
        this.territory = territory;
    }

    // ---------------------------------------------------------------
    // Bloques
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        bloquear(event, event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        bloquear(event, event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        bloquear(event, event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        bloquear(event, event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        bloquear(event, event.getPlayer(), event.getBlock());
    }

    /** Mechero, cargas de fuego, etc. El fuego que se propaga solo lo maneja EnvironmentProtectionListener. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() == null) return;
        bloquear(event, event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (event.getPlayer() == null) return;
        bloquear(event, event.getPlayer(), event.getBlock());
    }

    /**
     * Click derecho sobre bloques con interfaz o mecanismo (cofres, hornos,
     * puertas, palancas, botones, etc) y pisar bloques (placas de presion,
     * pisotear cultivos). Se niega solo el uso del bloque, no el del item en
     * mano, para que el jugador pueda seguir comiendo o usando su arco.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.useInteractedBlock() == Event.Result.DENY) return;

        if (event.getAction() == Action.PHYSICAL) {
            if (!puede(event.getPlayer(), block.getChunk())) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !block.getType().isInteractable()) return;
        if (puede(event.getPlayer(), block.getChunk())) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        avisar(event.getPlayer());
    }

    // ---------------------------------------------------------------
    // Entidades: marcos, cuadros, soportes de armadura y vehiculos
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        boolean protegida = entity instanceof Hanging
                || entity instanceof ArmorStand
                || (entity instanceof Vehicle && entity instanceof InventoryHolder);
        if (!protegida) return;
        bloquear(event, event.getPlayer(), entity.getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        bloquear(event, event.getPlayer(), event.getRightClicked().getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        Entity victima = event.getEntity();
        if (!(victima instanceof Hanging || victima instanceof ArmorStand || victima instanceof Vehicle)) return;
        Player atacante = jugadorDe(event.getDamager());
        if (atacante == null) return;
        bloquear(event, atacante, victima.getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player jugador = jugadorDe(event.getRemover());
        if (jugador == null) return;
        bloquear(event, jugador, event.getEntity().getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null) return;
        bloquear(event, event.getPlayer(), event.getEntity().getLocation().getChunk());
    }

    /** Soportes de armadura, botes, vagonetas y cristales del End colocados a mano. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getPlayer() == null) return;
        bloquear(event, event.getPlayer(), event.getEntity().getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player atacante = jugadorDe(event.getAttacker());
        if (atacante == null) return;
        bloquear(event, atacante, event.getVehicle().getLocation().getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player atacante = jugadorDe(event.getAttacker());
        if (atacante == null) return;
        bloquear(event, atacante, event.getVehicle().getLocation().getChunk());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void bloquear(Cancellable event, Player player, Block block) {
        bloquear(event, player, block.getChunk());
    }

    private void bloquear(Cancellable event, Player player, Chunk chunk) {
        if (puede(player, chunk)) return;
        event.setCancelled(true);
        avisar(player);
    }

    private boolean puede(Player player, Chunk chunk) {
        Optional<UUID> owner = territory.getOwner(chunk);
        return owner.isEmpty()
                || territory.isAuthorized(owner.get(), player.getUniqueId())
                || player.hasPermission(BYPASS);
    }

    private void avisar(Player player) {
        player.sendActionBar(Component.text("Este territorio esta reclamado y no te pertenece", NamedTextColor.RED));
    }

    /** El jugador detras de un golpe: el mismo jugador, o quien disparo la flecha/tridente/etc. */
    private static Player jugadorDe(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
        return null;
    }
}
