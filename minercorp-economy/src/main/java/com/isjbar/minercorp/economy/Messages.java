package com.isjbar.minercorp.economy;

import com.isjbar.minercorp.economy.api.AccountInfo;
import com.isjbar.minercorp.economy.api.EconomyAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/** Textos configurables (MiniMessage) y utilidades para mostrar montos y nombres. */
final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final Money money;
    private final EconomyAPI economy;

    Messages(Plugin plugin, Money money, EconomyAPI economy) {
        this.plugin = plugin;
        this.money = money;
        this.economy = economy;
    }

    /** Texto de la clave {@code mensajes.<key>} con los placeholders dados, sin prefijo. */
    Component raw(String key, TagResolver... placeholders) {
        String template = plugin.getConfig().getString("mensajes." + key, key);
        return MM.deserialize(template, TagResolver.resolver(placeholders));
    }

    /** Manda el mensaje con el prefijo del plugin. */
    void send(Audience to, String key, TagResolver... placeholders) {
        to.sendMessage(prefix().append(raw(key, placeholders)));
    }

    Component prefix() {
        return MM.deserialize(plugin.getConfig().getString("mensajes.prefijo", ""));
    }

    static TagResolver text(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value);
    }

    String money(double amount) {
        return economy.format(amount);
    }

    String money(long cents) {
        return money.format(cents);
    }

    /** Nombre para mostrar de una cuenta: jugador, empresa, "Servidor" o "Cuenta desconocida". */
    String accountName(UUID id) {
        if (id == null) return "Varios";
        if (id.equals(EconomyAPI.SERVER)) return "Servidor";
        Optional<AccountInfo> info = economy.getAccount(id);
        if (info.isPresent() && info.get().displayName() != null) return info.get().displayName();
        String jugador = Bukkit.getOfflinePlayer(id).getName();
        return jugador != null ? jugador : "Cuenta desconocida";
    }

    Money moneyFormat() {
        return money;
    }

    /** "02/10 14:33" */
    static String date(long epochMillis) {
        return FECHA.format(Instant.ofEpochMilli(epochMillis));
    }

    /** "recien", "hace 5 min", "hace 3 h", "hace 2 d" */
    static String ago(long epochMillis) {
        long seg = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000);
        if (seg < 60) return "recien";
        if (seg < 3600) return "hace " + seg / 60 + " min";
        if (seg < 86400) return "hace " + seg / 3600 + " h";
        return "hace " + seg / 86400 + " d";
    }
}
