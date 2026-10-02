package com.isjbar.minercorp.resources;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.resources.api.ResourcesAPI;
import com.isjbar.minercorp.resources.command.RecursosCommand;
import com.isjbar.minercorp.resources.machine.MachineListener;
import com.isjbar.minercorp.resources.machine.MachineManager;
import com.isjbar.minercorp.resources.machine.MachineType;
import com.isjbar.minercorp.resources.oil.OilFieldManager;
import com.isjbar.minercorp.resources.resource.ResourceGuardListener;
import com.isjbar.minercorp.resources.resource.ResourceRegistry;
import com.isjbar.minercorp.resources.resource.ResourceType;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * MinerCorp-Recursos: recursos que no existen en Minecraft (petroleo y sus
 * derivados, coque, etc) y las maquinas que los extraen y los transforman.
 * Usa MinerCorp-Territory para saber de que empresa es cada chunk y sacar de
 * sus vetas, y MinerCorp-Economy para pagar las ventas. Expone
 * {@link ResourcesAPI} para vehiculos, gran sede y mercado.
 */
public class ResourcesPlugin extends JavaPlugin implements ResourcesAPI {

    private TerritoryAPI territoryAPI;
    private EconomyAPI economyAPI;
    private ResourceRegistry resources;
    private OilFieldManager oil;
    private MachineManager machines;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.territoryAPI = loadService(TerritoryAPI.class);
        this.economyAPI = loadService(EconomyAPI.class);
        if (territoryAPI == null || economyAPI == null) {
            getLogger().severe("No se encontro MinerCorp-Territory y/o MinerCorp-Economy. Deshabilitando MinerCorp-Recursos.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.resources = new ResourceRegistry(this);
        resources.load(getConfig());
        this.oil = new OilFieldManager(this);
        this.machines = new MachineManager(this, resources, oil, territoryAPI);

        getServer().getPluginManager().registerEvents(new MachineListener(this, machines, resources, territoryAPI), this);
        getServer().getPluginManager().registerEvents(new ResourceGuardListener(resources, machines), this);

        RecursosCommand command = new RecursosCommand(this);
        getCommand("recursos").setExecutor(command);
        getCommand("recursos").setTabCompleter(command);

        getServer().getServicesManager().register(ResourcesAPI.class, this, this, ServicePriority.Normal);
        machines.start();

        getLogger().info("MinerCorp-Recursos habilitado - " + resources.all().size() + " recursos, "
                + machines.types().size() + " tipos de maquina, " + machines.all().size() + " maquinas colocadas.");
    }

    @Override
    public void onDisable() {
        if (machines != null) machines.stop();
        getServer().getServicesManager().unregisterAll(this);
    }

    /** Relee el config.yml: recursos, combustibles, tipos de maquina y yacimientos. */
    public void reload() {
        reloadConfig();
        resources.load(getConfig());
        oil.loadConfig(getConfig());
        machines.loadTypes(getConfig());
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

    public ResourceRegistry resources() {
        return resources;
    }

    public OilFieldManager oil() {
        return oil;
    }

    public MachineManager machines() {
        return machines;
    }

    // ------------------------------------------------------------ ResourcesAPI

    @Override
    public Set<String> resourceIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ResourceType type : resources.all()) ids.add(type.id());
        return ids;
    }

    @Override
    public Optional<ItemStack> createItem(String resourceId, int amount) {
        return resources.create(resourceId, amount);
    }

    @Override
    public Optional<String> identify(ItemStack item) {
        return resources.identify(item);
    }

    @Override
    public String displayName(String resourceId) {
        return resources.displayName(resourceId);
    }

    @Override
    public double price(String resourceId) {
        return resources.get(resourceId).map(ResourceType::price).orElse(0.0);
    }

    @Override
    public Optional<ItemStack> createMachineItem(String machineId) {
        return machines.createItem(machineId);
    }

    @Override
    public Set<String> machineIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MachineType type : machines.types()) ids.add(type.id());
        return ids;
    }

    @Override
    public double machinePrice(String machineId) {
        return machines.type(machineId).map(MachineType::price).orElse(0.0);
    }
}
