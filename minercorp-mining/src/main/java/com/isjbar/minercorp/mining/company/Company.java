package com.isjbar.minercorp.mining.company;

import java.util.*;

/**
 * Una empresa minera fundada por un jugador. El balance vive en
 * MinerCorp-Economy (cuenta identificada por {@link #getId()}) y el
 * territorio reclamado vive en MinerCorp-Territory (dueno identificado por
 * el mismo id); esta clase solo guarda lo que es exclusivo del trabajo de
 * mineria: identidad, miembros, nivel/xp, stock de carbon y minions.
 */
public class Company {

    private final UUID id;
    private String name;
    private final UUID owner;
    private final Set<UUID> collaborators = new HashSet<>();

    private double xp;
    private int level = 1;

    private double rawCoal = 0;
    private double refinedCoal = 0;

    private final List<MinionData> minions = new ArrayList<>();

    public Company(UUID id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getCollaborators() {
        return collaborators;
    }

    public boolean isMember(UUID playerId) {
        return owner.equals(playerId) || collaborators.contains(playerId);
    }

    /** Dueno + colaboradores, para sincronizar con TerritoryAPI.setAuthorizedUsers. */
    public Set<UUID> allMemberIds() {
        Set<UUID> all = new HashSet<>(collaborators);
        all.add(owner);
        return all;
    }

    public double getXp() {
        return xp;
    }

    public void addXp(double amount) {
        this.xp += amount;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getRawCoal() {
        return rawCoal;
    }

    public void addRawCoal(double amount) {
        rawCoal += amount;
    }

    public boolean removeRawCoal(double amount) {
        if (rawCoal < amount) return false;
        rawCoal -= amount;
        return true;
    }

    public double getRefinedCoal() {
        return refinedCoal;
    }

    public void addRefinedCoal(double amount) {
        refinedCoal += amount;
    }

    public boolean removeRefinedCoal(double amount) {
        if (refinedCoal < amount) return false;
        refinedCoal -= amount;
        return true;
    }

    public List<MinionData> getMinions() {
        return minions;
    }
}
