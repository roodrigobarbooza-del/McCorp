package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        AccountManager accounts = new AccountManager(getDataFolder(), getLogger());
        EconomyAPI service = new EconomyService(accounts);

        getServer().getServicesManager().register(EconomyAPI.class, service, this, ServicePriority.Normal);

        double saldoInicial = getConfig().getDouble("saldo-inicial", 100.0);
        getServer().getPluginManager().registerEvents(new WelcomeBonusListener(accounts, saldoInicial), this);

        getLogger().info("MinerCorp-Economy habilitado.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }
}
