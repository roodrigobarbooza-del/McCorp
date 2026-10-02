package com.isjbar.minercorp.resources.machine;

import com.isjbar.minercorp.resources.ResourcesPlugin;
import com.isjbar.minercorp.resources.oil.OilFieldManager;
import com.isjbar.minercorp.resources.resource.ResourceRegistry;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import com.isjbar.minercorp.territory.api.VeinSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tipos de maquina, maquinas colocadas, su ciclo de trabajo y su guardado
 * (maquinas.yml). Las maquinas trabajan solo mientras su chunk esta cargado,
 * igual que un horno vanilla.
 */
public class MachineManager {

    /** Cada cuantos ticks avanza el ciclo de todas las maquinas. */
    private static final int STEP = 10;

    private final ResourcesPlugin plugin;
    private final ResourceRegistry resources;
    private final OilFieldManager oil;
    private final TerritoryAPI territory;
    private final File file;
    private final NamespacedKey itemKey;

    private final Map<String, MachineType> types = new LinkedHashMap<>();
    private final Map<String, Integer> fuelTicks = new HashMap<>();
    private final Map<UUID, Machine> machines = new LinkedHashMap<>();
    private final Map<Block, Machine> byBlock = new HashMap<>();
    /** Maquinas de mundos que no estan cargados: se guardan tal cual para no perderlas. */
    private final Map<String, ConfigurationSection> unloaded = new LinkedHashMap<>();

    private BukkitTask task;
    private boolean dirty;
    private int ticksSinceSave;

    public MachineManager(ResourcesPlugin plugin, ResourceRegistry resources, OilFieldManager oil, TerritoryAPI territory) {
        this.plugin = plugin;
        this.resources = resources;
        this.oil = oil;
        this.territory = territory;
        this.file = new File(plugin.getDataFolder(), "maquinas.yml");
        this.itemKey = new NamespacedKey(plugin, "maquina");
        loadTypes(plugin.getConfig());
        load();
    }

    // ------------------------------------------------------------------ tipos

