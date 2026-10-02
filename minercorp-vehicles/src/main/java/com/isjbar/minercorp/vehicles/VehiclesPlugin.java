package com.isjbar.minercorp.vehicles;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import com.isjbar.minercorp.vehicles.api.VehiclesAPI;
import com.isjbar.minercorp.vehicles.command.VehiculoCommand;
import com.isjbar.minercorp.vehicles.fuel.FuelRegistry;
import com.isjbar.minercorp.vehicles.garage.Garage;
import com.isjbar.minercorp.vehicles.gui.MenuListener;
import com.isjbar.minercorp.vehicles.type.VehicleTypes;
import com.isjbar.minercorp.vehicles.vehicle.LegacyMigration;
import com.isjbar.minercorp.vehicles.vehicle.VehicleListener;
import com.isjbar.minercorp.vehicles.vehicle.VehicleManager;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class VehiclesPlugin extends JavaPlugin {

    private TerritoryAPI territoryAPI;
    private EconomyAPI economyAPI;
    private MiningPlugin mining;
    private VehicleTypes types;
    private final FuelRegistry fuels = new FuelRegistry();
    private Garage garage;
    private VehicleManager vehicles;
    private LegacyMigration legacy;
    private VehiclesService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.territoryAPI = loadService(TerritoryAPI.class);
        this.economyAPI = loadService(EconomyAPI.class);
        Plugin miningPlugin = getServer().getPluginManager().getPlugin("MinerCorp-Mining");
        if (territoryAPI == null || economyAPI == null || !(miningPlugin instanceof MiningPlugin m) || !m.isEnabled()) {
            getLogger().severe("Faltan MinerCorp-Territory, MinerCorp-Economy o MinerCorp-Mining. Deshabilitando MinerCorp-Vehicles.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.mining = m;

        this.types = VehicleTypes.load(getConfig(), getLogger());
        fuels.loadConfig(getConfig(), getLogger());
        this.garage = new Garage(this);
        garage.load();
        this.vehicles = new VehicleManager(this);
        this.legacy = new LegacyMigration(this, vehicles);
        this.service = new VehiclesService(this);
        getServer().getServicesManager().register(VehiclesAPI.class, service, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new VehicleListener(this, vehicles, legacy), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        VehiculoCommand command = new VehiculoCommand(this);
        getCommand("vehiculo").setExecutor(command);
        getCommand("vehiculo").setTabCompleter(command);
        getCommand("garaje").setExecutor(command);

        vehicles.start();
        legacy.migrateStoredDrills();
        for (World world : getServer().getWorlds()) {
            legacy.convert(world.getEntities());
        }
        getLogger().info("MinerCorp-Vehicles habilitado - " + types.all().size() + " tipos de vehiculo.");
    }

    @Override
    public void onDisable() {
        if (vehicles != null) vehicles.stop();
        if (garage != null) garage.save();
        getServer().getServicesManager().unregisterAll(this);
    }

    /** Relee config.yml: tipos, combustibles y reglas. Los vehiculos en el mundo toman los cambios al volver a subirse. */
    public void reloadAll() {
        reloadConfig();
        this.types = VehicleTypes.load(getConfig(), getLogger());
        fuels.loadConfig(getConfig(), getLogger());
        vehicles.reloadSettings();
    }

    private <T> T loadService(Class<T> clazz) {
        RegisteredServiceProvider<T> provider = getServer().getServicesManager().getRegistration(clazz);
        return provider == null ? null : provider.getProvider();
    }

    public TerritoryAPI territory() {
        return territoryAPI;
    }

    public EconomyAPI economy() {
        return economyAPI;
    }

    public MiningPlugin mining() {
        return mining;
    }

    public VehicleTypes types() {
        return types;
    }

    public FuelRegistry fuels() {
        return fuels;
    }

    public Garage garage() {
        return garage;
    }

    public VehicleManager vehicles() {
        return vehicles;
    }

    public LegacyMigration legacy() {
        return legacy;
    }

    public VehiclesService service() {
        return service;
    }
}
