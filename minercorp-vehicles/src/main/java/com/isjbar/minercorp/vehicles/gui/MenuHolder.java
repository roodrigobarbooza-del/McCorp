package com.isjbar.minercorp.vehicles.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Marca los menus de este plugin. */
public final class MenuHolder implements InventoryHolder {

    public enum Screen { GARAJE, TIENDA, PANEL }

    private final Screen screen;
    private final UUID target;
    private Inventory inventory;

    public MenuHolder(Screen screen) {
        this(screen, null);
    }

    /** @param target raiz del vehiculo del menu (panel de control), o null */
    public MenuHolder(Screen screen, UUID target) {
        this.screen = screen;
        this.target = target;
    }

    public Screen screen() {
        return screen;
    }

    public UUID target() {
        return target;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