    public void loadTypes(FileConfiguration config) {
        types.clear();
        ConfigurationSection root = config.getConfigurationSection("maquinas");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec != null) types.put(id.toLowerCase(Locale.ROOT), MachineType.parse(id.toLowerCase(Locale.ROOT), sec, plugin.getLogger()));
            }
        }
        fuelTicks.clear();
        ConfigurationSection fuels = config.getConfigurationSection("combustibles");
        if (fuels != null) {
            for (String k : fuels.getKeys(false)) {
                String key = k.equals(k.toUpperCase(Locale.ROOT)) ? k : k.toLowerCase(Locale.ROOT);
                fuelTicks.put(key, (int) Math.round(fuels.getDouble(k) * 20));
            }
        }
    }

    public Collection<MachineType> types() {
        return types.values();
    }

    public Optional<MachineType> type(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(types.get(id.toLowerCase(Locale.ROOT)));
    }

    public int fuelTicks(String key) {
        return fuelTicks.getOrDefault(key, 0);
    }

    // ----------------------------------------------------------------- items

    public Optional<ItemStack> createItem(String id) {
        return type(id).map(this::createItem);
    }

    public ItemStack createItem(MachineType type) {
        ItemStack item = new ItemStack(type.block());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.name(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : type.description()) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Colocala en el territorio de tu empresa.", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Maquina MinerCorp", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<MachineType> identifyItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return Optional.empty();
        return type(item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    // -------------------------------------------------------------- maquinas

    public Optional<Machine> at(Block block) {
        return Optional.ofNullable(byBlock.get(block));
    }

    public Collection<Machine> all() {
        return machines.values();
    }

    public Machine place(MachineType type, Block block, UUID owner, int facing) {
        Machine machine = new Machine(UUID.randomUUID(), type, block, owner, facing);
        machine.refreshPanel();
        machines.put(machine.id(), machine);
        byBlock.put(block, machine);
        showModel(machine);
        dirty = true;
        save();
        return machine;
    }

    /** Saca la maquina del mundo (el bloque lo maneja quien llama). Devuelve su item y su contenido. */
    public List<ItemStack> remove(Machine machine) {
        List<ItemStack> drops = new ArrayList<>(machine.contents());
        drops.add(0, createItem(machine.type()));
        for (var viewer : new ArrayList<>(machine.inventory().getViewers())) viewer.closeInventory();
        hideModel(machine);
        machines.remove(machine.id());
        byBlock.remove(machine.block());
        dirty = true;
        save();
        return drops;
    }

    // --------------------------------------------------------------- modelos

    public void showModel(Machine machine) {
        if (machine.model != null) return;
        machine.model = MachineModel.spawn(plugin, machine);
        machine.model.setStatus(label(machine), machine.isRunning());
    }

    public void hideModel(Machine machine) {
        if (machine.model == null) return;
        machine.model.remove();
        machine.model = null;
    }

    public void onChunkLoad(Chunk chunk) {
        for (Machine m : machines.values()) {
            if (inChunk(m, chunk)) showModel(m);
        }
    }

    public void onChunkUnload(Chunk chunk) {
        for (Machine m : machines.values()) {
            if (inChunk(m, chunk)) hideModel(m);
        }
    }

    private static boolean inChunk(Machine m, Chunk chunk) {
        Block b = m.block();
        return b.getWorld().equals(chunk.getWorld()) && (b.getX() >> 4) == chunk.getX() && (b.getZ() >> 4) == chunk.getZ();
    }

    private static Component label(Machine m) {
        return Component.text(m.type().name(), NamedTextColor.AQUA)
                .append(Component.newline())
                .append(m.status().component());
    }

    // ----------------------------------------------------------------- ciclo

    public void start() {
        // Restos de modelos de una caida del server (no deberian existir: no son persistentes).
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (MachineModel.isModelPart(plugin, e)) e.remove();
            }
        }
        for (Machine m : machines.values()) {
            if (isLoaded(m)) showModel(m);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, STEP, STEP);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Machine m : machines.values()) hideModel(m);
        save();
        oil.save();
    }

    private void tick() {
        boolean particles = plugin.getConfig().getBoolean("efectos.particulas", true);
        boolean sounds = plugin.getConfig().getBoolean("efectos.sonidos", true);
        for (Machine m : machines.values()) {
            if (!isLoaded(m)) continue;
            Machine.Status before = m.status;
            work(m);
            if (before != m.status && m.model != null) m.model.setStatus(label(m), m.isRunning());
            if (m.model != null) m.model.animate(STEP, particles, sounds);
            if (!m.inventory().getViewers().isEmpty()) m.refreshPanel();
        }
        ticksSinceSave += STEP;
        if (ticksSinceSave >= plugin.getConfig().getInt("guardado-segundos", 60) * 20) {
            ticksSinceSave = 0;
            if (dirty) save();
            oil.saveIfDirty();
        }
    }

    private static boolean isLoaded(Machine m) {
        Block b = m.block();
        return b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4);
    }

    /** Un paso del ciclo: decide si puede trabajar, quema combustible y, al completar el ciclo, produce. */
    private void work(Machine m) {
        Machine.Status blocked = blocker(m);
        if (blocked != null) {
            m.status = blocked;
            return;
        }
        if (m.burnLeft <= 0 && !refuel(m)) {
            m.status = Machine.Status.SIN_COMBUSTIBLE;
            return;
        }
        m.status = Machine.Status.TRABAJANDO;
        m.burnLeft -= STEP;
        m.progress += STEP;
        dirty = true;
        if (m.progress >= m.type().cycleTicks()) {
            m.progress = 0;
            produce(m);
        }
    }

    /** Por que no puede trabajar ahora, o null si puede. */
    private Machine.Status blocker(Machine m) {
        MachineType type = m.type();
        Chunk chunk = m.block().getChunk();
        switch (type.kind()) {
            case VETA_CARBON -> {
                Optional<VeinSnapshot> vein = territory.getVein(chunk);
                if (vein.isEmpty() || vein.get().agotada()) return Machine.Status.VETA_AGOTADA;
            }
            case PETROLEO -> {
                if (oil.remaining(chunk) <= 0) return Machine.Status.SIN_PETROLEO;
            }
            case PROCESO -> {
                for (Map.Entry<String, Integer> in : type.input().entrySet()) {
                    ItemStack item = m.inventory().getItem(Machine.SLOT_INPUT);
                    if (!resources.itemKey(item).map(in.getKey()::equals).orElse(false)
                            || item.getAmount() < in.getValue()) {
                        return Machine.Status.SIN_ENTRADA;
                    }
                }
            }
        }
        for (MachineType.Output out : type.outputs()) {
            Optional<ItemStack> item = resources.create(out.resource(), (int) Math.ceil(out.amount()));
            if (item.isPresent() && !fits(m.inventory(), item.get())) return Machine.Status.SALIDA_LLENA;
        }
        return null;
    }

    private boolean refuel(Machine m) {
        ItemStack fuel = m.inventory().getItem(Machine.SLOT_FUEL);
        Optional<String> key = resources.itemKey(fuel);
        if (key.isEmpty() || !m.type().acceptsFuel(key.get())) return false;
        int ticks = fuelTicks(key.get());
        if (ticks <= 0) return false;
        fuel.setAmount(fuel.getAmount() - 1);
        m.inventory().setItem(Machine.SLOT_FUEL, fuel.getAmount() <= 0 ? null : fuel);
        m.burnLeft = ticks;
        m.burnMax = ticks;
        return true;
    }

    private void produce(Machine m) {
        MachineType type = m.type();
        Chunk chunk = m.block().getChunk();
        if (type.kind() == MachineType.Kind.PROCESO) {
            for (Map.Entry<String, Integer> in : type.input().entrySet()) {
                ItemStack item = m.inventory().getItem(Machine.SLOT_INPUT);
                item.setAmount(item.getAmount() - in.getValue());
                m.inventory().setItem(Machine.SLOT_INPUT, item.getAmount() <= 0 ? null : item);
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (MachineType.Output out : type.outputs()) {
                if (random.nextDouble() >= out.chance()) continue;
                give(m, out.resource(), (int) Math.round(out.amount()));
            }
            return;
        }
        MachineType.Output out = type.outputs().get(0);
        double got = type.kind() == MachineType.Kind.PETROLEO
                ? oil.extract(chunk, out.amount())
                : territory.extractFromVein(chunk, out.amount());
        m.pending += got;
        int whole = (int) Math.floor(m.pending + 1e-9);
        m.pending -= whole;
        give(m, out.resource(), whole);
    }

    private void give(Machine m, String resource, int amount) {
        if (amount <= 0) return;
        Optional<ItemStack> item = resources.create(resource, amount);
        if (item.isEmpty()) return;
        ItemStack stack = item.get();
        stack.setAmount(amount);
        ItemStack left = addToOutputs(m.inventory(), stack);
        if (left != null) {
            Location drop = m.block().getLocation().add(0.5, 1.2, 0.5);
            drop.getWorld().dropItemNaturally(drop, left);
        }
    }

    /** true si el item entra entero en los slots de salida. */
    static boolean fits(Inventory inv, ItemStack item) {
        int left = item.getAmount();
        for (int slot : Machine.SLOT_OUTPUTS) {
            ItemStack in = inv.getItem(slot);
            if (in == null || in.getType().isAir()) left -= item.getMaxStackSize();
            else if (in.isSimilar(item)) left -= in.getMaxStackSize() - in.getAmount();
            if (left <= 0) return true;
        }
        return false;
    }

    /** Agrega a los slots de salida; devuelve lo que no entro, o null. */
    static ItemStack addToOutputs(Inventory inv, ItemStack item) {
        int left = item.getAmount();
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int slot : Machine.SLOT_OUTPUTS) {
                if (left <= 0) break;
                ItemStack in = inv.getItem(slot);
                if (pass == 0 && in != null && in.isSimilar(item)) {
                    int add = Math.min(left, in.getMaxStackSize() - in.getAmount());
                    in.setAmount(in.getAmount() + add);
                    inv.setItem(slot, in);
                    left -= add;
                } else if (pass == 1 && (in == null || in.getType().isAir())) {
                    int add = Math.min(left, item.getMaxStackSize());
                    ItemStack copy = item.clone();
                    copy.setAmount(add);
                    inv.setItem(slot, copy);
                    left -= add;
                }
            }
        }
        if (left <= 0) return null;
        ItemStack rest = item.clone();
        rest.setAmount(left);
        return rest;
    }

    public void markDirty() {
        dirty = true;
    }

    // --------------------------------------------------------------- guardado

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Machine m : machines.values()) {
            ConfigurationSection sec = yaml.createSection("maquinas." + m.id());
            Block b = m.block();
            sec.set("tipo", m.type().id());
            sec.set("mundo", b.getWorld().getName());
            sec.set("x", b.getX());
            sec.set("y", b.getY());
            sec.set("z", b.getZ());
            sec.set("orientacion", m.facing());
            if (m.owner() != null) sec.set("dueno", m.owner().toString());
            sec.set("combustible-restante", m.burnLeft);
            sec.set("combustible-max", m.burnMax);
            sec.set("progreso", m.progress);
            sec.set("pendiente", m.pending);
            for (int i = 0; i < m.inventory().getSize(); i++) {
                if (!m.isFunctionalSlot(i)) continue;
                ItemStack item = m.inventory().getItem(i);
                if (item != null && !item.getType().isAir()) sec.set("items." + i, item);
            }
        }
        unloaded.forEach((id, sec) -> yaml.set("maquinas." + id, sec));
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar maquinas.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("maquinas");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            World world = plugin.getServer().getWorld(sec.getString("mundo", ""));
            Optional<MachineType> type = type(sec.getString("tipo"));
            if (world == null || type.isEmpty()) {
                plugin.getLogger().warning("Maquina " + id + ": mundo o tipo no encontrado, se guarda sin cargar.");
                unloaded.put(id, sec);
                continue;
            }
            Block block = world.getBlockAt(sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
            String owner = sec.getString("dueno");
            Machine m = new Machine(UUID.fromString(id), type.get(), block,
                    owner == null ? null : UUID.fromString(owner), sec.getInt("orientacion", 0));
            m.burnLeft = sec.getInt("combustible-restante");
            m.burnMax = sec.getInt("combustible-max");
            m.progress = sec.getInt("progreso");
            m.pending = sec.getDouble("pendiente");
            ConfigurationSection items = sec.getConfigurationSection("items");
            if (items != null) {
                for (String slot : items.getKeys(false)) {
                    ItemStack item = items.getItemStack(slot);
                    if (item != null) m.inventory().setItem(Integer.parseInt(slot), item);
                }
            }
            m.refreshPanel();
            machines.put(m.id(), m);
            byBlock.put(block, m);
        }
    }
}
