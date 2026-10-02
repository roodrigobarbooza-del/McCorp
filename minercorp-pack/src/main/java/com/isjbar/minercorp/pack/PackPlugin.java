package com.isjbar.minercorp.pack;

import com.isjbar.minercorp.pack.api.Modelos;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MinerCorp-Pack: le manda a cada jugador el resource pack de McCorp al
 * entrar. En modo "propio" el pack viaja dentro de este jar (lo arma Maven
 * desde la carpeta resourcepack/ del repo) y lo sirve un mini servidor web;
 * en modo "url" se baja de una direccion externa. Los otros plugins le ponen
 * modelos a sus items con {@link Modelos}.
 */
public class PackPlugin extends JavaPlugin implements Listener, TabExecutor {

    /** Siempre el mismo id: un pack nuevo reemplaza al viejo en el cliente. */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("mccorp-pack".getBytes(StandardCharsets.UTF_8));

    private enum Modo { PROPIO, URL, APAGADO }

    private Modo modo = Modo.APAGADO;
    private PackServer server;
    /** Modo url: la direccion fija. Modo propio: null (se arma por jugador). */
    private volatile URI url;
    private volatile String sha1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        cargar();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("pack").setExecutor(this);
        getCommand("pack").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        Modelos.configurar(false, getLogger());
        if (server != null) server.parar();
        server = null;
    }

    /** Lee el config y prepara el pack. Se puede llamar de nuevo para recargar. */
    private void cargar() {
        reloadConfig();
        String valor = getConfig().getString("modo", "propio").trim().toUpperCase(Locale.ROOT);
        try {
            modo = Modo.valueOf(valor);
        } catch (IllegalArgumentException e) {
            getLogger().warning("modo '" + valor + "' no existe (propio, url o apagado). Uso propio.");
            modo = Modo.PROPIO;
        }
        sha1 = null;
        url = null;
        if (modo != Modo.PROPIO && server != null) {
            server.parar();
            server = null;
        }
        switch (modo) {
            case PROPIO -> prepararPropio();
            case URL -> prepararUrl();
            case APAGADO -> getLogger().info("Pack apagado: los items se ven como vanilla.");
        }
        Modelos.configurar(modo != Modo.APAGADO, getLogger());
    }

    private void prepararPropio() {
        byte[] zip;
        try {
            zip = leerZip();
        } catch (IOException e) {
            getLogger().severe("No pude leer el pack: " + e.getMessage() + ". Queda apagado.");
            modo = Modo.APAGADO;
            return;
        }
        sha1 = sha1(zip);
        int puerto = getConfig().getInt("propio.puerto", 8164);
        try {
            if (server != null && server.puerto() != puerto) {
                server.parar();
                server = null;
            }
            if (server == null) server = new PackServer(puerto, zip);
            else server.actualizar(zip);
        } catch (IOException e) {
            getLogger().severe("No pude abrir el puerto " + puerto + " para servir el pack (" + e.getMessage()
                    + "). Cambia propio.puerto o usa modo url. Queda apagado.");
            modo = Modo.APAGADO;
            return;
        }
        getLogger().info("Sirviendo el pack (" + zip.length / 1024 + " KB, sha1 " + sha1 + ") en el puerto " + puerto + ".");
    }

    /** El zip de "propio.archivo" si hay uno, si no el que viene adentro del jar. */
    private byte[] leerZip() throws IOException {
        String archivo = getConfig().getString("propio.archivo", "").trim();
        if (!archivo.isEmpty()) {
            File file = new File(getDataFolder(), archivo);
            getLogger().info("Usando el pack de " + file.getPath());
            return Files.readAllBytes(file.toPath());
        }
        try (InputStream in = getResource("mccorp-pack.zip")) {
            if (in == null) throw new IOException("el jar no trae mccorp-pack.zip");
            return in.readAllBytes();
        }
    }

    private void prepararUrl() {
        String direccion = getConfig().getString("url.direccion", "").trim();
        URI uri;
        try {
            uri = URI.create(direccion);
            if (uri.getScheme() == null || !uri.getScheme().startsWith("http")) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            getLogger().severe("url.direccion no es un link http(s) valido. Queda apagado.");
            modo = Modo.APAGADO;
            return;
        }
        url = uri;
        String fijo = getConfig().getString("url.sha1", "").trim().toLowerCase(Locale.ROOT);
        if (fijo.matches("[0-9a-f]{40}")) {
            sha1 = fijo;
            getLogger().info("Pack desde " + uri + " (sha1 " + sha1 + ").");
            return;
        }
        getLogger().info("Bajando el pack de " + uri + " para calcular su sha1...");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (InputStream in = uri.toURL().openStream()) {
                String calculado = sha1(in.readAllBytes());
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!uri.equals(url)) return; // se recargo con otra url mientras bajaba
                    sha1 = calculado;
                    getLogger().info("sha1 del pack: " + calculado + ". Mandandolo a los que ya estan conectados.");
                    Bukkit.getOnlinePlayers().forEach(this::enviar);
                });
            } catch (IOException e) {
                getLogger().severe("No pude bajar el pack de " + uri + ": " + e.getMessage());
            }
        });
    }

    /** Manda el pack a un jugador, si hay uno listo. */
    public boolean enviar(Player player) {
        if (modo == Modo.APAGADO || sha1 == null) return false;
        URI uri = modo == Modo.URL ? url : urlPropia(player);
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1);
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(true)
                .required(getConfig().getBoolean("obligatorio", true))
                .prompt(Component.text(getConfig().getString("mensaje", ""), NamedTextColor.GOLD))
                .build());
        return true;
    }

    /** http://host:puerto/mccorp-pack.zip, con el host por el que entro el jugador. */
    private URI urlPropia(Player player) {
        String host = getConfig().getString("propio.direccion-publica", "").trim();
        if (host.isEmpty()) {
            InetSocketAddress virtual = player.getVirtualHost();
            host = virtual != null ? virtual.getHostString() : "";
        }
        if (host.isEmpty()) host = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]"; // IPv6
        // ?v= cambia con cada pack nuevo para que ningun cache intermedio entregue el viejo
        return URI.create("http://" + host + ":" + server.puerto() + PackServer.RUTA + "?v=" + sha1.substring(0, 8));
    }

    private static String sha1(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ eventos

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        enviar(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL -> {
                getLogger().warning(player.getName() + " no pudo bajar el pack (" + event.getStatus()
                        + "). Si juega desde afuera, revisa que el puerto "
                        + getConfig().getInt("propio.puerto", 8164) + " este abierto.");
                player.sendMessage(Component.text("No se pudo bajar el pack de texturas. Proba /pack o avisale a un admin.",
                        NamedTextColor.RED));
            }
            case FAILED_RELOAD -> getLogger().warning(player.getName() + ": el pack bajo pero no cargo (FAILED_RELOAD).");
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ /pack

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("minercorp.pack.admin");
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (admin && sub.equals("info")) {
            sender.sendMessage(Component.text("Modo: " + modo.name().toLowerCase(Locale.ROOT)
                    + (server != null ? " (puerto " + server.puerto() + ")" : "")
                    + (url != null ? " - " + url : "")
                    + " - sha1: " + (sha1 == null ? "pendiente" : sha1), NamedTextColor.YELLOW));
            return true;
        }
        if (admin && sub.equals("recargar")) {
            cargar();
            Bukkit.getOnlinePlayers().forEach(this::enviar);
            sender.sendMessage(Component.text("Pack recargado y reenviado a todos (modo "
                    + modo.name().toLowerCase(Locale.ROOT) + ").", NamedTextColor.GREEN));
            return true;
        }
        if (admin && sub.equals("reenviar") && args.length > 1) {
            List<Player> destino = new ArrayList<>();
            if (args[1].equalsIgnoreCase("todos")) destino.addAll(Bukkit.getOnlinePlayers());
            else {
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) {
                    sender.sendMessage(Component.text("No esta conectado: " + args[1], NamedTextColor.RED));
                    return true;
                }
                destino.add(p);
            }
            destino.forEach(this::enviar);
            sender.sendMessage(Component.text("Pack reenviado a " + destino.size() + " jugador(es).", NamedTextColor.GREEN));
            return true;
        }
        if (sender instanceof Player player) {
            if (enviar(player)) player.sendMessage(Component.text("Te mando el pack de nuevo.", NamedTextColor.GREEN));
            else player.sendMessage(Component.text("El pack de texturas esta apagado o todavia no esta listo.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("Uso: /pack info | recargar | reenviar <jugador|todos>", NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minercorp.pack.admin")) return List.of();
        if (args.length == 1) {
            return List.of("info", "recargar", "reenviar").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reenviar")) {
            List<String> nombres = new ArrayList<>(List.of("todos"));
            Bukkit.getOnlinePlayers().forEach(p -> nombres.add(p.getName()));
            return nombres.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
