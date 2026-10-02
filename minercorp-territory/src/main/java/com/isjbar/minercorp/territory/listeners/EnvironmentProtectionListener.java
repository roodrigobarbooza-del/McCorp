package com.isjbar.minercorp.territory.listeners;

import com.isjbar.minercorp.territory.api.TerritoryAPI;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Protege los chunks reclamados de lo que no hace un jugador directamente:
 * explosiones, fuego, liquidos que entran desde afuera, pistones que cruzan
 * el borde, dispensadores apuntando adentro, endermen/withers/ravagers
 * moviendo bloques y arboles que crecen desde afuera.
 *
 * La regla general es: algo que nace en el territorio de un dueno puede
 * afectar a ese mismo dueno, pero nunca a un chunk reclamado por otro.
 */
public class EnvironmentProtectionListener implements Listener {

    private final TerritoryAPI territory;
    private final boolean protegerExplosiones;
    private final boolean protegerFuego;

    public EnvironmentProtectionListener(TerritoryAPI territory, boolean protegerExplosiones, boolean protegerFuego) {
        this.territory = territory;
        this.protegerExplosiones = protegerExplosiones;
        this.protegerFuego = protegerFuego;
    }

    // ---------------------------------------------------------------
    // Explosiones
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (protegerExplosiones) event.blockList().removeIf(this::reclamado);
    }

    /** Camas y anclas de reaparicion usadas en la dimension equivocada. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (protegerExplosiones) event.blockList().removeIf(this::reclamado);
    }

    /** Marcos, cuadros, soportes de armadura (minions incluidos) y vehiculos. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!protegerExplosiones) return;
        EntityDamageEvent.DamageCause causa = event.getCause();
        if (causa != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && causa != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        Entity victima = event.getEntity();
        if (!(victima instanceof Hanging || victima instanceof ArmorStand || victima instanceof Vehicle)) return;
        if (reclamado(victima.getLocation().getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingExplode(HangingBreakEvent event) {
        if (!protegerExplosiones || event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) return;
        if (reclamado(event.getEntity().getLocation().getBlock())) event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Fuego
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (protegerFuego && reclamado(event.getBlock())) event.setCancelled(true);
    }

    /** Fuego que se propaga, lava que prende bloques cercanos o rayos. Los jugadores los maneja ProtectionListener. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!protegerFuego || event.getPlayer() != null) return;
        BlockIgniteEvent.IgniteCause causa = event.getCause();
        if (causa != BlockIgniteEvent.IgniteCause.SPREAD
                && causa != BlockIgniteEvent.IgniteCause.LAVA
                && causa != BlockIgniteEvent.IgniteCause.LIGHTNING) return;
        if (reclamado(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (!protegerFuego || !Tag.FIRE.isTagged(event.getSource().getType())) return;
        if (reclamado(event.getBlock())) event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Liquidos, pistones y dispensadores que cruzan el borde
    // ---------------------------------------------------------------

    /** Lava o agua fluyendo desde afuera (o desde otro dueno) hacia un chunk reclamado. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block desde = event.getBlock();
        Block hacia = event.getToBlock();
        // La gran mayoria de los flujos no cruza de chunk: salimos antes de buscar duenos.
        if (mismoChunk(desde, hacia)) return;
        if (invade(desde, hacia)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonInvade(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonInvade(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    /**
     * Un piston no puede mover bloques de (ni hacia) un territorio que no sea
     * de su mismo dueno. Se revisa el bloque movido y sus dos vecinos sobre el
     * eje del piston, asi cubre tanto empujar como tirar sin depender de hacia
     * donde reporta la direccion cada evento.
     */
    private boolean pistonInvade(Block piston, List<Block> movidos, BlockFace direccion) {
        if (invade(piston, piston.getRelative(direccion))) return true;
        for (Block movido : movidos) {
            if (invade(piston, movido)
                    || invade(piston, movido.getRelative(direccion))
                    || invade(piston, movido.getRelative(direccion.getOppositeFace()))) {
                return true;
            }
        }
        return false;
    }

    /** Dispensadores/soltadores tirando lava, agua, fuego o TNT hacia un territorio ajeno. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispensador = event.getBlock();
        if (!(dispensador.getBlockData() instanceof Directional directional)) return;
        if (invade(dispensador, dispensador.getRelative(directional.getFacing()))) event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Mobs y crecimiento
    // ---------------------------------------------------------------

    /**
     * Endermen que se llevan bloques, withers y ravagers que los rompen. No se
     * toca al resto de los mobs para no frenar cosas vanilla como las ovejas
     * comiendo pasto o los aldeanos cosechando.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Enderman || entity instanceof Wither || entity instanceof Ravager)) return;
        if (reclamado(event.getBlock())) event.setCancelled(true);
    }

    /** Arboles u hongos gigantes plantados afuera que crecen hacia adentro de un territorio ajeno. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        Block origen = event.getLocation().getBlock();
        event.getBlocks().removeIf((BlockState state) -> invade(origen, state.getBlock()));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private boolean reclamado(Block block) {
        return territory.getOwner(block.getChunk()).isPresent();
    }

    /** True si {@code destino} esta reclamado por alguien distinto del dueno de {@code origen} (o origen no tiene dueno). */
    private boolean invade(Block origen, Block destino) {
        if (mismoChunk(origen, destino)) return false;
        UUID duenoDestino = territory.getOwner(destino.getChunk()).orElse(null);
        if (duenoDestino == null) return false;
        UUID duenoOrigen = territory.getOwner(origen.getChunk()).orElse(null);
        return !Objects.equals(duenoOrigen, duenoDestino);
    }

    private static boolean mismoChunk(Block a, Block b) {
        return a.getWorld() == b.getWorld()
                && (a.getX() >> 4) == (b.getX() >> 4)
                && (a.getZ() >> 4) == (b.getZ() >> 4);
    }
}
