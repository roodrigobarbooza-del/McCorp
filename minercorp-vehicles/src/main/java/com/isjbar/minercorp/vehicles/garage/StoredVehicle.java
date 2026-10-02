package com.isjbar.minercorp.vehicles.garage;

/**
 * Un vehiculo guardado en un garaje.
 *
 * @param type  id del tipo
 * @param fuel  litros en el tanque
 * @param cargo contenido de la carga (bytes de ItemStack.serializeItemsAsBytes en base64), o null
 */
public record StoredVehicle(String type, double fuel, String cargo) {
}
