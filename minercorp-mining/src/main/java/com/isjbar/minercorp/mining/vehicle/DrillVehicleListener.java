package com.isjbar.minercorp.mining.vehicle;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

/** Solo los miembros de la empresa duena pueden subirse a su taladro-vehiculo. */
public class DrillVehicleListener implements Listener {

    private final MiningPlugin plugin;
    private final DrillVehicleManager vehicles;

    public DrillVehicleListener(MiningPlugin plugin, DrillVehicleManager vehicles) {
        this.plugin = plugin;
        this.vehicles = vehicles;
    }

    @EventHandler
    public void onEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (!(event.getEntered() instanceof Player player)) return;

        String companyIdStr = boat.getPersistentDataContainer().get(vehicles.companyIdKey(), PersistentDataType.STRING);
        if (companyIdStr == null) return;

        Optional<Company> company = plugin.companies().getById(UUID.fromString(companyIdStr));
        if (company.isEmpty() || !company.get().isMember(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Este taladro no es de tu empresa", NamedTextColor.RED));
        }
    }

    /** Si el bote se destruye por cualquier via (fuego, lava, etc), limpiamos su carroceria para que no quede flotando. */
    @EventHandler
    public void onDestroy(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (boat.getPersistentDataContainer().has(vehicles.companyIdKey(), PersistentDataType.STRING)) {
            vehicles.removeVehicle(boat);
        }
    }
}
