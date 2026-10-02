package com.isjbar.minercorp.mining.sede;

import com.isjbar.minercorp.mining.MiningPlugin;
import com.isjbar.minercorp.mining.company.Company;
import com.isjbar.minercorp.territory.api.TerritoryAPI;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * La "Obra de la Sede": lo que pasa despues del primer reclamo de una
 * empresa. El jugador elige el sitio con un Plano de obra, limpia la
 * parcela, entrega materiales por etapas en el cofre de obra, y el edificio
 * se levanta solo, bloque por bloque, con una animacion. Al terminar la sede
 * queda inaugurada y se habilitan minions y taladros.
 */
public class ObraManager {

    private final MiningPlugin plugin;
    private final TerritoryAPI territory;
    private final SedeBlueprint plano;
    private final File file;
    private final Map<UUID, Obra> obras = new LinkedHashMap<>();
    private final Map<UUID, BossBar> barras = new HashMap<>();
    private final Map<UUID, Set<UUID>> espectadores = new HashMap<>();
    private final NamespacedKey planoKey;
    private final NamespacedKey fuegosKey;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Random random = new Random();
    private boolean dirty;

    public ObraManager(MiningPlugin plugin, TerritoryAPI territory) {
        this.plugin = plugin;
        this.territory = territory;
        this.file = new File(plugin.getDataFolder(), "obras.yml");
        this.planoKey = new NamespacedKey(plugin, "plano_obra");
        this.fuegosKey = new NamespacedKey(plugin, "fuegos_sede");
        String archivo = plugin.getConfig().getString("sede.archivo-estructura", "estructuras/sede.nbt");
        this.plano = SedeBlueprint.load(new File(plugin.getDataFolder(), archivo), plugin.getLogger());
        load();
    }

