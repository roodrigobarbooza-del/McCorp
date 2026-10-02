package com.isjbar.minercorp.resources.resource;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.List;

/** Un recurso configurado en recursos.&lt;id&gt; del config.yml. */
public record ResourceType(String id, String name, Material material, TextColor color,
                           double price, List<String> description) {
}
