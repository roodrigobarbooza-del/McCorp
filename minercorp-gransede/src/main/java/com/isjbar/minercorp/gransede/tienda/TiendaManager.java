package com.isjbar.minercorp.gransede.tienda;

import com.isjbar.minercorp.gransede.api.Producto;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Las tiendas y lo que venden. Hay dos fuentes de productos: los del
 * config (items vanilla o comandos) y los que registran otros plugins por
 * GranSedeAPI. Al recargar el config solo se reemplazan los del config.
 */
public class TiendaManager {

    private final Logger logger;
    private final Map<String, Tienda> tiendas = new LinkedHashMap<>();
    private final Map<String, Producto> deConfig = new LinkedHashMap<>();
    private final Map<String, Producto> registrados = new LinkedHashMap<>();

    public TiendaManager(Logger logger) {
        this.logger = logger;
    }

    public void cargar(ConfigurationSection sec) {
        tiendas.clear();
        deConfig.clear();
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(id);
            if (t == null) continue;
            String tiendaId = id.toLowerCase();
            tiendas.put(tiendaId, new Tienda(tiendaId, t.getString("titulo", id), t.getString("vendedor", id), profesion(t.getString("profesion"))));
            ConfigurationSection prods = t.getConfigurationSection("productos");
            if (prods == null) continue;
            for (String pid : prods.getKeys(false)) {
                Producto p = leerProducto(tiendaId, pid, prods.getConfigurationSection(pid));
                if (p != null) deConfig.put(p.id(), p);
            }
        }
    }

    private Producto leerProducto(String tienda, String id, ConfigurationSection sec) {
        if (sec == null) return null;
        String nombre = sec.getString("nombre", id);
        double precio = sec.getDouble("precio", -1);
        if (precio < 0) {
            logger.warning("Producto '" + id + "' de la tienda '" + tienda + "' sin precio, se ignora.");
            return null;
        }
        List<String> lore = sec.getStringList("lore");

        if (sec.isString("item")) {
            String[] partes = sec.getString("item").trim().split("\\s+");
            Material m = Material.matchMaterial(partes[0]);
            if (m == null || !m.isItem()) {
                logger.warning("Producto '" + id + "': item desconocido " + partes[0]);
                return null;
            }
            int cantidad = 1;
            if (partes.length > 1) {
                try {
                    cantidad = Math.max(1, Integer.parseInt(partes[1]));
                } catch (NumberFormatException ignored) {
                }
            }
            ItemStack stack = new ItemStack(m, cantidad);
            return new Producto(id, tienda, nombre, stack, lore, precio, player -> {
                player.getInventory().addItem(stack.clone()).values()
                        .forEach(sobra -> player.getWorld().dropItemNaturally(player.getLocation(), sobra));
                return true;
            });
        }

        if (sec.isString("comando")) {
            String comando = sec.getString("comando");
            Material icono = Optional.ofNullable(Material.matchMaterial(sec.getString("icono", "CHEST"))).orElse(Material.CHEST);
            return new Producto(id, tienda, nombre, new ItemStack(icono), lore, precio,
                    player -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando.replace("%player%", player.getName())));
        }

        logger.warning("Producto '" + id + "' de la tienda '" + tienda + "' sin 'item' ni 'comando', se ignora.");
        return null;
    }

    private Villager.Profession profesion(String nombre) {
        if (nombre != null) {
            Villager.Profession p = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(nombre.toLowerCase()));
            if (p != null) return p;
        }
        return Villager.Profession.NITWIT;
    }

    public Optional<Tienda> tienda(String id) {
        return Optional.ofNullable(tiendas.get(id.toLowerCase()));
    }

    public Collection<Tienda> tiendas() {
        return tiendas.values();
    }

    public void registrar(Producto p) {
        registrados.put(p.id(), p);
    }

    public void quitar(String id) {
        registrados.remove(id);
    }

    /** Productos de una tienda: primero los registrados por otros plugins, despues los del config. */
    public List<Producto> productos(String tienda) {
        Map<String, Producto> todos = new LinkedHashMap<>();
        for (Producto p : registrados.values()) if (p.tienda().equalsIgnoreCase(tienda)) todos.put(p.id(), p);
        for (Producto p : deConfig.values()) if (p.tienda().equalsIgnoreCase(tienda)) todos.putIfAbsent(p.id(), p);
        return new ArrayList<>(todos.values());
    }

    public Optional<Producto> producto(String id) {
        Producto p = registrados.get(id);
        return Optional.ofNullable(p != null ? p : deConfig.get(id));
    }

    /** Para mostrar en /gransede cuantas cosas vende cada tienda. */
    public Map<String, Integer> conteo() {
        Map<String, Integer> m = new HashMap<>();
        for (Tienda t : tiendas.values()) m.put(t.id(), productos(t.id()).size());
        return m;
    }

    public static boolean entregarSeguro(Producto p, Player player, Logger logger) {
        try {
            return p.entregar().test(player);
        } catch (RuntimeException e) {
            logger.warning("Fallo la entrega de '" + p.id() + "' a " + player.getName() + ": " + e);
            return false;
        }
    }
}
