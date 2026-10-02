package com.isjbar.minercorp.resources.machine;

import com.isjbar.minercorp.resources.ResourcesPlugin;
import com.isjbar.minercorp.resources.resource.ResourceRegistry;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * Colocar, abrir, usar y romper maquinas, y que no se rompan por explosiones
 * ni pistones. Solo la empresa duena del territorio (y los admins) puede
 * tocarlas.
 */
public class MachineListener implements Listener {

    private final ResourcesPlugin plugin;
    private final MachineManager machines;
    private final ResourceRegistry resources;
    private final TerritoryAPI territory;

    public MachineListener(ResourcesPlugin plugin, MachineManager machines, ResourceRegistry resources, TerritoryAPI territory) {
        this.plugin = plugin;
        this.machines = machines;
        this.resources = resources;
        this.territory = territory;
    }

    // -------------------------------------------------------- colocar/romper

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<MachineType> type = machines.identifyItem(event.getItemInHand());
        if (type.isEmpty()) return;
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Optional<UUID> owner = territory.getOwner(block.getChunk());
        boolean admin = player.hasPermission("minercorp.admin");
        if (owner.isEmpty() && !admin) {
            event.setCancelled(true);
            msg(player, NamedTextColor.RED, "Las maquinas solo se colocan en el territorio de tu empresa.");
            return;
        }
        if (owner.isPresent() && !territory.isAuthorized(owner.get(), player.getUniqueId()) && !admin) {
            event.setCancelled(true);
            msg(player, NamedTextColor.RED, "Este territorio es de otra empresa.");
            return;
        }
        if (type.get().kind() == MachineType.Kind.VETA_CARBON && territory.getVein(block.getChunk()).isEmpty()) {
            msg(player, NamedTextColor.GOLD, "Ojo: este chunk no tiene veta de carbon, la perforadora no va a sacar nada.");
        }
        if (type.get().kind() == MachineType.Kind.PETROLEO && plugin.oil().remaining(block.getChunk()) <= 0) {
            msg(player, NamedTextColor.GOLD, "Ojo: este chunk no tiene petroleo (proba /recursos sondear en otros chunks).");
        }
        int facing = Math.floorMod(Math.round(player.getLocation().getYaw() / 90f), 4);
        String problem = machines.placementProblem(type.get(), block, facing, owner.orElse(null));
        if (problem != null) {
            event.setCancelled(true);
            msg(player, NamedTextColor.RED, problem + " El cuerpo se arma detras del panel, del lado opuesto a donde estas.");
            return;
        }
        machines.place(type.get(), block, owner.orElse(null), facing);
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        msg(player, NamedTextColor.GREEN, type.get().name() + " colocada. Click derecho para cargarla.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<Machine> machine = machines.at(event.getBlock());
        if (machine.isEmpty()) return;
        Player player = event.getPlayer();
        if (!event.getBlock().equals(machine.get().block())) {
            // Barrera del cuerpo: la maquina se desarma rompiendo el panel.
            event.setCancelled(true);
            msg(player, NamedTextColor.YELLOW, "Para desarmar la maquina rompe su panel de control.");
            return;
        }
        if (!canUse(player, machine.get())) {
            event.setCancelled(true);
            msg(player, NamedTextColor.RED, "Esta maquina es de otra empresa.");
            return;
        }
        event.setDropItems(false);
        Location drop = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack item : machines.remove(machine.get())) {
            drop.getWorld().dropItemNaturally(drop, item);
        }
        msg(player, NamedTextColor.YELLOW, machine.get().type().name() + " desarmada.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> machines.at(b).isPresent());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> machines.at(b).isPresent());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> machines.at(b).isPresent())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> machines.at(b).isPresent())) event.setCancelled(true);
    }

