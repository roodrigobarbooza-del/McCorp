package com.isjbar.minercorp.gransede.obra;

import com.isjbar.minercorp.gransede.GranSedePlugin;
import com.isjbar.minercorp.gransede.zona.Zona;
import com.isjbar.minercorp.gransede.zona.ZonaProteccionListener;
import com.isjbar.minercorp.gransede.zona.ZonaTipo;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * La Gran Sede la construye el plugin, como la obra de la sede de cada
 * empresa en Mining: el admin elige el lugar con un Plano (con vista previa
 * de donde va cada cosa), confirma, el terreno se despeja solo y el plano se
 * levanta por etapas con la misma animacion de bloques que caen a su lugar.
 *
 * Al terminar crea las zonas (sede, mina y bosque) en zonas.yml, marca el
 * spawn y pone los vendedores, asi la Gran Sede queda andando sin hacer
 * nada mas a mano.
 */
public class ObraGranSedeManager {

    public static final String ZONA_SEDE = "gran-sede";
    public static final String ZONA_MINA = "gran-sede-mina";
    public static final String ZONA_BOSQUE = "gran-sede-bosque";

    private final GranSedePlugin plugin;
    private final PlanoGranSede plano;
    private final File file;
    private final NamespacedKey planoKey;
    private final Random random = new Random();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, Ubicacion> pendientes = new HashMap<>();
    private final Set<UUID> viendoBarra = new HashSet<>();
    private final BossBar barra = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);

    private ObraGranSede obra;
    private int etapaAnunciada = -1;
    private boolean dirty;
    private int tickGuardado;

    public ObraGranSedeManager(GranSedePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "obra.yml");
        this.planoKey = new NamespacedKey(plugin, "plano_gran_sede");
        String archivo = plugin.getConfig().getString("obra.archivo-estructura", "estructuras/gransede.nbt");
        this.plano = PlanoGranSede.cargar(new File(plugin.getDataFolder(), archivo), plugin.getLogger());
        load();
    }

    public void start() {
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVistaPrevia, 5, 5));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::actualizarBarra, 20, 20));
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        ocultarBarra();
        if (obra != null) tickets(false);
        save();
    }

    public PlanoGranSede plano() {
        return plano;
    }

    public Optional<ObraGranSede> obra() {
        return Optional.ofNullable(obra);
    }

    // ---------------------------------------------------------------
    // Plano (item) y ubicacion
    // ---------------------------------------------------------------

    public ItemStack crearPlano() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Plano de la Gran Sede", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                gris("Mira al suelo donde va el centro de la plaza."),
                gris("La entrada queda del lado donde estas parado."),
                gris(plano.ancho() + " x " + plano.fondo() + " bloques. Clic derecho para elegir.")));
        meta.getPersistentDataContainer().set(planoKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean esPlano(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(planoKey, PersistentDataType.BYTE);
    }

    public record Ubicacion(World world, int ax, int ay, int az, BlockFace frente, String error) {
        public boolean valida() {
            return error == null;
        }
    }

    public Ubicacion ubicacionPara(Player player) {
        Block target = player.getTargetBlockExact(64, FluidCollisionMode.NEVER);
        if (target == null) return null;
        for (int i = 0; i < 3 && !target.getType().isSolid(); i++) target = target.getRelative(BlockFace.DOWN);
        BlockFace frente = player.getFacing().getOppositeFace();
        World w = target.getWorld();
        int ax = target.getX(), ay = target.getY(), az = target.getZ();

        String error = null;
        if (ay + plano.alto() + alturaDespeje() >= w.getMaxHeight() || ay - 8 <= w.getMinHeight()) {
            error = "No entra a esa altura.";
        } else {
            for (int[] e : esquinas(frente, ax, ay, az)) {
                if (!w.isChunkLoaded(e[0] >> 4, e[2] >> 4)) {
                    error = "Acercate al centro: hay partes del terreno sin cargar.";
                    break;
                }
            }
        }
        return new Ubicacion(w, ax, ay, az, frente, error);
    }

    private int[][] esquinas(BlockFace frente, int ax, int ay, int az) {
        return new int[][]{
                ObraGranSede.aMundo(plano, frente, ax, ay, az, 0, 0, 0),
                ObraGranSede.aMundo(plano, frente, ax, ay, az, plano.ancho() - 1, 0, 0),
                ObraGranSede.aMundo(plano, frente, ax, ay, az, 0, 0, plano.fondo() - 1),
                ObraGranSede.aMundo(plano, frente, ax, ay, az, plano.ancho() - 1, 0, plano.fondo() - 1)};
    }

    /** Clic derecho con el Plano: guarda el lugar y pide confirmar (despeja un area grande). */
    public void elegir(Player player) {
        if (obra != null) {
            player.sendMessage(Component.text("Ya hay una obra de la Gran Sede en curso. /gransede construir estado", NamedTextColor.RED));
            return;
        }
        Ubicacion u = ubicacionPara(player);
        if (u == null) {
            player.sendMessage(Component.text("Mira al suelo donde va el centro de la Gran Sede.", NamedTextColor.RED));
            return;
        }
        if (!u.valida()) {
            player.sendMessage(Component.text(u.error(), NamedTextColor.RED));
            return;
        }
        pendientes.put(player.getUniqueId(), u);
        player.sendMessage(Component.text("Lugar elegido en " + u.ax() + ", " + u.ay() + ", " + u.az()
                + ". Se va a despejar todo en " + plano.ancho() + " x " + plano.fondo() + " bloques y "
                + (plano.alto() + alturaDespeje()) + " de alto, y construir la Gran Sede. ", NamedTextColor.YELLOW)
                .append(Component.text("[Confirmar]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/gransede construir confirmar"))));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    /** Arranca la obra en el lugar elegido. Devuelve un error o null. */
    public String confirmar(Player player) {
        if (obra != null) return "Ya hay una obra de la Gran Sede en curso.";
        Ubicacion u = pendientes.remove(player.getUniqueId());
        if (u == null) return "Primero elegi el lugar con el Plano (/gransede construir).";
        obra = new ObraGranSede(u.world().getName(), u.ax(), u.ay(), u.az(), u.frente());
        etapaAnunciada = -1;
        tickets(true);
        dirty = true;
        save();
        anunciar(Component.text("Empieza la obra de la Gran Sede: despejando el terreno.", NamedTextColor.GOLD), Sound.BLOCK_ANVIL_PLACE);
        return null;
    }

    /** Frena la obra. Lo que ya se construyo queda en pie; las zonas no se tocan. */
    public boolean cancelar() {
        if (obra == null) return false;
        tickets(false);
        obra = null;
        ocultarBarra();
        dirty = true;
        save();
        return true;
    }

    public int porcentaje() {
        if (obra == null) return 0;
        if (obra.fase == ObraGranSede.Fase.DESPEJE) return obra.cursor * 100 / (plano.ancho() * plano.fondo());
        return obra.cursor * 100 / Math.max(1, plano.piezas().size());
    }

    // ---------------------------------------------------------------
    // Despeje y construccion
    // ---------------------------------------------------------------

    private void tick() {
        if (obra == null) return;
        World w = obra.mundo();
        if (w == null) return;
        if (obra.fase == ObraGranSede.Fase.DESPEJE) despejar(w);
        else construir(w);
        if (++tickGuardado >= 100) {
            tickGuardado = 0;
            if (dirty) save();
        }
    }

    /** Vacia el volumen del plano de arriba hacia abajo, unas columnas por tick. */
    private void despejar(World w) {
        int total = plano.ancho() * plano.fondo();
        int porTick = Math.max(1, plugin.getConfig().getInt("obra.columnas-despeje-por-tick", 40));
        int techo = plano.alto() + alturaDespeje();
        for (int n = 0; n < porTick && obra.cursor < total; n++, obra.cursor++) {
            int lx = obra.cursor % plano.ancho(), lz = obra.cursor / plano.ancho();
            for (int ly = techo; ly >= 1; ly--) {
                int[] p = obra.aMundo(plano, lx, ly, lz);
                Block b = w.getBlockAt(p[0], p[1], p[2]);
                if (b.getType().isAir()) continue;
                if (random.nextInt(60) == 0) {
                    w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0, b.getBlockData());
                }
                b.setType(Material.AIR, false);
            }
        }
        dirty = true;
        if (obra.cursor >= total) {
            quitarItemsSueltos(w);
            obra.fase = ObraGranSede.Fase.CONSTRUCCION;
            obra.cursor = 0;
            anunciar(Component.text("Terreno despejado. Se levanta la Gran Sede.", NamedTextColor.GOLD), Sound.BLOCK_NOTE_BLOCK_BELL);
        }
    }

    private void construir(World w) {
        List<PlanoGranSede.Pieza> piezas = plano.piezas();
        int porTick = Math.max(1, plugin.getConfig().getInt("obra.bloques-por-tick", 20));
        int maxAnimados = Math.max(0, plugin.getConfig().getInt("obra.animaciones-a-la-vez", 16));
        for (int n = 0; n < porTick && obra.cursor < piezas.size(); n++) {
            int indice = obra.cursor++;
            anunciarEtapa(plano.etapaDe(indice));
            PlanoGranSede.Pieza pieza = piezas.get(indice);
            int[] pos = obra.aMundo(plano, pieza.x(), pieza.y(), pieza.z());
            Block block = w.getBlockAt(pos[0], pos[1], pos[2]);
            BlockData data = obra.dataRotada(pieza.data());
            if (block.getBlockData().matches(data)) continue;
            if (obra.enVuelo < maxAnimados && random.nextInt(6) == 0) {
                animar(obra, block, data, pieza.y() == 0);
            } else {
                colocar(block, data, pieza.y() == 0, random.nextInt(10) == 0);
            }
        }
        dirty = true;
        if (obra.cursor >= piezas.size() && obra.enVuelo == 0) inaugurar(w);
    }

    /** Igual que la obra de Mining: un BlockDisplay chico que baja y crece hasta su lugar. */
    private void animar(ObraGranSede o, Block block, BlockData data, boolean piso) {
        o.enVuelo++;
        BlockDisplay fantasma = block.getWorld().spawn(block.getLocation(), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(data);
            d.setTransformation(new Transformation(new Vector3f(0.2f, 0.9f, 0.2f), new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!fantasma.isValid()) return;
            fantasma.setInterpolationDelay(0);
            fantasma.setInterpolationDuration(5);
            fantasma.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
        }, 2);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            fantasma.remove();
            o.enVuelo--;
            if (obra != o) return; // se cancelo mientras tanto
            colocar(block, data, piso, true);
        }, 7);
    }

    private void colocar(Block block, BlockData data, boolean piso, boolean efecto) {
        if (piso) rellenarDebajo(block);
        block.setBlockData(data, false);
        if (efecto) {
            World w = block.getWorld();
            Location c = block.getLocation().add(0.5, 0.5, 0.5);
            w.playSound(c, data.getSoundGroup().getPlaceSound(), 0.7f, 0.85f + random.nextFloat() * 0.3f);
            w.spawnParticle(Particle.BLOCK, c, 6, 0.3, 0.3, 0.3, 0, data);
        }
    }

    /** El piso tapa los pozos que haya debajo con tierra, hasta 6 bloques. */
    private void rellenarDebajo(Block block) {
        Block b = block.getRelative(BlockFace.DOWN);
        for (int i = 0; i < 6 && b.getY() > b.getWorld().getMinHeight(); i++) {
            if (b.getType().isSolid()) break;
            b.setType(Material.DIRT, false);
            b = b.getRelative(BlockFace.DOWN);
        }
    }

    private void anunciarEtapa(int etapa) {
        if (etapa == etapaAnunciada) return;
        etapaAnunciada = etapa;
        anunciar(Component.text("Gran Sede: " + PlanoGranSede.ETAPAS[etapa] + " (" + (etapa + 1) + "/"
                + PlanoGranSede.ETAPAS.length + ")", NamedTextColor.YELLOW), Sound.BLOCK_NOTE_BLOCK_BELL);
    }

    // ---------------------------------------------------------------
    // Inauguracion: zonas, spawn, vendedores
    // ---------------------------------------------------------------

    private void inaugurar(World w) {
        ObraGranSede o = obra;
        obra = null;
        tickets(false);
        ocultarBarra();

        // Zonas. La de la sede protege tambien un poco abajo y arriba del edificio.
        int[][] e = esquinas(o.frente(), o.ax(), o.ay(), o.az());
        Location a = new Location(w, e[0][0], o.ay() - 8, e[0][2]);
        Location b = new Location(w, e[3][0], o.ay() + plano.alto() + 16, e[3][2]);
        plugin.zonas().put(Zona.entre(ZONA_SEDE, ZonaTipo.SEDE, a, b));
        zonaDeMarcadores(o, w, "mina", ZONA_MINA, ZonaTipo.MINA);
        zonaDeMarcadores(o, w, "bosque", ZONA_BOSQUE, ZonaTipo.BOSQUE);

        // Spawn mirando hacia adentro de la plaza.
        for (Marcador m : plano.marcadores("spawn")) {
            int[] p = o.aMundo(plano, m.x(), m.y(), m.z());
            plugin.zonas().setSpawn(new Location(w, p[0] + 0.5, p[1], p[2] + 0.5, o.yawFrente() + 180f, 0f));
        }

        // Vendedores: se sacan los que hubiera en el area y se ponen los del plano.
        Location centro = o.centro();
        double radio = Math.max(plano.ancho(), plano.fondo()) / 2.0 + 2;
        for (Entity ent : w.getNearbyEntities(centro, radio, plano.alto() + 8, radio)) {
            if (plugin.vendedores().esVendedor(ent)) ent.remove();
        }
        for (Marcador m : plano.marcadores("npc")) {
            int[] p = o.aMundo(plano, m.x(), m.y(), m.z());
            Location loc = new Location(w, p[0] + 0.5, p[1], p[2] + 0.5, o.yawFrente(), 0f);
            plugin.tiendas().tienda(m.valor()).ifPresentOrElse(
                    t -> plugin.vendedores().crear(t, loc),
                    () -> plugin.getLogger().warning("El plano pide un vendedor de '" + m.valor() + "' pero esa tienda no esta en el config."));
        }

        dirty = true;
        save();

        Location techo = centro.clone().add(0, plano.alto(), 0);
        for (int i = 0; i < 5; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> fuego(techo.clone().add(
                    random.nextDouble() * 16 - 8, random.nextDouble() * 4, random.nextDouble() * 16 - 8)), i * 6L);
        }
        Title titulo = Title.title(Component.text("Gran Sede inaugurada", NamedTextColor.GOLD),
                Component.text("Tiendas, mina y bosque abiertos", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(w) && p.getLocation().distanceSquared(centro) <= radioBarra() * radioBarra()) {
                p.showTitle(titulo);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        Bukkit.broadcast(Component.text("La Gran Sede abrio sus puertas. /gransede ir", NamedTextColor.GOLD));
    }

    private void zonaDeMarcadores(ObraGranSede o, World w, String tipo, String nombre, ZonaTipo zonaTipo) {
        List<Marcador> ms = plano.marcadores(tipo);
        if (ms.size() < 2) return;
        int[] p1 = o.aMundo(plano, ms.get(0).x(), ms.get(0).y(), ms.get(0).z());
        int[] p2 = o.aMundo(plano, ms.get(1).x(), ms.get(1).y(), ms.get(1).z());
        plugin.zonas().put(Zona.entre(nombre, zonaTipo,
                new Location(w, p1[0], p1[1], p1[2]), new Location(w, p2[0], p2[1], p2[2])));
    }

    private void fuego(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).trail(true).build());
            f.setFireworkMeta(meta);
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, fw::detonate, 1);
    }

    private void quitarItemsSueltos(World w) {
        Location c = obra.centro();
        double r = Math.max(plano.ancho(), plano.fondo()) / 2.0 + 1;
        for (Entity e : w.getNearbyEntities(c, r, plano.alto() + alturaDespeje(), r)) {
            if (e instanceof org.bukkit.entity.Item) e.remove();
        }
    }

    // ---------------------------------------------------------------
    // Vista previa, barra, avisos
    // ---------------------------------------------------------------

    private void tickVistaPrevia() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!esPlano(p.getInventory().getItemInMainHand()) || !p.hasPermission(ZonaProteccionListener.PERMISO_ADMIN)) continue;
            if (obra != null) {
                p.sendActionBar(Component.text("Obra en curso: " + porcentaje() + "%", NamedTextColor.GRAY));
                continue;
            }
            Ubicacion u = ubicacionPara(p);
            if (u == null) {
                p.sendActionBar(Component.text("Mira al suelo donde va el centro de la Gran Sede", NamedTextColor.GRAY));
                continue;
            }
            Color color = u.valida() ? Color.fromRGB(0x55FF55) : Color.fromRGB(0xFF5555);
            contorno(p, u, 0, 0, plano.ancho() - 1, plano.fondo() - 1, u.ay() + 1.1, new Particle.DustOptions(color, 1.5f), 1.0);
            // Donde va cada cosa: mina gris, bosque verde oscuro, vendedores dorados, spawn celeste.
            caja(p, u, "mina", new Particle.DustOptions(Color.fromRGB(0x9A9A9A), 1.2f));
            caja(p, u, "bosque", new Particle.DustOptions(Color.fromRGB(0x2E8B3A), 1.2f));
            for (Marcador m : plano.marcadores("npc")) columna(p, u, m, Color.fromRGB(0xFFD24A));
            for (Marcador m : plano.marcadores("spawn")) columna(p, u, m, Color.fromRGB(0x55CCFF));
            p.sendActionBar(u.valida()
                    ? Component.text("Clic derecho para construir la Gran Sede aca (dorado: vendedores, celeste: llegada)", NamedTextColor.GREEN)
                    : Component.text(u.error(), NamedTextColor.RED));
        }
    }

    private void caja(Player p, Ubicacion u, String tipo, Particle.DustOptions polvo) {
        List<Marcador> ms = plano.marcadores(tipo);
        if (ms.size() < 2) return;
        contorno(p, u, ms.get(0).x(), ms.get(0).z(), ms.get(1).x(), ms.get(1).z(), u.ay() + 1.3, polvo, 1.0);
    }

    private void contorno(Player p, Ubicacion u, int lx0, int lz0, int lx1, int lz1, double y, Particle.DustOptions polvo, double paso) {
        int[] a = ObraGranSede.aMundo(plano, u.frente(), u.ax(), u.ay(), u.az(), lx0, 0, lz0);
        int[] b = ObraGranSede.aMundo(plano, u.frente(), u.ax(), u.ay(), u.az(), lx1, 0, lz1);
        double x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]) + 1;
        double z0 = Math.min(a[2], b[2]), z1 = Math.max(a[2], b[2]) + 1;
        for (double x = x0; x <= x1; x += paso) {
            p.spawnParticle(Particle.DUST, x, y, z0, 1, polvo);
            p.spawnParticle(Particle.DUST, x, y, z1, 1, polvo);
        }
        for (double z = z0; z <= z1; z += paso) {
            p.spawnParticle(Particle.DUST, x0, y, z, 1, polvo);
            p.spawnParticle(Particle.DUST, x1, y, z, 1, polvo);
        }
    }

    private void columna(Player p, Ubicacion u, Marcador m, Color color) {
        int[] c = ObraGranSede.aMundo(plano, u.frente(), u.ax(), u.ay(), u.az(), m.x(), m.y(), m.z());
        Particle.DustOptions polvo = new Particle.DustOptions(color, 1.4f);
        for (double dy = 0; dy <= 3; dy += 0.5) p.spawnParticle(Particle.DUST, c[0] + 0.5, c[1] + dy, c[2] + 0.5, 1, polvo);
    }

    private void actualizarBarra() {
        if (obra == null) return;
        String texto = obra.fase == ObraGranSede.Fase.DESPEJE
                ? "Despejando el terreno de la Gran Sede: " + porcentaje() + "%"
                : "Construyendo la Gran Sede (" + PlanoGranSede.ETAPAS[plano.etapaDe(Math.min(obra.cursor, plano.piezas().size() - 1))]
                + "): " + porcentaje() + "%";
        barra.name(Component.text(texto, obra.fase == ObraGranSede.Fase.DESPEJE ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        barra.color(obra.fase == ObraGranSede.Fase.DESPEJE ? BossBar.Color.YELLOW : BossBar.Color.GREEN);
        barra.progress(Math.max(0f, Math.min(1f, porcentaje() / 100f)));
        Location centro = obra.centro();
        Set<UUID> ahora = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (centro.getWorld() != null && p.getWorld().equals(centro.getWorld())
                    && p.getLocation().distanceSquared(centro) <= radioBarra() * radioBarra()) {
                ahora.add(p.getUniqueId());
                p.showBossBar(barra);
            }
        }
        for (UUID id : viendoBarra) {
            if (ahora.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(barra);
        }
        viendoBarra.clear();
        viendoBarra.addAll(ahora);
    }

    private void ocultarBarra() {
        for (UUID id : viendoBarra) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(barra);
        }
        viendoBarra.clear();
    }

    private void anunciar(Component msg, Sound sonido) {
        if (obra == null) return;
        Location c = obra.centro();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean cerca = c.getWorld() != null && p.getWorld().equals(c.getWorld()) && p.getLocation().distanceSquared(c) <= radioBarra() * radioBarra();
            if (cerca || p.hasPermission(ZonaProteccionListener.PERMISO_ADMIN)) {
                p.sendMessage(msg);
                if (cerca) p.playSound(p.getLocation(), sonido, 0.8f, 1f);
            }
        }
    }

    /** Mientras dura la obra, los chunks del area no se descargan aunque el admin se vaya. */
    private void tickets(boolean poner) {
        if (obra == null || obra.conTicket == poner) return;
        World w = obra.mundo();
        if (w == null) return;
        Set<Long> chunks = new HashSet<>();
        for (int lx = 0; lx < plano.ancho(); lx += 8) {
            for (int lz = 0; lz < plano.fondo(); lz += 8) {
                int[] p = obra.aMundo(plano, Math.min(lx, plano.ancho() - 1), 0, Math.min(lz, plano.fondo() - 1));
                chunks.add(((long) (p[0] >> 4) << 32) | ((p[2] >> 4) & 0xFFFFFFFFL));
            }
        }
        for (int[] e : esquinas(obra.frente(), obra.ax(), obra.ay(), obra.az())) {
            chunks.add(((long) (e[0] >> 4) << 32) | ((e[2] >> 4) & 0xFFFFFFFFL));
        }
        for (long k : chunks) {
            int cx = (int) (k >> 32), cz = (int) k;
            if (poner) w.addPluginChunkTicket(cx, cz, plugin);
            else w.removePluginChunkTicket(cx, cz, plugin);
        }
        obra.conTicket = poner;
    }

    private int alturaDespeje() {
        return Math.max(0, plugin.getConfig().getInt("obra.altura-despeje", 10));
    }

    private double radioBarra() {
        return plugin.getConfig().getDouble("obra.radio-barra", 96);
    }

    private static Component gris(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    // ---------------------------------------------------------------
    // Persistencia (obra.yml)
    // ---------------------------------------------------------------

    public void save() {
        dirty = false;
        if (obra == null) {
            if (file.exists() && !file.delete()) plugin.getLogger().warning("No se pudo borrar obra.yml");
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", obra.world());
        y.set("x", obra.ax());
        y.set("y", obra.ay());
        y.set("z", obra.az());
        y.set("frente", obra.frente().name());
        y.set("fase", obra.fase.name());
        // Un poco para atras: las piezas en el aire al apagar se vuelven a poner (las que ya estan se saltean).
        y.set("cursor", obra.fase == ObraGranSede.Fase.CONSTRUCCION ? Math.max(0, obra.cursor - 400) : obra.cursor);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar obra.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try {
            obra = new ObraGranSede(y.getString("world"), y.getInt("x"), y.getInt("y"), y.getInt("z"),
                    BlockFace.valueOf(y.getString("frente", "NORTH")));
            obra.fase = ObraGranSede.Fase.valueOf(y.getString("fase", "DESPEJE"));
            obra.cursor = y.getInt("cursor");
            plugin.getLogger().info("Se retoma la obra de la Gran Sede (" + obra.fase + ").");
        } catch (IllegalArgumentException | NullPointerException e) {
            plugin.getLogger().warning("obra.yml invalido, se ignora: " + e.getMessage());
            obra = null;
        }
    }

    /** Llamar despues de cargar los mundos (onEnable): pone los tickets de una obra retomada. */
    public void retomar() {
        if (obra != null) tickets(true);
    }
}
