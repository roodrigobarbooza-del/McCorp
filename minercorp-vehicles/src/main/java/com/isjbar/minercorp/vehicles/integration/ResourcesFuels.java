package com.isjbar.minercorp.vehicles.integration;

import com.isjbar.minercorp.resources.api.ResourcesAPI;
import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Registra como combustibles los items de MinerCorp-Recursos (bidones de
 * gasolina y diesel, carbon crudo) segun "combustibles-de-recursos" del
 * config. En su propia clase para que Vehicles arranque sin ese plugin.
 */
public final class ResourcesFuels {

    private ResourcesFuels() {
    }

    public static int registrar(VehiclesPlugin plugin) {
        ResourcesAPI recursos = plugin.getServer().getServicesManager().load(ResourcesAPI.class);
        if (recursos == null) return 0;
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("combustibles-de-recursos");
        if (sec == null) return 0;
        int count = 0;
        for (String resourceId : sec.getKeys(false)) {
            if (!recursos.resourceIds().contains(resourceId)) {
                plugin.getLogger().warning("combustibles-de-recursos: MinerCorp-Recursos no tiene '" + resourceId + "'");
                continue;
            }
            String fuelId = sec.getString(resourceId + ".combustible", resourceId);
            double litros = sec.getDouble(resourceId + ".litros", 20);
            if (litros <= 0) continue;
            plugin.fuels().register(fuelId + "#" + resourceId, fuelId, recursos.displayName(resourceId),
                    item -> recursos.identify(item).filter(resourceId::equals).isPresent(), litros);
            count++;
        }
        return count;
    }
}
