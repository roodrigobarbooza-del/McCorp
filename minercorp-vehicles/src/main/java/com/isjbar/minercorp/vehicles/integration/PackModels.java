package com.isjbar.minercorp.vehicles.integration;

import com.isjbar.minercorp.pack.api.Modelos;
import org.bukkit.Bukkit;

/**
 * Pregunta a MinerCorp-Pack si los jugadores reciben el resource pack. Solo
 * esta clase nombra a {@link Modelos}, y solo despues de comprobar que el
 * plugin existe, asi Vehicles arranca igual sin el.
 */
public final class PackModels {

    private PackModels() {
    }

    /** true si los jugadores reciben el pack: los vehiculos se dibujan con sus modelos 3D. */
    public static boolean active() {
        return Bukkit.getPluginManager().isPluginEnabled("MinerCorp-Pack") && Bridge.active();
    }

    /** Clase aparte: la JVM la carga recien al primer uso, ya con el plugin presente. */
    private static final class Bridge {
        static boolean active() {
            return Modelos.activo();
        }
    }
}
