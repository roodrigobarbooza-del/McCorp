package com.isjbar.minercorp.gransede.tienda;

import org.bukkit.entity.Villager;

/** Una tienda del config: su titulo, el nombre del vendedor y su oficio (solo cambia la ropa del aldeano). */
public record Tienda(String id, String titulo, String vendedor, Villager.Profession profesion) {
}
