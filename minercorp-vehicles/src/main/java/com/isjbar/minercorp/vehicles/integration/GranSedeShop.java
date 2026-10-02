package com.isjbar.minercorp.vehicles.integration;

import com.isjbar.minercorp.gransede.api.GranSedeAPI;
import com.isjbar.minercorp.gransede.api.Producto;
import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.api.OwnerKind;
import com.isjbar.minercorp.vehicles.api.PurchaseResult;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Pone los vehiculos a la venta en las tiendas de la Gran Sede, si ese
 * plugin esta instalado. En su propia clase para que Vehicles arranque igual
 * sin la Gran Sede (solo se carga si el plugin existe).
 *
 * Los productos se registran con cobraPropio: la compra la hace
 * {@link com.isjbar.minercorp.vehicles.VehiclesService#purchase}, que cobra
 * al jugador o a su empresa segun el tipo y lo deja en el garaje.
 */
public final class GranSedeShop {

    private GranSedeShop() {
    }

    public static int registrar(VehiclesPlugin plugin) {
        GranSedeAPI api = plugin.getServer().getServicesManager().load(GranSedeAPI.class);
        if (api == null) return 0;
        String vehiculos = plugin.getConfig().getString("tienda-gran-sede.vehiculos", "concesionaria");
        String taladros = plugin.getConfig().getString("tienda-gran-sede.taladros", "taladros");
        int count = 0;
        for (VehicleType type : plugin.types().all()) {
            List<String> lore = new ArrayList<>(type.descripcion());
            lore.add("Velocidad maxima: " + Math.round(type.velocidad() * 20 * 3.6) + " km/h");
            if (type.carga() > 0) lore.add("Carga: " + type.carga() + " espacios");
            if (type.dueno() == OwnerKind.EMPRESA) {
                lore.add("Lo paga la empresa (nivel " + type.nivelEmpresa() + ")");
            }
            lore.add("Va a tu garaje: /garaje");
            String id = type.id();
            api.registrarProducto(new Producto("vehiculo-" + id, type.isDrill() ? taladros : vehiculos, type.nombre(),
                    new ItemStack(type.icono()), lore, type.precio(), player -> {
                        PurchaseResult result = plugin.service().purchase(player, id);
                        player.sendMessage(Component.text(result.message(),
                                result == PurchaseResult.OK ? NamedTextColor.GREEN : NamedTextColor.RED));
                        return result == PurchaseResult.OK;
                    }, true));
            count++;
        }
        return count;
    }
}
