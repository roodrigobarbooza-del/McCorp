package com.isjbar.minercorp.vehicles.vehicle;

import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Click derecho a un vehiculo: con combustible en la mano lo carga, con
 * Shift abre la carga, si no te subis. Click izquierdo lo guarda en el garaje.
 */
public class VehicleListener implements Listener {

    private final VehiclesPlugin plugin;
    private final VehicleManager vehicles;
    private final LegacyMigration legacy;

    public VehicleListener(VehiclesPlugin plugin, VehicleManager vehicles, LegacyMigration legacy) {
        this.plugin = plugin;
        this.vehicles = vehicles;
        this.legacy = legacy;
    }

    // Sin ignoreCancelled: las cajas de click son nuestras, y si otro plugin
    // (proteccion de spawn, etc) cancela la interaccion igual hay que poder subirse.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction click)) return;
        Optional<BlockDisplay> rootOpt = vehicles.rootOf(click);
        if (rootOpt.isEmpty()) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        BlockDisplay root = rootOpt.get();
        Player player = event.getPlayer();
        Optional<VehicleType> type = vehicles.typeOf(root);
        if (type.isEmpty()) {
            player.sendActionBar(Component.text("Este vehiculo es de un tipo que ya no existe en el config", NamedTextColor.RED));
            return;
        }
        boolean owner = vehicles.canUse(player, root);

        if (player.isSneaking()) {
            if (!owner) {
                player.sendActionBar(Component.text("La carga solo la abre el dueno", NamedTextColor.RED));
                return;
            }
            vehicles.openCargo(player, root);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.fuels().isAnyFuel(hand)) {
            if (!owner) {
                player.sendActionBar(Component.text("Este vehiculo no es tuyo", NamedTextColor.RED));
                return;
            }
            VehicleManager.RefuelResult r = vehicles.refuelFromItem(root, hand);
            if (r.used() < 0) {
                player.sendActionBar(Component.text("Este vehiculo usa: " + r.acceptedNames(), NamedTextColor.RED));
                return;
            }
            if (r.used() == 0) {
                player.sendActionBar(Component.text("El tanque ya esta lleno", NamedTextColor.YELLOW));
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - r.used());
            player.sendActionBar(Component.text("Combustible: " + (int) Math.ceil(vehicles.fuelOf(root))
                    + "/" + (int) type.get().tanque(), NamedTextColor.GREEN));
            return;
        }

        if (player.isInsideVehicle()) return;
        switch (vehicles.sit(player, root)) {
            case CONDUCTOR -> { }
            case ACOMPANANTE -> player.sendActionBar(Component.text("Vas de acompanante. Shift para bajarte", NamedTextColor.GRAY));
            case SIN_LUGAR -> player.sendActionBar(Component.text(owner ? "No hay asientos libres"
                    : "Este vehiculo no es tuyo y no hay asientos libres", NamedTextColor.RED));
        }
    }

    /** Golpear el vehiculo (click izquierdo) lo guarda en el garaje del dueno. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction click)) return;
        Optional<BlockDisplay> rootOpt = vehicles.rootOf(click);
        if (rootOpt.isEmpty()) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        BlockDisplay root = rootOpt.get();
        if (!vehicles.canUse(player, root)) {
            player.sendActionBar(Component.text("Este vehiculo no es tuyo", NamedTextColor.RED));
            return;
        }
        if (!vehicles.store(root)) {
            player.sendActionBar(Component.text("No se puede guardar con gente arriba", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Vehiculo guardado en el garaje. Sacalo con /garaje.", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Entity vehicle = event.getDismounted();
        if (vehicles.isRoot(vehicle)) {
            vehicles.stopDriving(vehicle.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && vehicles.rootOf(vehicle).isPresent()) {
            vehicle.removePassenger(event.getPlayer());
            if (vehicles.isRoot(vehicle)) vehicles.stopDriving(vehicle.getUniqueId());
        }
    }

    @EventHandler
    public void onCargoClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof VehicleManager.CargoHolder holder) {
            vehicles.onCargoClosed(holder.rootId(), event.getInventory(), event.getPlayer());
        }
    }

    /** Con la cabeza dentro de un bloque (tuneles bajos, cabinas): que no se asfixie. */
    @EventHandler(ignoreCancelled = true)
    public void onSuffocate(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;
        if (event.getEntity() instanceof Player player && vehicles.isRiding(player)) {
            event.setCancelled(true);
        }
    }

    /** Convierte taladros de MinerCorp-Mining cuando se carga su chunk. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> entities = event.getEntities().stream()
                .filter(e -> e instanceof BlockDisplay || e instanceof org.bukkit.entity.Boat).toList();
        if (entities.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> legacy.convert(entities));
    }
}
