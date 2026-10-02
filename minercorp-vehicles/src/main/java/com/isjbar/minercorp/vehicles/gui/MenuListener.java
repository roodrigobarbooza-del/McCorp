package com.isjbar.minercorp.vehicles.gui;

import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.api.PurchaseResult;
import com.isjbar.minercorp.vehicles.vehicle.VehicleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
        } else if (action.startsWith(Menus.COMPRAR) && holder.screen() == MenuHolder.Screen.TIENDA) {
            if (!plugin.getConfig().getBoolean("tienda-por-comando", true)) return;
            PurchaseResult result = plugin.service().purchase(player, action.substring(Menus.COMPRAR.length()));
            player.sendMessage(Component.text(result.message(),
                    result == PurchaseResult.OK ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result == PurchaseResult.OK) Menus.openGarage(plugin, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof MenuHolder) event.setCancelled(true);
    }
}
