package com.isjbar.minercorp.gransede.trabajo;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.gransede.zona.Zona;
import com.isjbar.minercorp.gransede.zona.ZonaManager;
import com.isjbar.minercorp.gransede.zona.ZonaProteccionListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La mina y el bosque de la Gran Sede: cada bloque de la lista paga un
 * jornal a la billetera del jugador, no suelta items y vuelve solo despues
 * de unos segundos. Lo que no esta en la lista no se puede romper.
 */
public class TrabajoListener implements Listener {

    private static final long HORA_MS = 60L * 60L * 1000L;

    private final Plugin plugin;
    private final ZonaManager zonas;
    private final EconomyAPI economy;
    private final TrabajoConfig mina;
    private final TrabajoConfig bosque;
    private final double topePorHora;

    /** Bloques esperando volver, con lo que tienen que volver a ser. */
    private final Map<Location, BlockData> pendientes = new HashMap<>();
    private final Map<UUID, double[]> ganadoEstaHora = new HashMap<>(); // [inicioVentanaMs, ganado]

    public TrabajoListener(Plugin plugin, ZonaManager zonas, EconomyAPI economy,
                           TrabajoConfig mina, TrabajoConfig bosque, double topePorHora) {
        this.plugin = plugin;
        this.zonas = zonas;
        this.economy = economy;
        this.mina = mina;
        this.bosque = bosque;
        this.topePorHora = topePorHora;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block block = e.getBlock();
        Optional<Zona> zona = zonas.at(block.getLocation());
        if (zona.isEmpty()) return;
        TrabajoConfig trabajo = switch (zona.get().tipo()) {
            case MINA -> mina;
            case BOSQUE -> bosque;
            default -> null;
        };
        if (trabajo == null) return;

        Player player = e.getPlayer();
        if (ZonaProteccionListener.puedeConstruir(player)) return; // admin armando la mina

        Double pago = trabajo.pago().get(block.getType());
        if (pago == null || pendientes.containsKey(block.getLocation())) {
            e.setCancelled(true);
            return;
        }

        double cobrable = cobrable(player.getUniqueId(), pago);
        if (pago > 0 && cobrable <= 0) {
            e.setCancelled(true);
            player.sendActionBar(Component.text("Llegaste al tope de jornal de esta hora. Es momento de fundar tu empresa.", NamedTextColor.RED));
            return;
        }

        e.setDropItems(false);
        e.setExpToDrop(0);
        if (cobrable > 0) {
            economy.deposit(player.getUniqueId(), cobrable);
            player.sendActionBar(Component.text("+" + formato(cobrable) + "  ", NamedTextColor.GREEN)
                    .append(Component.text("Saldo: " + formato(economy.getBalance(player.getUniqueId())), NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
        }

        BlockData original = block.getBlockData().clone();
        BlockData vuelve = trabajo.regeneraMismo() ? original : trabajo.sortear(original.getMaterial()).createBlockData();
        Location loc = block.getLocation();
        pendientes.put(loc, vuelve);

        // El bloque roto se reemplaza un tick despues (cuando Bukkit ya lo saco).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!pendientes.containsKey(loc)) return;
            BlockData agotado = trabajo.agotado().createBlockData();
            if (agotado instanceof Orientable a && original instanceof Orientable o && a.getAxes().contains(o.getAxis())) {
                a.setAxis(o.getAxis());
            }
            loc.getBlock().setBlockData(agotado, false);
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> restaurar(loc), trabajo.regeneracionTicks());
    }

    private void restaurar(Location loc) {
        BlockData data = pendientes.remove(loc);
        if (data == null) return;
        loc.getBlock().setBlockData(data, false);
        loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3);
    }

    /** Al apagar el server: todo lo pendiente vuelve ya, asi la mina nunca queda vacia. */
    public void restaurarTodo() {
        for (Map.Entry<Location, BlockData> e : Map.copyOf(pendientes).entrySet()) {
            if (e.getKey().isWorldLoaded()) e.getKey().getBlock().setBlockData(e.getValue(), false);
        }
        pendientes.clear();
    }

    /** Cuanto de {@code pago} se le puede dar todavia sin pasar el tope de la hora, y lo anota. */
    private double cobrable(UUID player, double pago) {
        if (topePorHora <= 0) return pago;
        long ahora = System.currentTimeMillis();
        double[] v = ganadoEstaHora.computeIfAbsent(player, k -> new double[]{ahora, 0});
        if (ahora - v[0] >= HORA_MS) {
            v[0] = ahora;
            v[1] = 0;
        }
        double dar = Math.max(0, Math.min(pago, topePorHora - v[1]));
        v[1] += dar;
        return dar;
    }

    private static String formato(double d) {
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
