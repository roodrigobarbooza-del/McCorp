package com.isjbar.minercorp.mining.company;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.resources.api.ResourcesAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * El refinado ya no es por comando: el carbon crudo que junta la empresa
 * (taladros y minions) se saca como items de "Carbon crudo" de
 * MinerCorp-Recursos y se lleva a un Horno de coque.
 */
public final class RawCoalItems {

    private RawCoalItems() {
    }

    /** Saca hasta {@code max} unidades enteras de carbon crudo de la empresa como items. */
    public static void sacar(MiningPlugin plugin, Player player, Company company, int max) {
        ResourcesAPI recursos = plugin.recursos();
        if (recursos == null) {
            msg(player, NamedTextColor.RED, "Falta el plugin MinerCorp-Recursos en el server.");
            return;
        }
        int cantidad = (int) Math.min(max, Math.floor(company.getRawCoal()));
        if (cantidad <= 0) {
            msg(player, NamedTextColor.RED, "La empresa no tiene carbon crudo para sacar.");
            return;
        }
        if (!company.removeRawCoal(cantidad)) return;
        int left = cantidad;
        while (left > 0) {
            ItemStack stack = recursos.createItem("carbon_crudo", left).orElse(null);
            if (stack == null) {
                company.addRawCoal(left);
                break;
            }
            left -= stack.getAmount();
            player.getInventory().addItem(stack).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        plugin.companies().save();
        msg(player, NamedTextColor.GREEN, "Sacaste " + (cantidad - left) + " de carbon crudo. Refinalo en un Horno de coque.");
    }

    /** Respuesta a /empresa refinar y al viejo boton del menu. */
    public static void avisarRefinado(Player player) {
        msg(player, NamedTextColor.GOLD, "El refinado ya no es por comando: saca el carbon crudo con /empresa carbon <cantidad>"
                + " y ponelo en un Horno de coque (MinerCorp-Recursos).");
    }

    private static void msg(Player player, NamedTextColor color, String text) {
        player.sendMessage(Component.text(text, color));
    }
}
