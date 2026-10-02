package com.isjbar.minercorp.resources.resource;

import com.isjbar.minercorp.pack.api.Modelos;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Pone el modelo del resource pack a los items de recursos, si MinerCorp-Pack
 * esta instalado. Solo esta clase nombra a {@link Modelos}, y solo la toca
 * despues de comprobar que el plugin existe, asi Recursos arranca igual sin el.
 */
public final class PackModels {

    private PackModels() {
    }

    public static void apply(ItemStack item, String model) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MinerCorp-Pack")) return;
        Bridge.apply(item, model);
    }

    /** true si los jugadores reciben el pack: las maquinas se dibujan con sus modelos 3D. */
    public static boolean active() {
        return Bukkit.getPluginManager().isPluginEnabled("MinerCorp-Pack") && Bridge.active();
    }

    /** Clase aparte: la JVM la carga recien al primer uso, ya con el plugin presente. */
    private static final class Bridge {
        static void apply(ItemStack item, String model) {
            Modelos.aplicar(item, model);
        }

        static boolean active() {
            return Modelos.activo();
        }
    }
}
