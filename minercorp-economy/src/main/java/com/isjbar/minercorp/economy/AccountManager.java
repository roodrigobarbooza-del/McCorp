package com.isjbar.minercorp.economy;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/** Ledger de cuentas (jugadores o empresas, todo identificado por UUID) con persistencia en accounts.yml. */
class AccountManager {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Double> balances = new HashMap<>();

    AccountManager(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "accounts.yml");
        this.logger = logger;
        load();
    }

    double getBalance(UUID accountId) {
        return balances.getOrDefault(accountId, 0.0);
    }

    boolean hasAccount(UUID accountId) {
        return balances.containsKey(accountId);
    }

    void deposit(UUID accountId, double amount) {
        balances.put(accountId, getBalance(accountId) + amount);
        save();
    }

    boolean withdraw(UUID accountId, double amount) {
        double current = getBalance(accountId);
        if (current < amount) return false;
        balances.put(accountId, current - amount);
        save();
        return true;
    }

    boolean transfer(UUID fromAccountId, UUID toAccountId, double amount) {
        if (getBalance(fromAccountId) < amount) return false;
        balances.put(fromAccountId, getBalance(fromAccountId) - amount);
        balances.put(toAccountId, getBalance(toAccountId) + amount);
        save();
        return true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            balances.put(UUID.fromString(key), yaml.getDouble(key));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("No se pudo guardar accounts.yml: " + e.getMessage());
        }
    }
}
