package com.isjbar.minercorp.gransede.npc;

import com.isjbar.minercorp.gransede.tienda.Tienda;
import com.isjbar.minercorp.gransede.tienda.TiendaMenu;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Los vendedores de la Gran Sede: aldeanos vanilla quietos, invulnerables y
 * sin intercambios, con el id de su tienda guardado en el PDC. Son entidades
 * persistentes del mundo, asi que no hace falta guardarlos aparte.
 */
public class VendedorManager {

    private final NamespacedKey tiendaKey;

    public VendedorManager(Plugin plugin) {
        this.tiendaKey = new NamespacedKey(plugin, "vendedor_tienda");
    }

    public Villager crear(Tienda tienda, Location loc) {
        return loc.getWorld().spawn(loc, Villager.class, v -> {
            v.getPersistentDataContainer().set(tiendaKey, PersistentDataType.STRING, tienda.id());
            aplicar(v, tienda);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCanPickupItems(false);
            v.setVillagerLevel(5); // que no cambie de oficio
        });
    }

    /** Pone nombre y oficio segun el config actual (sirve tambien al recargar). */
    public void aplicar(Villager v, Tienda tienda) {
        v.customName(TiendaMenu.legacy(tienda.vendedor()));
        v.setCustomNameVisible(true);
        v.setProfession(tienda.profesion());
    }

    public Optional<String> tiendaDe(Entity e) {
        if (!(e instanceof Villager)) return Optional.empty();
        return Optional.ofNullable(e.getPersistentDataContainer().get(tiendaKey, PersistentDataType.STRING));
    }

    public boolean esVendedor(Entity e) {
        return tiendaDe(e).isPresent();
    }
}
