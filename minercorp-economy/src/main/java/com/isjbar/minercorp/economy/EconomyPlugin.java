package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class EconomyPlugin extends JavaPlugin {

    private Ledger ledger;
    private LedgerStorage storage;
    private EconomyService economy;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        getDataFolder().mkdirs();

        Money money = new Money(getConfig().getConfigurationSection("moneda"));
        this.ledger = new Ledger(money, getConfig().getInt("historial.por-cuenta", 100));
        this.storage = new LedgerStorage(ledger, getDataFolder(), getLogger());
        try {
            storage.load();
        } catch (Exception e) {
            // Nunca arrancar con cuentas vacias: el proximo guardado pisaria los saldos reales.
            getLogger().log(Level.SEVERE, "No se pudieron cargar las cuentas. MinerCorp-Economy queda deshabilitado"
                    + " para no perder saldos. Revisa cuentas.yml o restaura una copia de la carpeta 'copias'.", e);
            this.storage = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        backup();
        storage.start(getConfig().getInt("guardado.intervalo-segundos", 30));

        this.economy = new EconomyService(this, ledger, money);
        this.messages = new Messages(this, money, economy);
        getServer().getServicesManager().register(EconomyAPI.class, economy, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(new EconomyListener(this), this);
        getServer().getPluginManager().registerEvents(new WalletMenu.ClickListener(), this);

        EconomyCommands commands = new EconomyCommands(this);
        for (String name : List.of("saldo", "pagar", "movimientos", "top", "billetera", "eco")) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) continue;
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }

        getLogger().info("MinerCorp-Economy habilitado - " + ledger.accountCount() + " cuentas cargadas.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (storage != null) storage.shutdown();
    }

    /** Copia diaria de cuentas.yml en la carpeta "copias", conservando las ultimas N. */
    private void backup() {
        Path cuentas = getDataFolder().toPath().resolve("cuentas.yml");
        if (!Files.exists(cuentas)) return;
        Path dir = getDataFolder().toPath().resolve("copias");
        try {
            Files.createDirectories(dir);
            Files.copy(cuentas, dir.resolve("cuentas-" + LocalDate.now() + ".yml"), StandardCopyOption.REPLACE_EXISTING);
            List<Path> copias = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "cuentas-*.yml")) {
                stream.forEach(copias::add);
            }
            copias.sort(null);
            int conservar = Math.max(1, getConfig().getInt("guardado.copias-a-conservar", 7));
            for (int i = 0; i < copias.size() - conservar; i++) Files.deleteIfExists(copias.get(i));
        } catch (IOException e) {
            getLogger().warning("No se pudo hacer la copia de seguridad de cuentas.yml: " + e.getMessage());
        }
    }

    Ledger ledger() {
        return ledger;
    }

    EconomyAPI economy() {
        return economy;
    }

    Messages messages() {
        return messages;
    }
}
