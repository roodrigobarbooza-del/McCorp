package com.isjbar.minercorp.mining.vehicle;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Click derecho al taladro: con carbon en la mano lo carga de combustible,
 * con cualquier otra cosa te subis. Solo miembros de la empresa duena.
 */
public class DrillVehicleListener implements Listener {

    private final MiningPlugin plugin;
    private final DrillVehicleManager vehicles;

    public DrillVehicleListener(MiningPlugin plugin, DrillVehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction seat)) return;
        Optional<BlockDisplay> rootOpt = vehicles.rootOf(seat);
        if (rootOpt.isEmpty()) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        BlockDisplay root = rootOpt.get();
        Player player = event.getPlayer();
        Optional<UUID> companyId = vehicles.companyOf(root);
        Optional<Company> company = companyId.flatMap(id -> plugin.companies().getById(id));
        if (company.isEmpty() || !company.get().isMember(player.getUniqueId())) {
            player.sendActionBar(Component.text("Este taladro no es de tu empresa", NamedTextColor.RED));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (vehicles.isFuelItem(hand.getType())) {
            int used = vehicles.refuelFromItem(root, hand);
            if (used <= 0) {
                player.sendActionBar(Component.text("El tanque ya esta lleno", NamedTextColor.YELLOW));
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - used);
            DrillTier tier = vehicles.tierOf(root);
            player.sendActionBar(Component.text("Combustible: " + (int) Math.ceil(vehicles.fuelOf(root))
                    + "/" + (int) tier.combustible(), NamedTextColor.GREEN));
            return;
        }

        if (player.isInsideVehicle()) return;
        if (!root.getPassengers().isEmpty()) {
            player.sendActionBar(Component.text("Ya hay alguien manejando este taladro", NamedTextColor.RED));
            return;
        }
        if (root.addPassenger(player)) {
            vehicles.startDriving(root, player);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Entity vehicle = event.getDismounted();
        if (vehicles.isDrillRoot(vehicle)) {
            vehicles.stopDriving(vehicle.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && vehicles.isDrillRoot(vehicle)) {
            vehicle.eject();
            vehicles.stopDriving(vehicle.getUniqueId());
        }
    }

    /** En tuneles bajos la cabeza del conductor puede quedar dentro de un bloque: que no se asfixie. */
    @EventHandler(ignoreCancelled = true)
    public void onSuffocate(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;
        if (event.getEntity() instanceof Player player && vehicles.isDriving(player)) {
            event.setCancelled(true);
        }
    }

    /** Convierte botes-taladro de la version anterior cuando se carga su chunk. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        var boats = event.getEntities().stream().filter(e -> e instanceof org.bukkit.entity.Boat).toList();
        if (boats.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> vehicles.convertLegacy(
                boats.stream().filter(Entity::isValid).toList()));
    }
}
