package com.isjbar.minercorp.resources.machine;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marca el inventario del panel de una maquina para reconocerlo en los eventos. */
public final class MachineHolder implements InventoryHolder {

    private final Machine machine;
    private Inventory inventory;

    MachineHolder(Machine machine) {
        this.machine = machine;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public Machine machine() {
        return machine;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
