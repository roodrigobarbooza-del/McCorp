package com.isjbar.minercorp.vehicles.gui;

import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.api.PurchaseResult;
import com.isjbar.minercorp.vehicles.vehicle.VehicleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.isjbar.minercorp.mining.company.Company;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

public class MenuListener implements Listener {

    private final VehiclesPlugin plugin;

    public MenuListener(VehiclesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(Menus.actionKey(plugin), PersistentDataType.STRING);
        if (action == null) return;

        if (action.equals(Menus.CERRAR)) {
            player.closeInventory();
        } else if (action.equals(Menus.TIENDA)) {
            Menus.openShop(plugin, player);
        } else if (action.equals(Menus.GARAJE)) {
            Menus.openGarage(plugin, player);
        } else if (action.startsWith(Menus.SACAR) && holder.screen() == MenuHolder.Screen.GARAJE) {
            String[] parts = action.substring(Menus.SACAR.length()).split(":");
            UUID owner = UUID.fromString(parts[0]);
            int index = Integer.parseInt(parts[1]);
            // Solo su garaje o el de su empresa.
            boolean allowed = owner.equals(player.getUniqueId()) || plugin.mining().companies().getById(owner)
                    .map(c -> c.isMember(player.getUniqueId())).orElse(false);
            if (!allowed) return;
            VehicleManager.DeployResult result = plugin.vehicles().deploy(player, owner, index);
            player.closeInventory();
            player.sendMessage(Component.text(result.message(),
                    result == VehicleManager.DeployResult.OK ? NamedTextColor.GREEN : NamedTextColor.RED));
        } else if (holder.screen() == MenuHolder.Screen.PANEL) {
            panel(player, holder, action);
        } else if (action.startsWith(Menus.COMPRAR) && holder.screen() == MenuHolder.Screen.TIENDA) {
            if (!plugin.getConfig().getBoolean("tienda-por-comando", true)) return;
            PurchaseResult result = plugin.service().purchase(player, action.substring(Menus.COMPRAR.length()));
            player.sendMessage(Component.text(result.message(),
                    result == PurchaseResult.OK ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result == PurchaseResult.OK) Menus.openGarage(plugin, player);
        }
    }

    /** Botones del panel de control del taladro. */
    private void panel(Player player, MenuHolder holder, String action) {
        VehicleManager vehicles = plugin.vehicles();
        if (!(plugin.getServer().getEntity(holder.target()) instanceof BlockDisplay root) || !vehicles.isRoot(root)) {
            player.closeInventory();
            return;
        }
        if (!vehicles.canUse(player, root) || player.getLocation().distanceSquared(root.getLocation()) > 12 * 12) {
            player.closeInventory();
            return;
        }
        switch (action) {
            case Menus.MANEJAR -> {
                player.closeInventory();
                if (player.isInsideVehicle()) return;
                VehicleManager.SitResult r = vehicles.sit(player, root);
                if (r != VehicleManager.SitResult.CONDUCTOR) {
                    player.sendActionBar(Component.text("Ya hay alguien manejando", NamedTextColor.RED));
                }
            }
            case Menus.CARGAR_INVENTARIO -> {
                int used = vehicles.refuelFromInventory(player, root);
                player.sendActionBar(Component.text(used > 0
                        ? "Cargaste " + used + " items. Combustible: " + (int) Math.ceil(vehicles.fuelOf(root))
                        : "No tenes combustible que sirva o el tanque ya esta lleno",
                        used > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
                Menus.openPanel(plugin, player, root);
            }
            case Menus.CARGAR_EMPRESA -> {
                Optional<Company> company = vehicles.ownerOf(root).flatMap(id -> plugin.mining().companies().getById(id));
                double used = company.map(c -> vehicles.refuelFromRawCoal(c, root, c.getRawCoal())).orElse(0.0);
                player.sendActionBar(Component.text(used > 0
                        ? "Usaste " + Math.round(used * 100.0) / 100.0 + " de carbon crudo de la empresa"
                        : "La empresa no tiene carbon crudo o el tanque ya esta lleno",
                        used > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
                Menus.openPanel(plugin, player, root);
            }
            case Menus.GUARDAR -> {
                player.closeInventory();
                if (vehicles.store(root)) {
                    player.sendMessage(Component.text("Guardado en el garaje. Sacalo con /garaje.", NamedTextColor.GREEN));
                } else {
                    player.sendActionBar(Component.text("No se puede guardar con gente arriba", NamedTextColor.RED));
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof MenuHolder) event.setCancelled(true);
    }
}
