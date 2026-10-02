package com.isjbar.minercorp.territory;

import com.isjbar.minercorp.territory.api.TerritoryAPI;
import com.isjbar.minercorp.territory.listeners.EnvironmentProtectionListener;
import com.isjbar.minercorp.territory.listeners.ProtectionListener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class TerritoryPlugin extends JavaPlugin {

    private TerritoryManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        this.manager = new TerritoryManager(this);
        TerritoryAPI service = new TerritoryService(manager);

        getServer().getServicesManager().register(TerritoryAPI.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new ProtectionListener(service), this);
        getServer().getPluginManager().registerEvents(new EnvironmentProtectionListener(
                service,
                getConfig().getBoolean("proteccion.explosiones", true),
                getConfig().getBoolean("proteccion.fuego", true)
        ), this);
        getServer().getPluginManager().registerEvents(new TerritoryEntryListener(manager), this);

        long guardado = Math.max(20L, getConfig().getLong("guardado-segundos", 30) * 20L);
        getServer().getScheduler().runTaskTimer(this, manager::saveIfDirty, guardado, guardado);

        getLogger().info("MinerCorp-Territory habilitado.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
        getServer().getServicesManager().unregisterAll(this);
    }
}
