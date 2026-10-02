package com.isjbar.minercorp.gransede.obra;

/**
 * Un punto del plano que no es un bloque, en coordenadas locales: donde
 * aparece el jugador, donde va cada vendedor, y las esquinas de la mina y
 * el bosque.
 *
 * @param tipo  "spawn", "npc", "mina" o "bosque"
 * @param valor id de la tienda para "npc"; vacio para el resto
 */
public record Marcador(String tipo, String valor, int x, int y, int z) {
}
