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
 * @param entregar entrega el producto al jugador ya cobrado; devuelve false si no pudo (se le devuelve el dinero)
 */
public record Producto(String id, String tienda, String nombre, ItemStack icono, List<String> lore,
                       double precio, Predicate<Player> entregar) {
}
