package com.isjbar.minercorp.resources.resource;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Los recursos del config y sus items. Cada item lleva el id del recurso en
 * su PersistentDataContainer: eso es lo que lo distingue del item vanilla del
 * mismo material (un "Carbon crudo" no es un COAL cualquiera).
 */
public class ResourceRegistry {

    private final Plugin plugin;
    private final NamespacedKey key;
    private final Map<String, ResourceType> types = new LinkedHashMap<>();

    public ResourceRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "recurso");
    }

    public void load(FileConfiguration config) {
        types.clear();
        ConfigurationSection root = config.getConfigurationSection("recursos");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            Material material = Material.matchMaterial(sec.getString("material", "PAPER"));
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Recurso '" + id + "': material invalido, uso PAPER.");
                material = Material.PAPER;
            }
            TextColor color = TextColor.fromHexString(sec.getString("color", "#ffffff"));
            types.put(id.toLowerCase(Locale.ROOT), new ResourceType(
                    id.toLowerCase(Locale.ROOT),
                    sec.getString("nombre", id),
                    material,
                    color == null ? NamedTextColor.WHITE : color,
                    sec.getDouble("precio", 0),
                    sec.getStringList("descripcion")));
        }
    }

    public Collection<ResourceType> all() {
        return types.values();
    }

    public Optional<ResourceType> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(types.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<ItemStack> create(String id, int amount) {
        return get(id).map(type -> create(type, amount));
    }

    public ItemStack create(ResourceType type, int amount) {
        ItemStack item = new ItemStack(type.material());
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.name(), type.color()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : type.description()) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (type.price() > 0) {
            lore.add(Component.text("Se vende a " + type.price() + " c/u", NamedTextColor.DARK_GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Recurso MinerCorp", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Id del recurso del item, o vacio si es un item comun. */
    public Optional<String> identify(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public boolean isResource(ItemStack item) {
        return identify(item).isPresent();
    }

    public String displayName(String id) {
        return get(id).map(ResourceType::name).orElse(id);
    }

    /**
     * Clave de combustible/entrada de un item: el id del recurso si es un
     * recurso, o el nombre del material si es un item vanilla comun.
     */
    public Optional<String> itemKey(ItemStack item) {
        if (item == null || item.getType().isAir()) return Optional.empty();
        Optional<String> id = identify(item);
        return id.isPresent() ? id : Optional.of(item.getType().name());
    }
}
