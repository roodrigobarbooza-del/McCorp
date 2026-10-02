package com.isjbar.minercorp.resources.resource;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * Un recurso configurado en recursos.&lt;id&gt; del config.yml. {@code model} es
 * el id del modelo del resource pack (null = se ve como {@code material}).
 */
public record ResourceType(String id, String name, Material material, String model, TextColor color,
                           double price, List<String> description) {
}
