package com.isjbar.minercorp.mining.minion;

import com.isjbar.minercorp.economy.api.EconomyAPI;
import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.mining.company.MinionData;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import com.isjbar.minercorp.territory.api.VeinSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Coloca, elimina y hace producir a los minions (los "empleados" de la empresa). */
public class MinionManager {

    private final MiningPlugin plugin;
    private final TerritoryAPI territory;
    private final EconomyAPI economy;
    private final NamespacedKey minionIdKey;
    private final NamespacedKey companyIdKey;
    private BukkitTask task;

    public MinionManager(MiningPlugin plugin, TerritoryAPI territory, EconomyAPI economy) {
        this.plugin = plugin;
        this.territory = territory;
        this.economy = economy;
        this.minionIdKey = new NamespacedKey(plugin, "minion_id");
        this.companyIdKey = new NamespacedKey(plugin, "minion_company");
    }

    public void start() {
        long interval = plugin.getConfig().getLong("minions.intervalo-ticks", 200);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public MinionPlacement place(Company company, Location location) {
        int max = plugin.levels().minionsPermitidos(company.getLevel());
        if (company.getMinions().size() >= max) {
            return MinionPlacement.LIMITE_NIVEL;
        }

        Chunk chunk = location.getChunk();
        if (territory.getVein(chunk).isEmpty()) {
            return MinionPlacement.FUERA_DE_TERRITORIO;
        }

        double costo = plugin.getConfig().getDouble("minions.costo-base", 300)
                + company.getMinions().size() * plugin.getConfig().getDouble("minions.costo-incremento-por-minion", 150);
        if (!economy.withdraw(company.getId(), costo)) {
            return MinionPlacement.SIN_SALDO;
        }

        UUID minionId = UUID.randomUUID();
        spawnEntity(company, minionId, location);
        company.getMinions().add(new MinionData(minionId, location));
        plugin.companies().save();
        return MinionPlacement.OK;
    }

    private void spawnEntity(Company company, UUID minionId, Location location) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.customName(Component.text("Minion de " + company.getName(), NamedTextColor.GOLD));
        stand.setCustomNameVisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(true);
        stand.setSmall(true);
        stand.setBasePlate(true);
        stand.setItem(EquipmentSlot.HAND, new ItemStack(Material.IRON_PICKAXE));
        stand.getPersistentDataContainer().set(minionIdKey, PersistentDataType.STRING, minionId.toString());
        stand.getPersistentDataContainer().set(companyIdKey, PersistentDataType.STRING, company.getId().toString());
    }

    public boolean remove(Company company, MinionData minion) {
        Location loc = minion.toLocation();
        if (loc != null && loc.isWorldLoaded()) {
            Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, 1, 2, 1);
            for (Entity e : nearby) {
                String id = e.getPersistentDataContainer().get(minionIdKey, PersistentDataType.STRING);
                if (minion.getId().toString().equals(id)) {
                    e.remove();
                    break;
                }
            }
        }
        boolean removed = company.getMinions().removeIf(m -> m.getId().equals(minion.getId()));
        if (removed) plugin.companies().save();
        return removed;
    }

    public Optional<MinionData> nearest(Company company, Location location, double maxDistance) {
        return company.getMinions().stream()
                .filter(m -> {
                    Location l = m.toLocation();
                    return l != null && l.getWorld().equals(location.getWorld()) && l.distance(location) <= maxDistance;
                })
                .min((a, b) -> Double.compare(a.toLocation().distance(location), b.toLocation().distance(location)));
    }

    private void tick() {
        int produccion = plugin.getConfig().getInt("minions.produccion-por-ciclo", 2);
        for (Company company : plugin.companies().all()) {
            for (MinionData minion : company.getMinions()) {
                Location loc = minion.toLocation();
                if (loc == null) continue;
                Chunk chunk = loc.getChunk();
                Optional<VeinSnapshot> vein = territory.getVein(chunk);
                if (vein.isEmpty() || vein.get().agotada()) continue;
                double extraido = territory.extractFromVein(chunk, produccion);
                if (extraido > 0) {
                    company.addRawCoal(extraido);
                    company.addXp(extraido * 0.5);
                }
            }
            plugin.companies().checkLevelUp(company);
        }
        plugin.companies().save();
    }

    public enum MinionPlacement {
        OK, LIMITE_NIVEL, FUERA_DE_TERRITORIO, SIN_SALDO
    }
}
