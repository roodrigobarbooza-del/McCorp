package com.isjbar.minercorp.gransede.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * Algo que se vende en una tienda de la Gran Sede.
 *
 * @param id       identificador unico (ej: "camion-cisterna"); registrar otro con el mismo id lo reemplaza
 * @param tienda   id de la tienda donde aparece ("concesionaria", "taladros", "ferreteria" u otra del config)
 * @param nombre   nombre legible
 * @param icono    item que se muestra en el menu (se clona, no se entrega)
 * @param lore     lineas extra de descripcion
 * @param precio   precio en la billetera personal del jugador
 * @param entregar entrega el producto al jugador; devuelve false si no pudo
 * @param cobraPropio false: la Gran Sede cobra {@code precio} de la billetera personal antes de
 *                    entregar y lo devuelve si {@code entregar} da false. true: {@code entregar}
 *                    cobra por su cuenta (ej. de la cuenta de la empresa) y avisa al jugador si
 *                    falla; {@code precio} es solo lo que muestra el menu.
 */
public record Producto(String id, String tienda, String nombre, ItemStack icono, List<String> lore,
                       double precio, Predicate<Player> entregar, boolean cobraPropio) {

    /** Producto que cobra la Gran Sede de la billetera personal. */
    public Producto(String id, String tienda, String nombre, ItemStack icono, List<String> lore,
                    double precio, Predicate<Player> entregar) {
        this(id, tienda, nombre, icono, lore, precio, entregar, false);
    }
}