    // ------------------------------------------------------------------ abrir

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Optional<Machine> machine = machines.at(event.getClickedBlock());
        if (machine.isEmpty()) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            // Shift + click con algo en la mano: cargarlo directo sin abrir el panel.
            quickLoad(player, machine.get());
            return;
        }
        if (!canUse(player, machine.get())) {
            msg(player, NamedTextColor.RED, "Esta maquina es de otra empresa.");
            return;
        }
        machine.get().refreshPanel();
        player.openInventory(machine.get().inventory());
    }

    private void quickLoad(Player player, Machine machine) {
        if (!canUse(player, machine)) {
            msg(player, NamedTextColor.RED, "Esta maquina es de otra empresa.");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        int slot = targetSlot(machine, hand);
        if (slot < 0) {
            msg(player, NamedTextColor.RED, "La " + machine.type().name() + " no usa eso.");
            return;
        }
        ItemStack rest = merge(machine.inventory(), slot, hand);
        player.getInventory().setItemInMainHand(rest);
        machines.markDirty();
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, 0.8f, 1f);
    }

    // ------------------------------------------------------------------ panel

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MachineHolder holder)) return;
        Machine machine = holder.machine();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        // Juntar todo en el cursor (doble click) podria sacar los vidrios del panel.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (clicked != top) {
            // Shift + click desde el inventario del jugador: lo mandamos al slot que corresponde.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                int slot = targetSlot(machine, item);
                if (slot >= 0) {
                    event.setCurrentItem(merge(top, slot, item));
                    machines.markDirty();
                }
            }
            return;
        }

        int slot = event.getSlot();
        if (!machine.isFunctionalSlot(slot)) {
            event.setCancelled(true);
            return;
        }
        machines.markDirty();
        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            // Simple: desde el panel solo se saca o se pone con el mouse.
            event.setCancelled(true);
            return;
        }
        ItemStack cursor = event.getCursor();
        boolean placing = cursor != null && !cursor.getType().isAir();
        if (!placing) return; // sacar siempre se puede (incluido shift + click)
        if (Machine.isOutputSlot(slot)) {
            event.setCancelled(true);
            return;
        }
        Optional<String> key = resources.itemKey(cursor);
        boolean ok = slot == Machine.SLOT_FUEL
                ? key.map(machine.type()::acceptsFuel).orElse(false)
                : key.map(machine.type()::acceptsInput).orElse(false);
        if (!ok) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MachineHolder)) return;
        int size = top.getSize();
        if (event.getRawSlots().stream().anyMatch(s -> s < size)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MachineHolder) machines.markDirty();
    }

    // ----------------------------------------------------------------- chunks

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        machines.onChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        machines.onChunkUnload(event.getChunk());
    }

    // ---------------------------------------------------------------- helpers

    private boolean canUse(Player player, Machine machine) {
        if (player.hasPermission("minercorp.admin")) return true;
        return machine.owner() == null || territory.isAuthorized(machine.owner(), player.getUniqueId());
    }

    /** Slot del panel donde va ese item (entrada antes que combustible), o -1 si la maquina no lo usa. */
    private int targetSlot(Machine machine, ItemStack item) {
        Optional<String> key = resources.itemKey(item);
        if (key.isEmpty()) return -1;
        if (!machine.type().isExtractor() && machine.type().acceptsInput(key.get())) return Machine.SLOT_INPUT;
        if (machine.type().acceptsFuel(key.get())) return Machine.SLOT_FUEL;
        return -1;
    }

    /** Mete todo lo que entre de {@code item} en el slot; devuelve lo que sobra (o null). */
    private static ItemStack merge(Inventory inv, int slot, ItemStack item) {
        ItemStack in = inv.getItem(slot);
        if (in == null || in.getType().isAir()) {
            inv.setItem(slot, item.clone());
            return null;
        }
        if (!in.isSimilar(item)) return item;
        int add = Math.min(item.getAmount(), in.getMaxStackSize() - in.getAmount());
        in.setAmount(in.getAmount() + add);
        inv.setItem(slot, in);
        if (add >= item.getAmount()) return null;
        ItemStack rest = item.clone();
        rest.setAmount(item.getAmount() - add);
        return rest;
    }

    private static void msg(Player player, NamedTextColor color, String text) {
        player.sendMessage(Component.text("[Recursos] ", NamedTextColor.DARK_AQUA).append(Component.text(text, color)));
    }
}
