package com.isjbar.minercorp.mining.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marcador vacio para distinguir los inventarios del menu de MinerCorp de cualquier otro cofre/GUI. */
public class MenuHolder implements InventoryHolder {

    public enum Pantalla {
        PRINCIPAL
    }

    private final Pantalla pantalla;
    private Inventory inventory;

    public MenuHolder(Pantalla pantalla) {
        this.pantalla = pantalla;
    }

    public Pantalla getPantalla() {
        return pantalla;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
