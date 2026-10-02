package com.isjbar.minercorp.gransede.zona;

public enum ZonaTipo {
    /** Protegida: nadie rompe ni pone bloques salvo admins. */
    SEDE,
    /** Mina publica: se pican los bloques de trabajos.mina y se regeneran. */
    MINA,
    /** Bosque publico: se talan los troncos de trabajos.bosque y se regeneran. */
    BOSQUE
}
