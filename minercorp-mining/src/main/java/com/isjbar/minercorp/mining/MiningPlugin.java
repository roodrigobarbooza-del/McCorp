package com.isjbar.minercorp.mining;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.mining.commands.EmpresaCommand;
import com.isjbar.minercorp.mining.company.CompanyManager;
import com.isjbar.minercorp.mining.company.LevelConfig;
import com.isjbar.minercorp.mining.gui.MenuListener;
import com.isjbar.minercorp.mining.minion.MinionManager;
import com.isjbar.minercorp.mining.sede.ObraListener;
import com.isjbar.minercorp.mining.sede.ObraManager;
import com.isjbar.minercorp.resources.api.ResourcesAPI;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MiningPlugin extends JavaPlugin {

    private CompanyManager companyManager;
    private LevelConfig levelConfig;
    private MinionManager minionManager;
    private ObraManager obraManager;
    private TerritoryAPI territoryAPI;
    private EconomyAPI economyAPI;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        actualizarConfig();

        this.territoryAPI = loadService(TerritoryAPI.class);
        this.economyAPI = loadService(EconomyAPI.class);
        if (territoryAPI == null || economyAPI == null) {
            getLogger().severe("No se encontro MinerCorp-Territory y/o MinerCorp-Economy. Deshabilitando MinerCorp-Mining.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.levelConfig = new LevelConfig(getConfig());
        this.companyManager = new CompanyManager(this, territoryAPI);
        this.minionManager = new MinionManager(this, territoryAPI, economyAPI);
        this.obraManager = new ObraManager(this, territoryAPI);

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ObraListener(this, obraManager), this);

        EmpresaCommand empresaCommand = new EmpresaCommand(this);
        getCommand("empresa").setExecutor(empresaCommand);
        getCommand("empresa").setTabCompleter(empresaCommand);

        minionManager.start();
        obraManager.start();

        getLogger().info("MinerCorp-Mining habilitado - " + companyManager.all().size() + " empresas cargadas.");
    }

    @Override
    public void onDisable() {
        if (minionManager != null) minionManager.stop();
        if (obraManager != null) obraManager.stop();
        if (companyManager != null) companyManager.save();
    }

    /**
     * Completa el config.yml del server con las claves nuevas del plugin. Bukkit
     * no lo hace solo: si una seccion falta en el archivo, getConfigurationSection
     * devuelve una seccion vacia en vez de la del jar.
     *
     * La seccion "taladros" se saca: los taladros ahora son de MinerCorp-Vehicles
     * y se configuran en su config.yml (tipos taladro-1, taladro-2, taladro-3).
     */
    private void actualizarConfig() {
        if (getConfig().isSet("taladros") || getConfig().isSet("niveles.taladro-tier-maximo-por-nivel")) {
            getConfig().set("taladros", null);
            getConfig().set("niveles.taladro-tier-maximo-por-nivel", null);
            getLogger().warning("config.yml tenia la seccion 'taladros': ahora los taladros se configuran en MinerCorp-Vehicles.");
        }
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private <T> T loadService(Class<T> clazz) {
        RegisteredServiceProvider<T> provider = getServer().getServicesManager().getRegistration(clazz);
        return provider == null ? null : provider.getProvider();
    }

    public CompanyManager companies() {
        return companyManager;
    }

    public TerritoryAPI territory() {
        return territoryAPI;
    }

    public EconomyAPI economy() {
        return economyAPI;
    }

    public LevelConfig levels() {
        return levelConfig;
    }

    public MinionManager minions() {
        return minionManager;
    }

    public ObraManager obras() {
        return obraManager;
    }

    /**
     * Items de recursos (carbon crudo, etc) de MinerCorp-Recursos, o null si
     * ese plugin no esta instalado. Se busca cada vez porque es opcional.
     */
    public ResourcesAPI recursos() {
        if (getServer().getPluginManager().getPlugin("MinerCorp-Recursos") == null) return null;
        return loadService(ResourcesAPI.class);
    }
}
