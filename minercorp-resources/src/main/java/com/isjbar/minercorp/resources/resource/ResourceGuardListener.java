package com.isjbar.minercorp.resources.resource;

import com.isjbar.minercorp.resources.machine.MachineManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Los recursos son items vanilla con una marca: esto evita que se usen como
 * el item vanilla (el carbon crudo no sirve en un horno comun, el bidon de
 * gasolina no sirve de tinte, el asfalto no se coloca, etc). Todo lo que se hace
 * con ellos pasa por las maquinas.
 */
public class ResourceGuardListener implements Listener {

    private final ResourceRegistry resources;
    private final MachineManager machines;

    public ResourceGuardListener(ResourceRegistry resources, MachineManager machines) {
        this.resources = resources;
        this.machines = machines;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (resources.isResource(event.getFuel())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (resources.isResource(event.getSource())) event.setCancelled(true);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (resources.isResource(item) || machines.identifyItem(item).isPresent()) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        // Las maquinas manejan sus propios clicks (MachineListener).
        if (event.getClickedBlock() != null && machines.at(event.getClickedBlock()).isPresent()) return;
        if (resources.isResource(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUseOnEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (resources.isResource(item)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (resources.isResource(event.getItem())) event.setCancelled(true);
    }
}
