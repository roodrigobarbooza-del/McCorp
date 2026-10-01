package com.isjbar.minercorp.territory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Un chunk reclamado: quien lo posee, quien puede romper bloques ahi, y su veta. */
class Claim {

    private final UUID ownerId;
    private String ownerLabel;
    private String purpose;
    private final Set<UUID> authorizedUsers = new HashSet<>();
    private final VeinState vein;

    Claim(UUID ownerId, String ownerLabel, String purpose, VeinState vein) {
        this.ownerId = ownerId;
        this.ownerLabel = ownerLabel;
        this.purpose = purpose;
        this.vein = vein;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getOwnerLabel() {
        return ownerLabel;
    }

    void setOwnerLabel(String ownerLabel) {
        this.ownerLabel = ownerLabel;
    }

    String getPurpose() {
        return purpose;
    }

    Set<UUID> getAuthorizedUsers() {
        return authorizedUsers;
    }

    /**
     * Un jugador esta autorizado si esta en el set de autorizados (que Mining
     * mantiene sincronizado con dueno + colaboradores de la empresa). No se
     * compara contra ownerId directamente porque ownerId es un identificador
     * generico (ej: el UUID de una empresa) que no tiene por que coincidir
     * con el UUID de ningun jugador.
     */
    boolean isAuthorized(UUID playerId) {
        return authorizedUsers.contains(playerId);
    }

    VeinState getVein() {
        return vein;
    }
}
