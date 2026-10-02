package com.isjbar.minercorp.mining.sede;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Chest;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.UUID;

/** Eventos de la obra de sede: usar el plano, el cofre de obra y la parcela reservada. */
public class ObraListener implements Listener {

    private final MiningPlugin plugin;
    private final ObraManager obras;

    public ObraListener(MiningPlugin plugin, ObraManager obras) {
        this.plugin = plugin;
        this.obras = obras;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUsarPlano(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Optional<UUID> companyId = obras.empresaDelPlano(event.getItem());
        if (companyId.isEmpty()) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Optional<Company> company = plugin.companies().getById(companyId.get());
        if (company.isEmpty() || !company.get().isMember(player.getUniqueId())) {
            player.sendMessage(Component.text("Este plano es de una empresa a la que no perteneces.", NamedTextColor.RED));
            return;
        }
        String error = obras.iniciar(player, company.get());
        if (error != null) {
            player.sendMessage(Component.text(error, NamedTextColor.RED));
            return;
        }
        player.getInventory().setItemInMainHand(null);
        player.sendMessage(Component.text("Obra iniciada. ", NamedTextColor.GREEN)
                .append(Component.text("Limpia la parcela marcada por los andamios y deja en el cofre los materiales que pide el cartel.",
                        NamedTextColor.GRAY)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRomper(BlockBreakEvent event) {
        if (obras.obraDelCofre(event.getBlock()).isPresent()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Es el cofre de obra. Si quieres abandonar la obra usa /empresa obra cancelar.", NamedTextColor.RED));
            return;
        }
        obras.obraEn(event.getBlock()).filter(Obra::limpia).ifPresent(obra -> {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("No se puede tocar la sede mientras esta en obra.", NamedTextColor.RED));
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPoner(BlockPlaceEvent event) {
        obras.obraEn(event.getBlock()).filter(Obra::limpia).ifPresent(obra -> {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("La parcela de obra tiene que quedar libre para construir.", NamedTextColor.RED));
        });
    }

    @EventHandler
    public void onCerrarCofre(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) return;
        obras.obraDelCofre(chest.getBlock()).ifPresent(obras::alCerrarCofre);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFuegosArtificiales(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework fw && obras.esFuegoDeSede(fw)) event.setCancelled(true);
    }
}
