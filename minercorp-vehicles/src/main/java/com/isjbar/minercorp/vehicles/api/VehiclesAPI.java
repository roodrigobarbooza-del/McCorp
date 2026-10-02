package com.isjbar.minercorp.vehicles.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * API publica de MinerCorp-Vehicles, registrada en el ServicesManager igual
 * que TerritoryAPI y EconomyAPI.
 *
 * <ul>
 *   <li>El plugin de la gran sede la usa para el concesionario:
 *       {@link #catalog()} para mostrar los vehiculos y
 *       {@link #purchase(Player, String)} para venderlos.</li>
 *   <li>El plugin de minerales la usa para que sus combustibles (gasolina,
 *       diesel...) carguen los vehiculos: {@link #registerFuel}.</li>
 * </ul>
 */
public interface VehiclesAPI {

    /** Vehiculos a la venta, en el orden del config. */
    List<VehicleOffer> catalog();

    /**
     * Cobra el vehiculo (al jugador o a su empresa, segun el tipo) y lo deja
     * en el garaje correspondiente. No lo pone en el mundo: se saca con /garaje.
     */
    PurchaseResult purchase(Player buyer, String typeId);

    /** Deja un vehiculo nuevo en el garaje del dueno (jugador o empresa) sin cobrar. Devuelve false si el tipo no existe. */
    boolean giveToGarage(UUID ownerId, String typeId);

    /**
     * Registra un combustible que se carga haciendo click derecho al vehiculo
     * con el item en la mano. Si el id ya existe (por ejemplo, definido en el
     * config), se reemplaza.
     *
     * @param id             id que usan los tipos en "combustibles" (ej: "gasolina")
     * @param name           nombre legible
     * @param matcher        true si el item es este combustible
     * @param litersPerItem  litros que carga cada item
     */
    void registerFuel(String id, String name, Predicate<ItemStack> matcher, double litersPerItem);
}
