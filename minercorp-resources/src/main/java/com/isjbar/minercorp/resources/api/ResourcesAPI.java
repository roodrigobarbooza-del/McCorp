package com.isjbar.minercorp.resources.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;

/**
 * API publica de MinerCorp-Recursos, para que otros plugins (vehiculos,
 * gran sede, mercado) creen y reconozcan los items de recursos sin depender
 * de como estan marcados por dentro. Se obtiene por el ServicesManager:
 *
 * <pre>
 *   ResourcesAPI recursos = getServer().getServicesManager().load(ResourcesAPI.class);
 *   ItemStack nafta = recursos.createItem("gasolina", 4);
 *   recursos.identify(itemEnLaMano).filter("diesel"::equals).ifPresent(...);
 * </pre>
 */
public interface ResourcesAPI {

    /** Ids de todos los recursos configurados (ej: "carbon_crudo", "petroleo_crudo", "gasolina"). */
    Set<String> resourceIds();

    /** Item del recurso con la cantidad pedida (sin pasarse del tamano maximo de pila), o vacio si el id no existe. */
    Optional<ItemStack> createItem(String resourceId, int amount);

    /** Id del recurso que representa el item, o vacio si es un item comun. */
    Optional<String> identify(ItemStack item);

    /** Nombre legible del recurso ("Bidon de gasolina"), o el id si no existe. */
    String displayName(String resourceId);

    /** Precio de venta al sistema por unidad, o 0 si no tiene. */
    double price(String resourceId);

    /** Item de la maquina para colocarla (ej: "refineria"), o vacio si no existe. */
    Optional<ItemStack> createMachineItem(String machineId);

    /** Ids de todas las maquinas configuradas. */
    Set<String> machineIds();

    /** Precio sugerido de la maquina (para la tienda de la Gran Sede). */
    double machinePrice(String machineId);
}
