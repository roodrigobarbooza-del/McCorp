package com.isjbar.minercorp.gransede;

import com.isjbar.minercorp.gransede.api.GranSedeAPI;
import com.isjbar.minercorp.gransede.api.Producto;
import com.isjbar.minercorp.gransede.tienda.TiendaManager;
import com.isjbar.minercorp.gransede.zona.ZonaManager;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class GranSedeService implements GranSedeAPI {

    private final TiendaManager tiendas;
    private final ZonaManager zonas;

    public GranSedeService(TiendaManager tiendas, ZonaManager zonas) {
        this.tiendas = tiendas;
        this.zonas = zonas;
    }

    @Override
    public void registrarProducto(Producto producto) {
        Objects.requireNonNull(producto.id(), "id");
        Objects.requireNonNull(producto.tienda(), "tienda");
        Objects.requireNonNull(producto.entregar(), "entregar");
        Producto limpio = producto.lore() == null
                ? new Producto(producto.id(), producto.tienda(), producto.nombre(), producto.icono(), List.of(), producto.precio(), producto.entregar())
                : producto;
        tiendas.registrar(limpio);
    }

    @Override
    public void quitarProducto(String id) {
        tiendas.quitar(id);
    }

    @Override
    public Collection<Producto> productos(String tienda) {
        return tiendas.productos(tienda);
    }

    @Override
    public boolean esGranSede(Location location) {
        return zonas.at(location).isPresent();
    }
}
