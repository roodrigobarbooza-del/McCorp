package com.isjbar.minercorp.territory;

import com.isjbar.minercorp.territory.api.ClaimResult;
import com.isjbar.minercorp.territory.api.VeinSnapshot;
import com.isjbar.minercorp.territory.util.ChunkKey;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/** Persistencia y reglas de negocio de los chunks reclamados. */
class TerritoryManager {

    private final TerritoryPlugin plugin;
    private final File file;
    private final Map<ChunkKey, Claim> claims = new LinkedHashMap<>();

    TerritoryManager(TerritoryPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "territories.yml");
        load();
    }

    ClaimResult claim(UUID ownerId, String ownerLabel, String purpose, Chunk chunk) {
        ChunkKey key = ChunkKey.of(chunk);
        if (claims.containsKey(key)) {
            return ClaimResult.YA_RECLAMADO;
        }

        int bloques = contarMineralDeCarbon(chunk);
        if (bloques == 0) {
            return ClaimResult.SIN_VETA;
        }

        double unidadesPorBloque = plugin.getConfig().getDouble("veta.unidades-por-bloque", 8);
        VeinState vein = new VeinState(bloques, unidadesPorBloque);
        claims.put(key, new Claim(ownerId, ownerLabel, purpose, vein));
        save();
        return ClaimResult.OK;
    }

    /** Usado por TerritoryEntryListener (mismo modulo) para armar el aviso de entrada al territorio. */
    Optional<Claim> claimAt(ChunkKey key) {
        return Optional.ofNullable(claims.get(key));
    }

    void unclaim(UUID ownerId, Chunk chunk) {
        ChunkKey key = ChunkKey.of(chunk);
        Claim claim = claims.get(key);
        if (claim != null && claim.getOwnerId().equals(ownerId)) {
            claims.remove(key);
            save();
        }
    }

    void setAuthorizedUsers(UUID ownerId, Set<UUID> authorized) {
        boolean changed = false;
        for (Claim claim : claims.values()) {
            if (claim.getOwnerId().equals(ownerId)) {
                claim.getAuthorizedUsers().clear();
                claim.getAuthorizedUsers().addAll(authorized);
                changed = true;
            }
        }
        if (changed) save();
    }

    Optional<UUID> getOwner(Chunk chunk) {
        Claim claim = claims.get(ChunkKey.of(chunk));
        return claim == null ? Optional.empty() : Optional.of(claim.getOwnerId());
    }

    boolean isAuthorized(UUID ownerId, UUID playerId) {
        for (Claim claim : claims.values()) {
            if (claim.getOwnerId().equals(ownerId)) {
                return claim.isAuthorized(playerId);
            }
        }
        return false;
    }

    Optional<VeinSnapshot> getVein(Chunk chunk) {
        Claim claim = claims.get(ChunkKey.of(chunk));
        return claim == null ? Optional.empty() : Optional.of(claim.getVein().toSnapshot());
    }

    double extractFromVein(Chunk chunk, double amount) {
        Claim claim = claims.get(ChunkKey.of(chunk));
        if (claim == null) return 0;
        double extraido = claim.getVein().extraer(amount);
        if (extraido > 0) save();
        return extraido;
    }

    int countClaims(UUID ownerId) {
        int count = 0;
        for (Claim claim : claims.values()) {
            if (claim.getOwnerId().equals(ownerId)) count++;
        }
        return count;
    }

    List<ChunkKey> getClaims(UUID ownerId) {
        List<ChunkKey> result = new ArrayList<>();
        for (Map.Entry<ChunkKey, Claim> entry : claims.entrySet()) {
            if (entry.getValue().getOwnerId().equals(ownerId)) result.add(entry.getKey());
        }
        return result;
    }

    private int contarMineralDeCarbon(Chunk chunk) {
        int count = 0;
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Material type = chunk.getBlock(x, y, z).getType();
                    if (type == Material.COAL_ORE || type == Material.DEEPSLATE_COAL_ORE) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ---------------------------------------------------------------
    // Persistencia
    // ---------------------------------------------------------------

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("claims");

        for (Map.Entry<ChunkKey, Claim> entry : claims.entrySet()) {
            ConfigurationSection sec = root.createSection(entry.getKey().serialize());
            Claim claim = entry.getValue();
            sec.set("ownerId", claim.getOwnerId().toString());
            sec.set("ownerLabel", claim.getOwnerLabel());
            sec.set("purpose", claim.getPurpose());
            sec.set("authorizedUsers", claim.getAuthorizedUsers().stream().map(UUID::toString).toList());
            sec.set("bloques", claim.getVein().getBloquesDetectados());
            sec.set("reservaActual", claim.getVein().getReservaActual());
            sec.set("reservaMaxima", claim.getVein().getReservaMaxima());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            logger().severe("No se pudo guardar territories.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("claims");
        if (root == null) return;

        for (String keyStr : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(keyStr);
            if (sec == null) continue;

            ChunkKey key = ChunkKey.parse(keyStr);
            UUID ownerId = UUID.fromString(sec.getString("ownerId"));
            String ownerLabel = sec.getString("ownerLabel", "desconocido");
            String purpose = sec.getString("purpose", "");

            VeinState vein = new VeinState(
                    sec.getInt("bloques"),
                    sec.getDouble("reservaActual"),
                    sec.getDouble("reservaMaxima")
            );
            Claim claim = new Claim(ownerId, ownerLabel, purpose, vein);
            for (String uuidStr : sec.getStringList("authorizedUsers")) {
                claim.getAuthorizedUsers().add(UUID.fromString(uuidStr));
            }
            claims.put(key, claim);
        }
    }

    private Logger logger() {
        return plugin.getLogger();
    }
}
