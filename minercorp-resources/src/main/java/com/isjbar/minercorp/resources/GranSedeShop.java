package com.isjbar.minercorp.resources;

import com.isjbar.minercorp.gransede.api.GranSedeAPI;
import com.isjbar.minercorp.gransede.api.Producto;
import com.isjbar.minercorp.resources.machine.MachineType;
import org.bukkit.inventory.ItemStack;

/**
 * Pone las maquinas a la venta en la tienda de la Gran Sede, si ese plugin
 * esta instalado. Esta en su propia clase para que MinerCorp-Recursos arranque
 * igual sin la Gran Sede (solo se carga si el plugin existe).
 */
final class GranSedeShop {

    private GranSedeShop() {
    }

    static int registrar(ResourcesPlugin plugin) {
        GranSedeAPI api = plugin.getServer().getServicesManager().load(GranSedeAPI.class);
        if (api == null) return 0;
        String tienda = plugin.getConfig().getString("gran-sede.tienda", "taladros");
        int count = 0;
        for (MachineType type : plugin.machines().types()) {
            if (type.price() <= 0) continue;
            ItemStack icono = plugin.machines().createItem(type);
            api.registrarProducto(new Producto("maquina-" + type.id(), tienda, type.name(), icono,
                    type.description(), type.price(), player -> {
                        player.getInventory().addItem(plugin.machines().createItem(type)).values()
                                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                        return true;
                    }));
            count++;
        }
        return count;
    }
}
