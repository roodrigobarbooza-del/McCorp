package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountType;
import com.isjbar.minercorp.economy.api.Reason;
import com.isjbar.minercorp.economy.api.TransactionRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Guarda el {@link Ledger} en {@code cuentas.yml} y el log de auditoria en
 * {@code transacciones/AAAA-MM.log}.
 *
 * Se guarda en un hilo aparte cada N segundos y solo si hubo cambios. Cada
 * guardado escribe un archivo temporal y lo renombra encima del real, asi un
 * corte de luz a mitad de escritura nunca deja el archivo corrupto.
 */
final class LedgerStorage {

    private static final int VERSION = 2;

    private final Ledger ledger;
    private final Logger logger;
    private final Path file;
    private final Path legacyFile;
    private final Path auditDir;
    private ScheduledExecutorService executor;

    LedgerStorage(Ledger ledger, File dataFolder, Logger logger) {
        this.ledger = ledger;
        this.logger = logger;
        this.file = dataFolder.toPath().resolve("cuentas.yml");
        this.legacyFile = dataFolder.toPath().resolve("accounts.yml");
        this.auditDir = dataFolder.toPath().resolve("transacciones");
    }

    // ---------------------------------------------------------------------- carga

    void load() throws IOException {
        if (Files.exists(file)) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IOException("cuentas.yml esta danado: " + e.getMessage(), e);
            }
            ledger.restore(read(yaml));
            return;
        }
        if (Files.exists(legacyFile)) {
            migrateLegacy();
        }
    }

    private Ledger.Snapshot read(YamlConfiguration yaml) {
        List<Ledger.AccountSnapshot> cuentas = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("cuentas");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(key);
                UUID id = parseUuid(key);
                if (sec == null || id == null) {
                    logger.warning("cuentas.yml: se ignora la cuenta '" + key + "' (formato invalido).");
                    continue;
                }
                AccountType type = parseType(sec.getString("tipo"));
                List<TransactionRecord> historial = new ArrayList<>();
                for (String linea : sec.getStringList("historial")) {
                    TransactionRecord r = parseRecord(id, linea);
                    if (r != null) historial.add(r);
                }
                cuentas.add(new Ledger.AccountSnapshot(id, type, sec.getString("nombre"),
                        parseUuid(sec.getString("dueno")), sec.getLong("saldo"), sec.getLong("creada"), historial));
            }
        }
        return new Ledger.Snapshot(yaml.getLong("estadisticas.creado"), yaml.getLong("estadisticas.destruido"),
                yaml.getLong("estadisticas.ultima-transaccion"), cuentas);
    }

    /** accounts.yml de la version 1: "uuid: saldo" con saldo en double. */
    private void migrateLegacy() throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile.toFile());
        List<Ledger.AccountSnapshot> cuentas = new ArrayList<>();
        long total = 0;
        long now = System.currentTimeMillis();
        for (String key : yaml.getKeys(false)) {
            UUID id = parseUuid(key);
            if (id == null) continue;
            long saldo = Math.max(0, Money.toCents(yaml.getDouble(key)));
            total += saldo;
            OfflinePlayer jugador = Bukkit.getOfflinePlayer(id);
            String nombre = jugador.getName();
            AccountType type = nombre != null ? AccountType.PLAYER : AccountType.OTHER;
            cuentas.add(new Ledger.AccountSnapshot(id, type, nombre, nombre != null ? id : null, saldo, now, List.of()));
        }
        ledger.restore(new Ledger.Snapshot(0, 0, 0, cuentas));
        ledger.markDirty();
        writeSnapshot(ledger.snapshot());
        Files.move(legacyFile, legacyFile.resolveSibling("accounts.yml.v1.bak"), StandardCopyOption.REPLACE_EXISTING);
        logger.info("Migradas " + cuentas.size() + " cuentas de accounts.yml (total " + Money.toDouble(total)
                + "). El archivo viejo quedo como accounts.yml.v1.bak.");
    }

    // ------------------------------------------------------------------- guardado

    void start(int intervalSeconds) {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MinerCorp-Economy-Guardado");
            t.setDaemon(true);
            return t;
        });
        int intervalo = Math.max(5, intervalSeconds);
        executor.scheduleWithFixedDelay(this::saveIfDirty, intervalo, intervalo, TimeUnit.SECONDS);
    }

    /** Para el hilo de guardado y guarda todo lo pendiente en este hilo. */
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        saveIfDirty();
    }

    private void saveIfDirty() {
        try {
            flushAudit();
            if (ledger.isDirty()) writeSnapshot(ledger.snapshot());
        } catch (Throwable t) {
            ledger.markDirty();
            logger.log(Level.SEVERE, "No se pudo guardar cuentas.yml, se reintenta en el proximo ciclo", t);
        }
    }

    private void writeSnapshot(Ledger.Snapshot snap) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "MinerCorp-Economy - cuentas. Los saldos estan en CENTAVOS (12345 = 123,45).",
                "No lo edites con el server prendido: usa /eco dar|quitar|fijar."));
        yaml.set("version", VERSION);
        yaml.set("estadisticas.creado", snap.minted());
        yaml.set("estadisticas.destruido", snap.burned());
        yaml.set("estadisticas.ultima-transaccion", snap.lastTxNumber());
        for (Ledger.AccountSnapshot a : snap.accounts()) {
            String base = "cuentas." + a.id() + ".";
            yaml.set(base + "tipo", a.type().name());
            if (a.name() != null) yaml.set(base + "nombre", a.name());
            if (a.owner() != null) yaml.set(base + "dueno", a.owner().toString());
            yaml.set(base + "saldo", a.balance());
            yaml.set(base + "creada", a.createdAt());
            List<String> historial = new ArrayList<>(a.history().size());
            for (TransactionRecord r : a.history()) historial.add(formatRecord(r));
            yaml.set(base + "historial", historial);
        }

        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("cuentas.yml.tmp");
        Files.writeString(tmp, yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void flushAudit() throws IOException {
        List<String> lineas = ledger.drainAudit();
        if (lineas.isEmpty()) return;
        Files.createDirectories(auditDir);
        Path log = auditDir.resolve(YearMonth.now() + ".log");
        Files.write(log, lineas, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    // ------------------------------------------------------------------- formato

    /** id;fecha;centavos;saldoDespues;contraparte;categoria;detalle */
    private static String formatRecord(TransactionRecord r) {
        return r.transactionId() + ";" + r.timestamp() + ";" + Money.toCents(r.amount()) + ";"
                + Money.toCents(r.balanceAfter()) + ";" + (r.counterparty() == null ? "-" : r.counterparty()) + ";"
                + r.reason().category() + ";" + (r.reason().detail() == null ? "" : r.reason().detail());
    }

    private static TransactionRecord parseRecord(UUID account, String linea) {
        String[] p = linea.split(";", 7);
        if (p.length < 7) return null;
        try {
            return new TransactionRecord(p[0], Long.parseLong(p[1]), account, parseUuid(p[4]),
                    Money.toDouble(Long.parseLong(p[2])), Money.toDouble(Long.parseLong(p[3])), Reason.of(p[5], p[6]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.equals("-")) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static AccountType parseType(String s) {
        if (s == null) return AccountType.OTHER;
        try {
            return AccountType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return AccountType.OTHER;
        }
    }
}
