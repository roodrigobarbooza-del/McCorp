package com.isjbar.minercorp.gransede.tienda;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marca los menus de tiendas de la Gran Sede y recuerda que estaba mirando el jugador. */
public class TiendaHolder implements InventoryHolder {

    public enum Pantalla { LISTA, CONFIRMAR }

    private final Pantalla pantalla;
    private final String tienda;
    private final int pagina;
    private final String producto;
    private Inventory inventory;

    public TiendaHolder(Pantalla pantalla, String tienda, int pagina, String producto) {
        this.pantalla = pantalla;
        this.tienda = tienda;
        this.pagina = pagina;
        this.producto = producto;
    }

    public Pantalla pantalla() { return pantalla; }
    public String tienda() { return tienda; }
    public int pagina() { return pagina; }
    public String producto() { return producto; }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
