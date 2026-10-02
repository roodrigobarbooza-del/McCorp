package com.isjbar.minercorp.vehicles.gui;

import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.vehicles.VehiclesPlugin;
import com.isjbar.minercorp.vehicles.api.OwnerKind;
import com.isjbar.minercorp.vehicles.garage.Garage;
import com.isjbar.minercorp.vehicles.garage.StoredVehicle;
import com.isjbar.minercorp.vehicles.type.VehicleType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Menus del garaje y del concesionario por comando. */
public final class Menus {

    static final String CERRAR = "cerrar";
    static final String TIENDA = "tienda";
    static final String GARAJE = "garaje";
    static final String SACAR = "sacar:";
    static final String COMPRAR = "comprar:";
    static final String INFO = "info";

    private Menus() {
    }

    // ---------------------------------------------------------------- garaje

    public static void openGarage(VehiclesPlugin plugin, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Screen.GARAJE);
        Inventory inv = plugin.getServer().createInventory(holder, 54, Component.text("Garaje", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);

        Optional<Company> company = plugin.mining().companies().getByMember(player.getUniqueId());
        List<UUID> owners = new ArrayList<>();
        owners.add(player.getUniqueId());
        company.ifPresent(c -> owners.add(c.getId()));

        int slot = 9;
        int total = 0;
        List<Garage.Parked> outside = new ArrayList<>();
        for (UUID owner : owners) {
            boolean mine = owner.equals(player.getUniqueId());
            String ownerLabel = mine ? "Tuyo" : "Empresa " + company.map(Company::getName).orElse("?");
            List<StoredVehicle> list = plugin.garage().stored(owner);
            for (int i = 0; i < list.size(); i++) {
                total++;
                if (slot > 44) continue;
                StoredVehicle v = list.get(i);
                Optional<VehicleType> type = plugin.types().get(v.type());
                List<String> lore = new ArrayList<>();
                lore.add(ownerLabel);
                lore.add("Combustible: " + (int) Math.ceil(v.fuel()) + type.map(t -> "/" + (int) t.tanque()).orElse(""));
                if (type.isPresent() && type.get().carga() > 0) {
                    lore.add(v.cargo() == null ? "Carga: vacia" : "Carga: con cosas adentro");
                }
                if (type.isPresent() && type.get().isDrill()) {
                    lore.add("Click: sacarlo delante tuyo");
                    lore.add("(dentro del territorio de la empresa)");
                } else {
                    lore.add("Click: sacarlo delante tuyo");
                }
                inv.setItem(slot++, item(plugin, type.map(VehicleType::icono).orElse(Material.BARRIER),
                        type.map(VehicleType::nombre).orElse(v.type() + " (tipo borrado)"),
                        type.isPresent() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, lore,
                        SACAR + owner + ":" + i));
            }
            outside.addAll(plugin.garage().parked(owner));
        }

        inv.setItem(4, item(plugin, Material.OAK_SIGN, "Tu garaje", NamedTextColor.GOLD,
                List.of(total + " vehiculos guardados",
                        "Golpea tu vehiculo (click izquierdo)", "para guardarlo aca."), INFO));
        if (total == 0) {
            inv.setItem(22, item(plugin, Material.CHEST, "Garaje vacio", NamedTextColor.GRAY,
                    List.of("Los vehiculos se compran", "en el concesionario de la gran sede."), INFO));
        } else if (total > 36) {
            inv.setItem(44, item(plugin, Material.CHEST, "Hay mas vehiculos", NamedTextColor.GRAY,
                    List.of("Saca alguno para ver el resto."), INFO));
        }

        List<String> outsideLore = new ArrayList<>();
        for (Garage.Parked p : outside) {
            if (outsideLore.size() >= 8) {
                outsideLore.add("...");
                break;
            }
            String nombre = plugin.types().get(p.type()).map(VehicleType::nombre).orElse(p.type());
            outsideLore.add(nombre + ": " + p.x() + ", " + p.y() + ", " + p.z() + " (" + p.world() + ")");
        }
        if (outsideLore.isEmpty()) outsideLore.add("Ninguno");
        inv.setItem(45, item(plugin, Material.COMPASS, "Vehiculos afuera", NamedTextColor.AQUA, outsideLore, INFO));

        if (plugin.getConfig().getBoolean("tienda-por-comando", true)) {
            inv.setItem(49, item(plugin, Material.EMERALD, "Concesionario", NamedTextColor.GREEN,
                    List.of("Comprar vehiculos"), TIENDA));
        }
        inv.setItem(53, item(plugin, Material.BARRIER, "Cerrar", NamedTextColor.RED, List.of(), CERRAR));
        player.openInventory(inv);
    }

    // ---------------------------------------------------------------- tienda

    public static void openShop(VehiclesPlugin plugin, Player player) {
        MenuHolder holder = new MenuHolder(MenuHolder.Screen.TIENDA);
        Inventory inv = plugin.getServer().createInventory(holder, 54, Component.text("Concesionario", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);

        Optional<Company> company = plugin.mining().companies().getByMember(player.getUniqueId());
        int slot = 10;
        for (VehicleType type : plugin.types().all()) {
            if (slot > 43) break;
            if (slot % 9 == 8) slot += 2;
            List<String> lore = new ArrayList<>(type.descripcion());
            lore.add("");
            lore.add("Precio: " + round(type.precio()) + (type.dueno() == OwnerKind.EMPRESA ? " (lo paga la empresa)" : ""));
            lore.add("Velocidad maxima: " + Math.round(type.velocidad() * 20 * 3.6) + " km/h");
            lore.add("Tanque: " + (int) type.tanque() + " L  -  usa " + plugin.fuels().names(type.combustibles()));
            if (type.carga() > 0) lore.add("Carga: " + type.carga() + " espacios");
            if (type.perforacion() != null) {
                lore.add("Tunel: " + type.perforacion().ancho() + "x" + type.perforacion().alto()
                        + "  potencia x" + type.perforacion().potencia());
            }
            NamedTextColor color = NamedTextColor.AQUA;
            if (type.dueno() == OwnerKind.EMPRESA) {
                lore.add("Requiere empresa nivel " + type.nivelEmpresa());
                if (company.isEmpty() || company.get().getLevel() < type.nivelEmpresa()) color = NamedTextColor.DARK_GRAY;
            }
            lore.add("Click para comprar");
            inv.setItem(slot++, item(plugin, type.icono(), type.nombre(), color, lore, COMPRAR + type.id()));
        }
        inv.setItem(49, item(plugin, Material.MINECART, "Garaje", NamedTextColor.GREEN, List.of("Volver al garaje"), GARAJE));
        inv.setItem(53, item(plugin, Material.BARRIER, "Cerrar", NamedTextColor.RED, List.of(), CERRAR));
        player.openInventory(inv);
    }

    // --------------------------------------------------------------- helpers

    static NamespacedKey actionKey(VehiclesPlugin plugin) {
        return new NamespacedKey(plugin, "accion");
    }

    private static ItemStack item(VehiclesPlugin plugin, Material material, String nombre, NamedTextColor color,
                                  List<String> lore, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(nombre, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        meta.getPersistentDataContainer().set(actionKey(plugin), PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String round(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 100.0) / 100.0);
    }
}
