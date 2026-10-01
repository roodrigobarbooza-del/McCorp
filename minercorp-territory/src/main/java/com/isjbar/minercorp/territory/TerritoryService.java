package com.isjbar.minercorp.territory;

import com.isjbar.minercorp.territory.api.ClaimResult;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import com.isjbar.minercorp.territory.api.VeinSnapshot;
import com.isjbar.minercorp.territory.util.ChunkKey;
import org.bukkit.Chunk;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class TerritoryService implements TerritoryAPI {

    private final TerritoryManager manager;

    TerritoryService(TerritoryManager manager) {
        this.manager = manager;
    }

    @Override
    public ClaimResult claim(UUID ownerId, String ownerLabel, String purpose, Chunk chunk) {
        return manager.claim(ownerId, ownerLabel, purpose, chunk);
    }

    @Override
    public void unclaim(UUID ownerId, Chunk chunk) {
        manager.unclaim(ownerId, chunk);
    }

    @Override
    public void setAuthorizedUsers(UUID ownerId, Set<UUID> authorized) {
        manager.setAuthorizedUsers(ownerId, authorized);
    }

    @Override
    public Optional<UUID> getOwner(Chunk chunk) {
        return manager.getOwner(chunk);
    }

    @Override
    public boolean isAuthorized(UUID ownerId, UUID playerId) {
        return manager.isAuthorized(ownerId, playerId);
    }

    @Override
    public Optional<VeinSnapshot> getVein(Chunk chunk) {
        return manager.getVein(chunk);
    }

    @Override
    public double extractFromVein(Chunk chunk, double amount) {
        return manager.extractFromVein(chunk, amount);
    }

    @Override
    public int countClaims(UUID ownerId) {
        return manager.countClaims(ownerId);
    }

    @Override
    public List<ChunkKey> getClaims(UUID ownerId) {
        return manager.getClaims(ownerId);
    }
}
