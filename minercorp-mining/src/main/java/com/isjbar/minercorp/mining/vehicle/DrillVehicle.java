package com.isjbar.minercorp.mining.vehicle;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.World;

import java.util.UUID;

/**
 * Estado en memoria de un taladro que alguien esta manejando. Lo que tiene
 * que sobrevivir a un reinicio (empresa, tier, combustible) vive en el
 * PersistentDataContainer de la entidad raiz; esto se descarta al bajarse.
 */
final class DrillVehicle {

    final UUID rootId;
    final UUID companyId;
    final DrillTier tier;
    final UUID driverId;
    final World world;
    final BossBar bossBar;
    /** Carroceria; null si se perdio (el taladro sigue andando, solo no se ve). */
    DrillModel model;

    /** Posicion de los "pies" del taladro (el piso donde esta apoyado). */
    double x, y, z;
    float yaw;
    double speed;
    double fallSpeed;
    double fuel;

    int drillProgress;
    int ticks;
    int lastWarningTick = -1000;
    double tripCoal;
    int tripBlocks;

    DrillVehicle(UUID rootId, UUID companyId, DrillTier tier, UUID driverId, World world, BossBar bossBar) {
        this.rootId = rootId;
        this.companyId = companyId;
        this.tier = tier;
        this.driverId = driverId;
        this.world = world;
        this.bossBar = bossBar;
    }
}
