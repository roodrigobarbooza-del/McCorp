package com.isjbar.minercorp.gransede.api;

import org.bukkit.Location;

import java.util.Collection;

/**
 * API publica de MinerCorp-GranSede, via el ServicesManager de Bukkit.
 *
 * La Gran Sede no conoce los vehiculos, taladros ni minerales: cada plugin
 * registra sus productos al arrancar (con {@code softdepend: [MinerCorp-GranSede]})
 * y aparecen a la venta en la tienda que indiquen.
 */
public interface GranSedeAPI {

    /** Agrega (o reemplaza, si ya habia uno con ese id) un producto en su tienda. */
    void registrarProducto(Producto producto);

    /** Saca un producto de la venta. */
    void quitarProducto(String id);

    /** Productos a la venta en una tienda, en orden de registro. */
    Collection<Producto> productos(String tienda);

    /** True si la ubicacion cae dentro de alguna zona de la Gran Sede (sede, mina o bosque). */
    boolean esGranSede(Location location);
}
