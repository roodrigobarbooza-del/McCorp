package com.isjbar.minercorp.gransede;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.gransede.api.GranSedeAPI;
import com.isjbar.minercorp.gransede.npc.VendedorListener;
import com.isjbar.minercorp.gransede.npc.VendedorManager;
import com.isjbar.minercorp.gransede.tienda.TiendaListener;
import com.isjbar.minercorp.gransede.tienda.TiendaManager;
import com.isjbar.minercorp.gransede.tienda.TiendaMenu;
import com.isjbar.minercorp.gransede.trabajo.TrabajoConfig;
import com.isjbar.minercorp.gransede.trabajo.TrabajoListener;
import com.isjbar.minercorp.gransede.zona.ZonaManager;
import com.isjbar.minercorp.gransede.zona.ZonaProteccionListener;
import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class GranSedePlugin extends JavaPlugin {

    private EconomyAPI economy;
    private ZonaManager zonas;
    private TiendaManager tiendas;
    private TiendaMenu menu;
    private VendedorManager vendedores;
    private ZonaProteccionListener proteccion;
    private TrabajoListener trabajos;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();

        RegisteredServiceProvider<EconomyAPI> rsp = getServer().getServicesManager().getRegistration(EconomyAPI.class);
        if (rsp == null) {
            getLogger().severe("No se encontro MinerCorp-Economy. Deshabilitando MinerCorp-GranSede.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.economy = rsp.getProvider();

        this.zonas = new ZonaManager(getDataFolder(), getLogger());
        this.tiendas = new TiendaManager(getLogger());
        this.menu = new TiendaMenu(this, tiendas, economy);
        this.vendedores = new VendedorManager(this);

        getServer().getServicesManager().register(GranSedeAPI.class, new GranSedeService(tiendas, zonas), this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new TiendaListener(tiendas, menu, economy, getLogger()), this);
        getServer().getPluginManager().registerEvents(new VendedorListener(vendedores, tiendas, menu), this);
        getServer().getPluginManager().registerEvents(new BienvenidaListener(this, zonas,
                () -> getConfig().getBoolean("spawn-jugadores-nuevos", true),
                () -> getConfig().getStringList("mensaje-bienvenida")), this);
        cargarReglas();

        GranSedeCommand cmd = new GranSedeCommand(this);
        getCommand("gransede").setExecutor(cmd);
        getCommand("gransede").setTabCompleter(cmd);

        getLogger().info("MinerCorp-GranSede habilitado: " + zonas.all().size() + " zonas, " + tiendas.tiendas().size() + " tiendas.");
    }

    @Override
    public void onDisable() {
        if (trabajos != null) trabajos.restaurarTodo();
        getServer().getServicesManager().unregisterAll(this);
    }

    /** Lee (o vuelve a leer) tiendas, proteccion y trabajos del config. */
    public void cargarReglas() {
        tiendas.cargar(getConfig().getConfigurationSection("tiendas"));

        if (proteccion != null) HandlerList.unregisterAll(proteccion);
        if (trabajos != null) {
            trabajos.restaurarTodo();
            HandlerList.unregisterAll(trabajos);
        }
        this.proteccion = new ZonaProteccionListener(zonas, getConfig().getConfigurationSection("proteccion"));
        TrabajoConfig mina = TrabajoConfig.leer("mina", getConfig().getConfigurationSection("trabajos.mina"), Material.SMOOTH_STONE, getLogger());
        TrabajoConfig bosque = TrabajoConfig.leer("bosque", getConfig().getConfigurationSection("trabajos.bosque"), Material.STRIPPED_OAK_LOG, getLogger());
        this.trabajos = new TrabajoListener(this, zonas, economy, mina, bosque, getConfig().getDouble("trabajos.tope-por-hora", 400.0));
        getServer().getPluginManager().registerEvents(proteccion, this);
        getServer().getPluginManager().registerEvents(trabajos, this);
    }

    public void recargar() {
        reloadConfig();
        cargarReglas();
    }

    public EconomyAPI economy() { return economy; }
    public ZonaManager zonas() { return zonas; }
    public TiendaManager tiendas() { return tiendas; }
    public TiendaMenu menu() { return menu; }
    public VendedorManager vendedores() { return vendedores; }
}
