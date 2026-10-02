package com.isjbar.minercorp.vehicles.api;

import org.bukkit.Material;

import java.util.List;

/**
 * Un vehiculo a la venta.
 *
 * @param id           id del tipo (para {@link VehiclesAPI#purchase})
 * @param name         nombre legible
 * @param description  lineas de descripcion para el lore
 * @param price        precio
 * @param icon         material para mostrarlo en un menu
 * @param owner        quien lo paga y es dueno
 * @param companyLevel nivel minimo de empresa (solo si owner es EMPRESA)
 */
public record VehicleOffer(String id, String name, List<String> description, double price, Material icon,
                           OwnerKind owner, int companyLevel) {
}
