package com.isjbar.minercorp.vehicles.vehicle;

import com.isjbar.minercorp.vehicles.model.Shape;
import com.isjbar.minercorp.vehicles.model.VehicleModel;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.World;

import java.util.UUID;

/**
 * Estado en memoria de un vehiculo que alguien esta manejando. Lo que tiene
 * que sobrevivir a un reinicio (tipo, dueno, combustible, carga) vive en el
 * PersistentDataContainer de la raiz; esto se descarta al bajarse.
 */
final class VehicleState {

    final UUID rootId;
    final VehicleType type;
    final Shape shape;
    final UUID ownerId;
    final UUID driverId;
    final World world;
    final BossBar bossBar;
    /** Carroceria; null si se perdio (el vehiculo sigue andando, solo no se ve). */
    VehicleModel model;

    /** Centro del vehiculo en el plano. */
    double x, z;
    /** Altura del piso bajo el punto de apoyo delantero y el trasero. */
    double yFront, yRear;
    float yaw;
    double speed;
    double fallSpeed;
    double fuel;

    int drillProgress;
    int ticks;
    int lastWarningTick = -1000;
    double tripCoal;
    int tripBlocks;
    double tripDistance;

    VehicleState(UUID rootId, VehicleType type, Shape shape, UUID ownerId, UUID driverId, World world, BossBar bossBar) {
        this.rootId = rootId;
        this.type = type;
        this.shape = shape;
        this.ownerId = ownerId;
        this.driverId = driverId;
        this.world = world;
        this.bossBar = bossBar;
    }

    double y() {
        return (yFront + yRear) / 2.0;
    }

    /** Pitch del vehiculo segun la diferencia de altura entre apoyos (negativo = nariz arriba). */
    float pitch() {
        return (float) -Math.toDegrees(Math.atan2(yFront - yRear, shape.contact() * 2));
    }
}