    public void start() {
        long porBloque = Math.max(1, plugin.getConfig().getLong("sede.ticks-por-bloque", 2));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickControl, 20, 20));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickConstruccion, porBloque, porBloque));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickVistaPrevia, 5, 5));
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        for (Obra obra : obras.values()) limpiarVisuales(obra, false);
        save();
    }

    // ---------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------

    public SedeBlueprint plano() {
        return plano;
    }

    public Optional<Obra> get(UUID companyId) {
        return Optional.ofNullable(obras.get(companyId));
    }

    /** True si la empresa ya puede usar minions y taladros. */
    public boolean sedeLista(Company company) {
        return company.isSedeInaugurada() || !plugin.getConfig().getBoolean("sede.requerida-para-minions-y-taladros", true);
    }

    public Optional<Obra> obraEn(Block block) {
        for (Obra obra : obras.values()) {
            if (obra.enParcela(plano, block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
                return Optional.of(obra);
            }
        }
        return Optional.empty();
    }

    public Optional<Obra> obraDelCofre(Block block) {
        for (Obra obra : obras.values()) {
            int[] c = obra.posCofre(plano);
            if (obra.world().equals(block.getWorld().getName())
                    && c[0] == block.getX() && c[1] == block.getY() && c[2] == block.getZ()) {
                return Optional.of(obra);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------
    // Plano de obra (item)
    // ---------------------------------------------------------------

    public ItemStack crearPlano(Company company) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Plano de obra: Sede de " + company.getName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    gris("Sostenlo y mira al suelo de tu territorio"),
                    gris("para ver donde quedaria la sede."),
                    gris("El frente (y el cofre) queda hacia ti."),
                    Component.text("Clic derecho para empezar la obra.", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(planoKey, PersistentDataType.STRING, company.getId().toString());
        });
        return item;
    }

    public Optional<UUID> empresaDelPlano(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return Optional.empty();
        String id = item.getItemMeta().getPersistentDataContainer().get(planoKey, PersistentDataType.STRING);
        if (id == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Le da el plano al jugador si la empresa todavia no tiene sede ni obra y el no lo tiene ya. */
    public boolean darPlanoSiCorresponde(Player player, Company company) {
        if (company.isSedeInaugurada() || obras.containsKey(company.getId())) return false;
        for (ItemStack it : player.getInventory().getContents()) {
            if (empresaDelPlano(it).filter(company.getId()::equals).isPresent()) return false;
        }
        player.getInventory().addItem(crearPlano(company)).values()
                .forEach(resto -> player.getWorld().dropItemNaturally(player.getLocation(), resto));
        return true;
    }

    public boolean esFuegoDeSede(Firework firework) {
        return firework.getPersistentDataContainer().has(fuegosKey);
    }

    // ---------------------------------------------------------------
    // Ubicacion
    // ---------------------------------------------------------------

    /** Donde quedaria la obra si el jugador confirma ahora; {@code error} es null si es valida. */
    public record Ubicacion(World world, int ax, int ay, int az, BlockFace frente, String error) {
        public boolean valida() {
            return error == null;
        }
    }

    public Ubicacion ubicacionPara(Player player, UUID companyId) {
        Block target = player.getTargetBlockExact(12, FluidCollisionMode.NEVER);
        if (target == null) return null;
        // Si mira pasto alto o flores, apoyar sobre el bloque solido de abajo.
        for (int i = 0; i < 3 && !target.getType().isSolid(); i++) target = target.getRelative(BlockFace.DOWN);

        BlockFace frente = player.getFacing().getOppositeFace();
        World world = target.getWorld();
        int ax = target.getX(), ay = target.getY(), az = target.getZ();

        String error = null;
        if (ay + plano.alto() + 1 >= world.getMaxHeight() || ay <= world.getMinHeight()) {
            error = "No entra a esa altura.";
        } else if (!todaEnTerritorio(world, companyId, frente, ax, ay, az)) {
            error = "La obra y su cofre tienen que quedar dentro de tu territorio.";
        }
        return new Ubicacion(world, ax, ay, az, frente, error);
    }

    private boolean todaEnTerritorio(World world, UUID companyId, BlockFace frente, int ax, int ay, int az) {
        Set<Long> chunks = new HashSet<>();
        for (int lx = 0; lx < plano.ancho(); lx++) {
            for (int lz = 0; lz < plano.fondo(); lz++) {
                int[] p = Obra.aMundo(plano, frente, ax, ay, az, lx, 0, lz);
                chunks.add(chunkKey(p[0] >> 4, p[2] >> 4));
            }
        }
        int[] c = Obra.posCofre(plano, frente, ax, ay, az);
        chunks.add(chunkKey(c[0] >> 4, c[2] >> 4));
        for (long key : chunks) {
            int cx = (int) (key >> 32), cz = (int) key;
            if (!world.isChunkLoaded(cx, cz)) return false;
            Optional<UUID> owner = territory.getOwner(world.getChunkAt(cx, cz));
            if (owner.isEmpty() || !owner.get().equals(companyId)) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Iniciar / cancelar
    // ---------------------------------------------------------------

    /** Arranca la obra en la ubicacion que esta mirando el jugador. Devuelve un mensaje de error o null si arranco. */
    public String iniciar(Player player, Company company) {
        if (company.isSedeInaugurada()) return "Tu empresa ya tiene su sede inaugurada.";
        if (obras.containsKey(company.getId())) return "Tu empresa ya tiene una obra en curso. Usa /empresa obra para verla.";
        Ubicacion u = ubicacionPara(player, company.getId());
        if (u == null) return "Mira al suelo donde quieres la sede.";
        if (!u.valida()) return u.error();

        Obra obra = new Obra(company.getId(), u.world().getName(), u.ax(), u.ay(), u.az(), u.frente());
        obra.setLimpiezaInicial(contarPendientes(obra));
        obra.setLimpia(obra.limpiezaInicial() == 0);
        obras.put(company.getId(), obra);
        sincronizar(obra);

        colocarCofre(obra);
        colocarAndamios(obra);
        actualizarHolograma(obra);
        dirty = true;
        save();

        World w = u.world();
        Location cofre = centroDe(obra.posCofre(plano), w);
        w.playSound(cofre, Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.2f);
        w.spawnParticle(Particle.CLOUD, cofre, 20, 0.6, 0.4, 0.6, 0.02);
        return null;
    }

    /**
     * Cancela la obra: los materiales entregados que todavia no se usaron se
     * dejan caer junto al cofre, y se quitan holograma, andamios y barra. Lo
     * que ya se construyo queda en pie.
     */
    public void cancelar(UUID companyId) {
        Obra obra = obras.remove(companyId);
        if (obra == null) return;
        World w = obra.mundo();
        if (w != null) {
            Map<Material, Integer> devolver = new EnumMap<>(obra.entregado());
            for (int i = obra.colocados(); i < obra.finPagado; i++) {
                Material m = SedeBlueprint.materialQuePide(plano.piezas().get(i).data());
                if (m != null) devolver.merge(m, 1, Integer::sum);
            }
            Location donde = centroDe(obra.posCofre(plano), w).add(0, 1, 0);
            devolver.forEach((m, cant) -> {
                while (cant > 0) {
                    int n = Math.min(cant, m.getMaxStackSize());
                    w.dropItemNaturally(donde, new ItemStack(m, n));
                    cant -= n;
                }
            });
        }
        limpiarVisuales(obra, true);
        dirty = true;
        save();
    }

    /** Si se libera un chunk que toca la obra, la obra no puede seguir. */
    public void alLiberarChunk(UUID companyId, Chunk chunk) {
        Obra obra = obras.get(companyId);
        if (obra == null || !obra.world().equals(chunk.getWorld().getName())) return;
        for (int lx = 0; lx < plano.ancho(); lx++) {
            for (int lz = 0; lz < plano.fondo(); lz++) {
                int[] p = obra.aMundo(plano, lx, 0, lz);
                if (p[0] >> 4 == chunk.getX() && p[2] >> 4 == chunk.getZ()) {
                    cancelar(companyId);
                    return;
                }
            }
        }
        int[] c = obra.posCofre(plano);
        if (c[0] >> 4 == chunk.getX() && c[2] >> 4 == chunk.getZ()) cancelar(companyId);
    }

    // ---------------------------------------------------------------
    // Ticks
    // ---------------------------------------------------------------

    /** Cada segundo: limpieza, cofre, holograma y barra. */
    private void tickControl() {
        for (Obra obra : new ArrayList<>(obras.values())) {
            if (obra.cargada()) {
                colocarCofre(obra);
                absorberCofre(obra);
                if (!obra.limpia()) {
                    obra.pendientesLimpieza = contarPendientes(obra);
                    if (obra.pendientesLimpieza > obra.limpiezaInicial()) obra.setLimpiezaInicial(obra.pendientesLimpieza);
                    if (obra.pendientesLimpieza == 0) {
                        obra.setLimpia(true);
                        dirty = true;
                        avisar(obra, Component.text("Parcela limpia. ", NamedTextColor.GREEN).append(
                                obra.etapasPagadas() > 0
                                        ? Component.text("Arranca la construccion.", NamedTextColor.GRAY)
                                        : Component.text("Ahora deja los materiales de la fundacion en el cofre de obra.", NamedTextColor.GRAY)),
                                Sound.ENTITY_PLAYER_LEVELUP);
                    }
                }
                actualizarHolograma(obra);
            }
            actualizarBarra(obra);
        }
        if (dirty) save();
    }

    /** Coloca el siguiente bloque de cada obra que tenga etapas pagadas por construir. */
    private void tickConstruccion() {
        for (Obra obra : new ArrayList<>(obras.values())) {
            boolean hayQueConstruir = obra.limpia() && obra.cursor < obra.finPagado;
            if (!hayQueConstruir) {
                if (obra.conTicket && obra.enVuelo == 0) tickets(obra, false);
                continue;
            }
            if (!obra.conTicket) {
                // Solo arranca con el chunk ya cargado (hay alguien cerca); el ticket evita que se
                // descargue a mitad de la animacion si el jugador se va o se desconecta.
                if (!obra.cargada()) continue;
                tickets(obra, true);
            }
            colocarSiguiente(obra);
        }
    }

    private void colocarSiguiente(Obra obra) {
        int indice = obra.cursor++;
        SedeBlueprint.Pieza pieza = plano.piezas().get(indice);
        int[] pos = obra.aMundo(plano, pieza.x(), pieza.y(), pieza.z());
        BlockData data = obra.dataRotada(pieza.data());
        Block block = obra.bloque(pos);
        if (block == null) return;
        if (block.getBlockData().matches(data)) {
            // Ya estaba (por ejemplo, al retomar despues de un reinicio).
            terminarPieza(obra);
            return;
        }

        World w = block.getWorld();
        obra.enVuelo++;
        BlockDisplay fantasma = w.spawn(block.getLocation(), BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(data);
            d.setTransformation(new Transformation(new Vector3f(0.2f, 0.9f, 0.2f), new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
        // Un tick despues (el cliente ya conoce la posicion inicial) baja y crece hasta su lugar.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!fantasma.isValid()) return;
            fantasma.setInterpolationDelay(0);
            fantasma.setInterpolationDuration(5);
            fantasma.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
        }, 2);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            fantasma.remove();
            obra.enVuelo--;
            if (obras.get(obra.companyId()) != obra) return; // se cancelo mientras tanto
            if (pieza.y() == 0) rellenarDebajo(block);
            block.setBlockData(data, false);
            Location c = block.getLocation().add(0.5, 0.5, 0.5);
            w.playSound(c, data.getSoundGroup().getPlaceSound(), 0.8f, 0.85f + random.nextFloat() * 0.3f);
            w.spawnParticle(Particle.BLOCK, c, 8, 0.3, 0.3, 0.3, 0, data);
            terminarPieza(obra);
        }, 7);
    }

    /** La fundacion tapa los pozos que haya debajo con piedra, hasta 8 bloques. */
    private void rellenarDebajo(Block block) {
        Block b = block.getRelative(BlockFace.DOWN);
        for (int i = 0; i < 8 && b.getY() > b.getWorld().getMinHeight(); i++) {
            if (b.getType().isSolid()) break;
            b.setType(Material.COBBLESTONE, false);
            b = b.getRelative(BlockFace.DOWN);
        }
    }

    private void terminarPieza(Obra obra) {
        obra.setColocados(obra.colocados() + 1);
        dirty = true;
        int hechos = obra.colocados();
        if (hechos >= plano.piezas().size()) {
            inaugurar(obra);
            return;
        }
        for (int e = 0; e < plano.etapas(); e++) {
            if (plano.finDeEtapa(e) == hechos && (e == 0 || plano.finDeEtapa(e - 1) < hechos)) {
                avisar(obra, Component.text("Etapa terminada: " + SedeBlueprint.NOMBRES_ETAPAS[e] + ".", NamedTextColor.GREEN),
                        Sound.BLOCK_NOTE_BLOCK_BELL);
            }
        }
    }

    // ---------------------------------------------------------------
    // Limpieza
    // ---------------------------------------------------------------

    /**
     * Cuenta lo que falta despejar: todo lo que no sea aire ni liquido
     * encima del nivel del suelo, dentro de la parcela. Son unos pocos
     * cientos de bloques ya cargados, asi que se cuenta directo.
     */
    private int contarPendientes(Obra obra) {
        World w = obra.mundo();
        if (w == null) return 0;
        int pendientes = 0;
        for (int lx = 0; lx < plano.ancho(); lx++) {
            for (int lz = 0; lz < plano.fondo(); lz++) {
                for (int ly = 1; ly <= plano.alto(); ly++) {
                    int[] p = obra.aMundo(plano, lx, ly, lz);
                    Block b = w.getBlockAt(p[0], p[1], p[2]);
                    if (!b.getType().isAir() && !b.isLiquid()) pendientes++;
                }
            }
        }
        return pendientes;
    }

    public int porcentajeLimpieza(Obra obra) {
        if (obra.limpia() || obra.limpiezaInicial() <= 0) return 100;
        double hecho = 1.0 - (double) obra.pendientesLimpieza / obra.limpiezaInicial();
        return (int) Math.floor(Math.max(0, Math.min(1, hecho)) * 100);
    }

    // ---------------------------------------------------------------
    // Cofre y materiales
    // ---------------------------------------------------------------

    private void colocarCofre(Obra obra) {
        Block block = obra.bloque(obra.posCofre(plano));
        if (block == null || block.getType() == Material.CHEST) return;
        Directional data = (Directional) Material.CHEST.createBlockData();
        data.setFacing(obra.frente());
        block.setBlockData(data, false);
    }

    /** Toma del cofre lo que necesita la etapa que se esta pagando; si la completa, sigue con la proxima. */
    private void absorberCofre(Obra obra) {
        Block block = obra.bloque(obra.posCofre(plano));
        if (block == null || !(block.getState(false) instanceof Chest chest)) return;
        Inventory inv = chest.getBlockInventory();

        boolean cambio = false;
        while (obra.etapasPagadas() < plano.etapas()) {
            Map<Material, Integer> pide = plano.materiales(obra.etapasPagadas());
            boolean completa = true;
            for (Map.Entry<Material, Integer> e : pide.entrySet()) {
                int falta = e.getValue() - obra.entregado().getOrDefault(e.getKey(), 0);
                if (falta <= 0) continue;
                int tomado = tomar(inv, e.getKey(), falta);
                if (tomado > 0) {
                    obra.entregado().merge(e.getKey(), tomado, Integer::sum);
                    cambio = true;
                }
                if (tomado < falta) completa = false;
            }
            if (!completa) break;

            String etapa = SedeBlueprint.NOMBRES_ETAPAS[obra.etapasPagadas()];
            obra.setEtapasPagadas(obra.etapasPagadas() + 1);
            obra.entregado().clear();
            cambio = true;
            sincronizar(obra);
            avisar(obra, Component.text("Materiales completos para la etapa " + etapa + ". ", NamedTextColor.GREEN)
                    .append(Component.text(obra.limpia() ? "Construyendo..." : "Termina de limpiar la parcela para que arranque.",
                            NamedTextColor.GRAY)), Sound.ENTITY_VILLAGER_WORK_MASON);
        }
        if (cambio) {
            dirty = true;
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_BARREL_CLOSE, 0.6f, 1.3f);
        }
    }

    private int tomar(Inventory inv, Material material, int maximo) {
        int tomado = 0;
        for (int i = 0; i < inv.getSize() && tomado < maximo; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != material) continue;
            int n = Math.min(maximo - tomado, it.getAmount());
            it.setAmount(it.getAmount() - n);
            inv.setItem(i, it.getAmount() <= 0 ? null : it);
            tomado += n;
        }
        return tomado;
    }

    /** Se llama cuando un jugador cierra el cofre de obra, para no esperar al proximo segundo. */
    public void alCerrarCofre(Obra obra) {
        absorberCofre(obra);
        actualizarHolograma(obra);
        if (dirty) save();
    }

    // ---------------------------------------------------------------
    // Inauguracion
    // ---------------------------------------------------------------

    private void inaugurar(Obra obra) {
        obras.remove(obra.companyId());
        limpiarVisuales(obra, true);
        Optional<Company> company = plugin.companies().getById(obra.companyId());
        company.ifPresent(c -> {
            c.setSedeInaugurada(true);
            plugin.companies().save();
        });
        String nombre = company.map(Company::getName).orElse("tu empresa");

        World w = obra.mundo();
        Location techo = obra.centro(plano).add(0, plano.alto(), 0);
        if (w != null) {
            for (int i = 0; i < 3; i++) {
                int demora = i * 8;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> fuego(techo.clone().add(
                        random.nextDouble() * 4 - 2, random.nextDouble() * 2, random.nextDouble() * 4 - 2)), demora);
            }
        }
        Title titulo = Title.title(
                Component.text("Sede inaugurada", NamedTextColor.GOLD),
                Component.text(nombre + " ya puede contratar minions y comprar taladros", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800)));
        for (Player p : miembrosConectados(obra)) {
            p.showTitle(titulo);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        dirty = true;
        save();
    }

    private void fuego(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class, f -> {
            f.getPersistentDataContainer().set(fuegosKey, PersistentDataType.BYTE, (byte) 1);
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.ORANGE, Color.YELLOW)
                    .withFade(Color.WHITE)
                    .trail(true)
                    .build());
            f.setFireworkMeta(meta);
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, fw::detonate, 1);
    }

    // ---------------------------------------------------------------
    // Visuales: andamios, holograma, barra
    // ---------------------------------------------------------------

    private void colocarAndamios(Obra obra) {
        int[][] esquinas = {{-1, -1}, {plano.ancho(), -1}, {-1, plano.fondo()}, {plano.ancho(), plano.fondo()}};
        BlockData andamio = Material.SCAFFOLDING.createBlockData();
        for (int[] e : esquinas) {
            for (int ly = 1; ly <= 3; ly++) {
                int[] p = obra.aMundo(plano, e[0], ly, e[1]);
                Block b = obra.bloque(p);
                if (b == null || !b.getType().isAir()) break;
                Optional<UUID> owner = territory.getOwner(b.getChunk());
                if (owner.isEmpty() || !owner.get().equals(obra.companyId())) break;
                b.setBlockData(andamio, false);
                obra.andamios().add(p);
            }
        }
    }

    private void actualizarHolograma(Obra obra) {
        World w = obra.mundo();
        if (w == null) return;
        if (obra.holograma == null || !obra.holograma.isValid()) {
            Location loc = centroDe(obra.posCofre(plano), w).add(0, 1.1, 0);
            obra.holograma = w.spawn(loc, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
            });
        }
        obra.holograma.text(textoHolograma(obra));
    }

    private Component textoHolograma(Obra obra) {
        String nombre = plugin.companies().getById(obra.companyId()).map(Company::getName).orElse("?");
        Component t = Component.text("Obra: Sede de " + nombre, NamedTextColor.GOLD);
        if (!obra.limpia()) {
            t = t.appendNewline().append(Component.text("Limpia la parcela: " + porcentajeLimpieza(obra) + "%", NamedTextColor.YELLOW));
        }
        if (obra.etapasPagadas() < plano.etapas()) {
            int etapa = obra.etapasPagadas();
            t = t.appendNewline().append(Component.text("Materiales para " + SedeBlueprint.NOMBRES_ETAPAS[etapa]
                    + " (" + (etapa + 1) + "/" + plano.etapas() + "):", NamedTextColor.WHITE));
            for (Map.Entry<Material, Integer> e : plano.materiales(etapa).entrySet()) {
                int tiene = obra.entregado().getOrDefault(e.getKey(), 0);
                boolean ok = tiene >= e.getValue();
                t = t.appendNewline().append(Component.translatable(e.getKey().translationKey(),
                                ok ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                        .append(Component.text(": " + tiene + "/" + e.getValue(), ok ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
        } else {
            t = t.appendNewline().append(Component.text("Materiales completos", NamedTextColor.GREEN));
        }
        return t;
    }

    private void actualizarBarra(Obra obra) {
        BossBar barra = barras.computeIfAbsent(obra.companyId(),
                id -> BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10));
        switch (obra.fase()) {
            case LIMPIEZA -> {
                barra.name(Component.text("Limpieza de la parcela: " + porcentajeLimpieza(obra) + "%", NamedTextColor.YELLOW));
                barra.progress(porcentajeLimpieza(obra) / 100f);
                barra.color(BossBar.Color.YELLOW);
            }
            case MATERIALES -> {
                int etapa = Math.min(obra.etapasPagadas(), plano.etapas() - 1);
                int pide = plano.materiales(etapa).values().stream().mapToInt(Integer::intValue).sum();
                int tiene = obra.entregado().values().stream().mapToInt(Integer::intValue).sum();
                barra.name(Component.text("Esperando materiales: " + SedeBlueprint.NOMBRES_ETAPAS[etapa]
                        + " (" + tiene + "/" + pide + ")", NamedTextColor.AQUA));
                barra.progress(pide == 0 ? 1f : Math.min(1f, (float) tiene / pide));
                barra.color(BossBar.Color.BLUE);
            }
            case CONSTRUYENDO -> {
                int total = plano.piezas().size();
                barra.name(Component.text("Construyendo la sede: " + (obra.colocados() * 100 / total) + "%", NamedTextColor.GREEN));
                barra.progress(Math.min(1f, (float) obra.colocados() / total));
                barra.color(BossBar.Color.GREEN);
            }
        }

        double radio = plugin.getConfig().getDouble("sede.radio-barra", 48);
        Set<UUID> viendo = espectadores.computeIfAbsent(obra.companyId(), id -> new HashSet<>());
        Location centro = obra.mundo() == null ? null : obra.centro(plano);
        Set<UUID> ahora = new HashSet<>();
        for (Player p : miembrosConectados(obra)) {
            if (centro != null && p.getWorld().equals(centro.getWorld()) && p.getLocation().distanceSquared(centro) <= radio * radio) {
                ahora.add(p.getUniqueId());
                p.showBossBar(barra);
            }
        }
        for (UUID id : viendo) {
            if (ahora.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(barra);
        }
        viendo.clear();
        viendo.addAll(ahora);
    }

    private void limpiarVisuales(Obra obra, boolean quitarAndamios) {
        if (obra.holograma != null) obra.holograma.remove();
        obra.holograma = null;
        BossBar barra = barras.remove(obra.companyId());
        Set<UUID> viendo = espectadores.remove(obra.companyId());
        if (barra != null && viendo != null) {
            for (UUID id : viendo) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(barra);
            }
        }
        if (quitarAndamios) {
            for (int[] p : obra.andamios()) {
                Block b = obra.bloque(p);
                if (b != null && b.getType() == Material.SCAFFOLDING) b.setType(Material.AIR, false);
            }
            obra.andamios().clear();
        }
        tickets(obra, false);
    }

    private void tickets(Obra obra, boolean poner) {
        World w = obra.mundo();
        if (w == null || obra.conTicket == poner) return;
        Set<Long> chunks = new HashSet<>();
        for (int lx = 0; lx < plano.ancho(); lx++) {
            for (int lz = 0; lz < plano.fondo(); lz++) {
                int[] p = obra.aMundo(plano, lx, 0, lz);
                chunks.add(chunkKey(p[0] >> 4, p[2] >> 4));
            }
        }
        for (long key : chunks) {
            int cx = (int) (key >> 32), cz = (int) key;
            if (poner) w.addPluginChunkTicket(cx, cz, plugin);
            else w.removePluginChunkTicket(cx, cz, plugin);
        }
        obra.conTicket = poner;
    }

    // ---------------------------------------------------------------
    // Vista previa del plano
    // ---------------------------------------------------------------

    private void tickVistaPrevia() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Optional<UUID> companyId = empresaDelPlano(p.getInventory().getItemInMainHand());
            if (companyId.isEmpty()) continue;
            if (obras.containsKey(companyId.get())) continue;
            Ubicacion u = ubicacionPara(p, companyId.get());
            if (u == null) {
                p.sendActionBar(Component.text("Mira al suelo donde quieres la sede", NamedTextColor.GRAY));
                continue;
            }
            Color color = u.valida() ? Color.fromRGB(0x55FF55) : Color.fromRGB(0xFF5555);
            dibujarContorno(p, u, new Particle.DustOptions(color, 1.0f));
            int[] c = Obra.posCofre(plano, u.frente(), u.ax(), u.ay(), u.az());
            p.spawnParticle(Particle.DUST, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, 4, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(0xFFD24A), 1.2f));
            p.sendActionBar(u.valida()
                    ? Component.text("Clic derecho para empezar la obra aqui", NamedTextColor.GREEN)
                    : Component.text(u.error(), NamedTextColor.RED));
        }
    }

    private void dibujarContorno(Player p, Ubicacion u, Particle.DustOptions polvo) {
        int[] a = Obra.aMundo(plano, u.frente(), u.ax(), u.ay(), u.az(), 0, 0, 0);
        int[] b = Obra.aMundo(plano, u.frente(), u.ax(), u.ay(), u.az(), plano.ancho() - 1, 0, plano.fondo() - 1);
        double x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]) + 1;
        double z0 = Math.min(a[2], b[2]), z1 = Math.max(a[2], b[2]) + 1;
        double y = u.ay() + 1.1;
        for (double x = x0; x <= x1; x += 0.5) {
            p.spawnParticle(Particle.DUST, x, y, z0, 1, polvo);
            p.spawnParticle(Particle.DUST, x, y, z1, 1, polvo);
        }
        for (double z = z0; z <= z1; z += 0.5) {
            p.spawnParticle(Particle.DUST, x0, y, z, 1, polvo);
            p.spawnParticle(Particle.DUST, x1, y, z, 1, polvo);
        }
        // Esquinas en altura, para que se vea el volumen del edificio.
        for (double dy = 0.5; dy <= plano.alto(); dy += 0.5) {
            p.spawnParticle(Particle.DUST, x0, y + dy, z0, 1, polvo);
            p.spawnParticle(Particle.DUST, x1, y + dy, z0, 1, polvo);
            p.spawnParticle(Particle.DUST, x0, y + dy, z1, 1, polvo);
            p.spawnParticle(Particle.DUST, x1, y + dy, z1, 1, polvo);
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void sincronizar(Obra obra) {
        obra.finPagado = plano.finDeEtapa(obra.etapasPagadas() - 1);
        if (obra.cursor < obra.colocados()) obra.cursor = obra.colocados();
    }

    private List<Player> miembrosConectados(Obra obra) {
        Optional<Company> company = plugin.companies().getById(obra.companyId());
        if (company.isEmpty()) return List.of();
        List<Player> out = new ArrayList<>();
        for (UUID id : company.get().allMemberIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private void avisar(Obra obra, Component mensaje, Sound sonido) {
        for (Player p : miembrosConectados(obra)) {
            p.sendMessage(mensaje);
            p.playSound(p.getLocation(), sonido, 0.8f, 1f);
        }
    }

    private static Location centroDe(int[] pos, World w) {
        return new Location(w, pos[0] + 0.5, pos[1] + 0.5, pos[2] + 0.5);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static Component gris(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    // ---------------------------------------------------------------
    // Persistencia
    // ---------------------------------------------------------------

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("obras");
        for (Obra o : obras.values()) {
            ConfigurationSection s = root.createSection(o.companyId().toString());
            s.set("world", o.world());
            s.set("x", o.ax());
            s.set("y", o.ay());
            s.set("z", o.az());
            s.set("frente", o.frente().name());
            s.set("limpieza-inicial", o.limpiezaInicial());
            s.set("limpia", o.limpia());
            s.set("etapas-pagadas", o.etapasPagadas());
            s.set("colocados", o.colocados());
            ConfigurationSection ent = s.createSection("entregado");
            o.entregado().forEach((m, n) -> ent.set(m.name(), n));
            s.set("andamios", o.andamios().stream().map(p -> p[0] + "," + p[1] + "," + p[2]).toList());
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar obras.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("obras");
        if (root == null) return;
        for (String idStr : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(idStr);
            if (s == null) continue;
            try {
                Obra o = new Obra(UUID.fromString(idStr), s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"),
                        BlockFace.valueOf(s.getString("frente", "NORTH")));
                o.setLimpiezaInicial(s.getInt("limpieza-inicial"));
                o.setLimpia(s.getBoolean("limpia"));
                o.setEtapasPagadas(s.getInt("etapas-pagadas"));
                o.setColocados(Math.min(s.getInt("colocados"), plano.piezas().size()));
                o.pendientesLimpieza = o.limpiezaInicial();
                ConfigurationSection ent = s.getConfigurationSection("entregado");
                if (ent != null) {
                    for (String mat : ent.getKeys(false)) {
                        Material m = Material.matchMaterial(mat);
                        if (m != null) o.entregado().put(m, ent.getInt(mat));
                    }
                }
                for (String p : s.getStringList("andamios")) {
                    String[] parts = p.split(",");
                    o.andamios().add(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
                }
                sincronizar(o);
                obras.put(o.companyId(), o);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Obra invalida en obras.yml (" + idStr + "): " + e.getMessage());
            }
        }
    }
}
