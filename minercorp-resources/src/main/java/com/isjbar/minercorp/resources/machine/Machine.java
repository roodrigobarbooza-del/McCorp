package com.isjbar.minercorp.resources.machine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Una maquina colocada en el mundo: su bloque, su empresa duena, su
 * inventario (entrada, combustible, salidas) y en que va de su ciclo.
 *
 * Panel (cofre de 3 filas):
 * <pre>
 *   . . . . i . . . .      i = estado
 *   . E . p . S S S .      E = entrada, p = progreso, S = salidas
 *   . C f . . S S S .      C = combustible, f = fuego
 * </pre>
 */
public class Machine {

    public static final int SLOT_INFO = 4;
    public static final int SLOT_INPUT = 10;
    public static final int SLOT_PROGRESS = 12;
    public static final int SLOT_FUEL = 19;
    public static final int SLOT_FLAME = 20;
    public static final int[] SLOT_OUTPUTS = {14, 15, 16, 23, 24, 25};

    /** Por que la maquina esta parada (o que esta trabajando). */
    public enum Status {
        TRABAJANDO("Trabajando", NamedTextColor.GREEN),
        SIN_COMBUSTIBLE("Sin combustible", NamedTextColor.RED),
        SIN_ENTRADA("Sin materia prima", NamedTextColor.GOLD),
        SALIDA_LLENA("Salida llena", NamedTextColor.GOLD),
        VETA_AGOTADA("Sin carbon en este chunk", NamedTextColor.RED),
        SIN_PETROLEO("Sin petroleo en este chunk", NamedTextColor.RED);

        final String text;
        final NamedTextColor color;

        Status(String text, NamedTextColor color) {
            this.text = text;
            this.color = color;
        }

        public Component component() {
            return Component.text(text, color);
        }
    }

    private final UUID id;
    private final MachineType type;
    private final Block block;
    private final UUID owner;
    private final int facing;
    private final Inventory inventory;

    int burnLeft;
    int burnMax;
    int progress;
    /** Fraccion extraida que todavia no llega a un item entero. */
    double pending;
    Status status = Status.SIN_COMBUSTIBLE;
    MachineModel model;

    Machine(UUID id, MachineType type, Block block, UUID owner, int facing) {
        this.id = id;
        this.type = type;
        this.block = block;
        this.owner = owner;
        this.facing = facing;
        MachineHolder holder = new MachineHolder(this);
        this.inventory = Bukkit.createInventory(holder, 27, Component.text(type.name()));
        holder.bind(inventory);
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!isFunctionalSlot(i)) inventory.setItem(i, filler);
        }
        if (type.isExtractor()) {
            inventory.setItem(SLOT_INPUT, pane(Material.BROWN_STAINED_GLASS_PANE,
                    type.kind() == MachineType.Kind.PETROLEO ? "Bombea del yacimiento del chunk" : "Extrae de la veta del chunk",
                    List.of()));
        }
    }

    public UUID id() {
        return id;
    }

    public MachineType type() {
        return type;
    }

    public Block block() {
        return block;
    }

    /** Empresa (o duena generica del territorio) a la que pertenece; null si la puso un admin fuera de territorio. */
    public UUID owner() {
        return owner;
    }

    public int facing() {
        return facing;
    }

    public Inventory inventory() {
        return inventory;
    }

    public Status status() {
        return status;
    }

    public boolean isRunning() {
        return status == Status.TRABAJANDO;
    }

    /** Slots donde el jugador puede poner o sacar items. */
    public boolean isFunctionalSlot(int slot) {
        if (slot == SLOT_FUEL || isOutputSlot(slot)) return true;
        return slot == SLOT_INPUT && !type.isExtractor();
    }

    public static boolean isOutputSlot(int slot) {
        for (int s : SLOT_OUTPUTS) if (s == slot) return true;
        return false;
    }

    /** Items que tiene que devolver al romperse: entrada, combustible y salidas. */
    public List<ItemStack> contents() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!isFunctionalSlot(i)) continue;
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) items.add(item);
        }
        return items;
    }

    /** Refresca los iconos de estado, progreso y fuego del panel. */
    void refreshPanel() {
        int pct = (int) Math.round(100.0 * progress / type.cycleTicks());
        int fuelPct = burnMax <= 0 ? 0 : (int) Math.round(100.0 * burnLeft / burnMax);
        inventory.setItem(SLOT_INFO, pane(isRunning() ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                type.name(), List.of(status.component(), Component.text("Ciclo: " + (type.cycleTicks() / 20.0) + " s", NamedTextColor.GRAY))));
        inventory.setItem(SLOT_PROGRESS, pane(Material.SPECTRAL_ARROW, "Progreso: " + pct + "%",
                List.of(Component.text(bar(pct), NamedTextColor.GREEN))));
        inventory.setItem(SLOT_FLAME, pane(burnLeft > 0 ? Material.BLAZE_POWDER : Material.GUNPOWDER,
                "Combustible: " + fuelPct + "%", List.of(Component.text(bar(fuelPct), NamedTextColor.GOLD))));
    }

    private static String bar(int pct) {
        int filled = Math.max(0, Math.min(10, pct / 10));
        return "|".repeat(filled * 2) + ".".repeat((10 - filled) * 2);
    }

    static ItemStack pane(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        List<Component> clean = new ArrayList<>();
        for (Component c : lore) clean.add(c.decoration(TextDecoration.ITALIC, false));
        meta.lore(clean);
        item.setItemMeta(meta);
        return item;
    }
}
