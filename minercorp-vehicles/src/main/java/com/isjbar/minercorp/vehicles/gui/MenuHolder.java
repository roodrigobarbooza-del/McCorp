package com.isjbar.minercorp.vehicles.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marca los menus de este plugin. */
public final class MenuHolder implements InventoryHolder {

    public enum Screen { GARAJE, TIENDA }

    private final Screen screen;
    private Inventory inventory;

    public MenuHolder(Screen screen) {
        this.screen = screen;
    }

    public Screen screen() {
        return screen;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
