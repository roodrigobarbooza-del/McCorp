package com.isjbar.minercorp.mining.company;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.territory.api.ClaimResult;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import com.isjbar.minercorp.territory.util.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Gestiona la creacion, persistencia y reglas de negocio de las empresas.
 * El territorio y el balance de cada empresa viven en MinerCorp-Territory y
 * MinerCorp-Economy respectivamente (identificados por {@code company.getId()}).
 */
public class CompanyManager {

    private final MiningPlugin plugin;
    private final TerritoryAPI territory;
    private final File file;
    private final Map<UUID, Company> companies = new LinkedHashMap<>();

    public CompanyManager(MiningPlugin plugin, TerritoryAPI territory) {
        this.plugin = plugin;
        this.territory = territory;
        this.file = new File(plugin.getDataFolder(), "companies.yml");
        load();
    }

    // ---------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------

    public Collection<Company> all() {
        return companies.values();
    }

    public Optional<Company> getById(UUID id) {
        return Optional.ofNullable(companies.get(id));
    }

    public Optional<Company> getByName(String name) {
        return companies.values().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<Company> getByMember(UUID playerId) {
        return companies.values().stream()
                .filter(c -> c.isMember(playerId))
                .findFirst();
    }

    // ---------------------------------------------------------------
    // Creacion / gestion
    // ---------------------------------------------------------------

    public Company create(String name, UUID owner) {
        Company company = new Company(UUID.randomUUID(), name, owner);
        companies.put(company.getId(), company);
        syncAuthorized(company);
        save();
        return company;
    }

    public void disband(Company company) {
        for (ChunkKey key : territory.getClaims(company.getId())) {
            resolveChunk(key).ifPresent(chunk -> territory.unclaim(company.getId(), chunk));
        }
        companies.remove(company.getId());
        save();
    }

    public void addCollaborator(Company company, UUID playerId) {
        company.getCollaborators().add(playerId);
        syncAuthorized(company);
        save();
    }

    public void removeCollaborator(Company company, UUID playerId) {
        company.getCollaborators().remove(playerId);
        syncAuthorized(company);
        save();
    }

    private void syncAuthorized(Company company) {
        territory.setAuthorizedUsers(company.getId(), company.allMemberIds());
    }

    /**
     * Intenta reclamar el chunk para la empresa, respetando el limite de
     * territorios del nivel actual antes de pedirselo a Territory.
     */
    public ClaimOutcome claim(Company company, Chunk chunk) {
        int maxChunks = plugin.levels().chunksPermitidos(company.getLevel());
        if (territory.countClaims(company.getId()) >= maxChunks) {
            return ClaimOutcome.LIMITE_NIVEL;
        }

        ClaimResult result = territory.claim(company.getId(), company.getName(), "Mineria de carbon", chunk);
        if (result == ClaimResult.OK) {
            syncAuthorized(company);
            return ClaimOutcome.OK;
        }
        return result == ClaimResult.YA_RECLAMADO ? ClaimOutcome.YA_RECLAMADO : ClaimOutcome.SIN_VETA;
    }

    public void unclaim(Company company, Chunk chunk) {
        territory.unclaim(company.getId(), chunk);
    }

    private Optional<Chunk> resolveChunk(ChunkKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null) return Optional.empty();
        return Optional.of(world.getChunkAt(key.x(), key.z()));
    }

    // ---------------------------------------------------------------
    // Niveles / xp
    // ---------------------------------------------------------------

    public boolean checkLevelUp(Company company) {
        boolean leveledUp = false;
        while (company.getLevel() < plugin.levels().maxNivel()) {
            double requerido = plugin.levels().xpRequerida(company.getLevel());
            if (company.getXp() >= requerido) {
                company.setLevel(company.getLevel() + 1);
                leveledUp = true;
            } else {
                break;
            }
        }
        if (leveledUp) save();
        return leveledUp;
    }

    // ---------------------------------------------------------------
    // Persistencia
    // ---------------------------------------------------------------

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("companies");

        for (Company c : companies.values()) {
            ConfigurationSection sec = root.createSection(c.getId().toString());
            sec.set("name", c.getName());
            sec.set("owner", c.getOwner().toString());
            sec.set("collaborators", c.getCollaborators().stream().map(UUID::toString).toList());
            sec.set("xp", c.getXp());
            sec.set("level", c.getLevel());
            sec.set("rawCoal", c.getRawCoal());
            sec.set("refinedCoal", c.getRefinedCoal());

            List<Map<String, Object>> minionsList = new ArrayList<>();
            for (MinionData m : c.getMinions()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", m.getId().toString());
                map.put("world", m.getWorld());
                map.put("x", m.getX());
                map.put("y", m.getY());
                map.put("z", m.getZ());
                minionsList.add(map);
            }
            sec.set("minions", minionsList);

            List<Map<String, Object>> drillsList = new ArrayList<>();
            for (StoredDrill d : c.getStoredDrills()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("tier", d.tier());
                map.put("combustible", d.combustible());
                drillsList.add(map);
            }
            sec.set("taladrosGuardados", drillsList);
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar companies.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("companies");
        if (root == null) return;

        for (String idStr : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(idStr);
            if (sec == null) continue;

            UUID id = UUID.fromString(idStr);
            String name = sec.getString("name", "Empresa");
            UUID owner = UUID.fromString(sec.getString("owner"));
            Company company = new Company(id, name, owner);

            for (String colStr : sec.getStringList("collaborators")) {
                company.getCollaborators().add(UUID.fromString(colStr));
            }

            company.addXp(sec.getDouble("xp", 0));
            company.setLevel(sec.getInt("level", 1));
            company.addRawCoal(sec.getDouble("rawCoal", 0));
            company.addRefinedCoal(sec.getDouble("refinedCoal", 0));

            for (Map<?, ?> map : sec.getMapList("minions")) {
                UUID minionId = UUID.fromString((String) map.get("id"));
                String world = (String) map.get("world");
                double x = ((Number) map.get("x")).doubleValue();
                double y = ((Number) map.get("y")).doubleValue();
                double z = ((Number) map.get("z")).doubleValue();
                company.getMinions().add(new MinionData(minionId, world, x, y, z));
            }

            for (Map<?, ?> map : sec.getMapList("taladrosGuardados")) {
                int tier = map.get("tier") instanceof Number n ? n.intValue() : 1;
                double combustible = map.get("combustible") instanceof Number n ? n.doubleValue() : 0;
                company.getStoredDrills().add(new StoredDrill(tier, combustible));
            }

            companies.put(id, company);
        }
    }

    public enum ClaimOutcome {
        OK, YA_RECLAMADO, SIN_VETA, LIMITE_NIVEL
    }
}
