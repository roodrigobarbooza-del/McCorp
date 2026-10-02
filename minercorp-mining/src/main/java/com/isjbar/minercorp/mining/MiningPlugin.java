package com.isjbar.minercorp.mining;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.mining.commands.EmpresaCommand;
import com.isjbar.minercorp.mining.commands.SaldoCommand;
import com.isjbar.minercorp.mining.company.CompanyManager;
import com.isjbar.minercorp.mining.company.LevelConfig;
import com.isjbar.minercorp.mining.gui.MenuListener;
import com.isjbar.minercorp.mining.minion.MinionManager;
import com.isjbar.minercorp.mining.sede.ObraListener;
import com.isjbar.minercorp.mining.sede.ObraManager;
import com.isjbar.minercorp.mining.vehicle.DrillVehicleListener;
import com.isjbar.minercorp.mining.vehicle.DrillVehicleManager;
import com.isjbar.minercorp.resources.api.ResourcesAPI;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MiningPlugin extends JavaPlugin {

    private CompanyManager companyManager;
    private LevelConfig levelConfig;
    private MinionManager minionManager;
    private DrillVehicleManager vehicleManager;
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
        this.vehicleManager = new DrillVehicleManager(this, territoryAPI, economyAPI);
        this.obraManager = new ObraManager(this, territoryAPI);

        getServer().getPluginManager().registerEvents(new DrillVehicleListener(this, vehicleManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ObraListener(this, obraManager), this);

        EmpresaCommand empresaCommand = new EmpresaCommand(this);
        getCommand("empresa").setExecutor(empresaCommand);
        getCommand("empresa").setTabCompleter(empresaCommand);
        SaldoCommand saldoCommand = new SaldoCommand(this);
        getCommand("saldo").setExecutor(saldoCommand);
        getCommand("saldo").setTabCompleter(saldoCommand);

        minionManager.start();
        vehicleManager.start();
        obraManager.start();

        getLogger().info("MinerCorp-Mining habilitado - " + companyManager.all().size() + " empresas cargadas.");
    }

    @Override
    public void onDisable() {
        if (minionManager != null) minionManager.stop();
        if (vehicleManager != null) vehicleManager.stop();
        if (obraManager != null) obraManager.stop();
        if (companyManager != null) companyManager.save();
    }

    /**
     * Completa el config.yml del server con las claves nuevas del plugin. Bukkit
     * no lo hace solo: si una seccion falta en el archivo, getConfigurationSection
     * devuelve una seccion vacia en vez de la del jar, y por eso con un config
     * viejo el taladro no reconocia el carbon como combustible.
     *
     * La seccion "taladros" del taladro-bote anterior (tiers con "radio" y una
     * "velocidad" pensada para botes) se reemplaza entera por la nueva.
     */
    private void actualizarConfig() {
        boolean taladroViejo = getConfig().isSet("taladros.intervalo-ticks")
                || getConfig().isSet("taladros.tier-1.radio");
        if (taladroViejo) {
            getConfig().set("taladros", null);
            getLogger().warning("config.yml tenia la seccion 'taladros' del taladro-bote anterior: se reemplazo por la nueva.");
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

    public DrillVehicleManager vehicles() {
        return vehicleManager;
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
