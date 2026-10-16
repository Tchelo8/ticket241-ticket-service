package com.tchelo.ticket_service_ticket241.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// Identité de l'appelant, telle que la gateway l'a extraite du JWT et transmise en headers
// (X-User-Id, X-User-Roles, X-User-Structure-Id). Rôles comparés exactement.
public record UserContext(String userId, Set<String> roles, Long structureId) {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_STRUCTURE_ADMIN = "ROLE_STRUCTURE_ADMIN";
    public static final String ROLE_AGENT = "ROLE_AGENT";

    public static UserContext of(String userId, String rolesHeader, String structureIdHeader) {
        Set<String> roles = rolesHeader == null ? Set.of() : Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        Long structureId = null;
        if (structureIdHeader != null && !structureIdHeader.isBlank()) {
            try {
                structureId = Long.valueOf(structureIdHeader.trim());
            } catch (NumberFormatException ignored) {
                // header malformé : traité comme absent
            }
        }
        return new UserContext(userId, roles, structureId);
    }

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }

    // Admin Ticket241, ou admin de cette structure : gestion des billets de ses événements
    public boolean canManageStructure(Long id) {
        return isAdmin() || (roles.contains(ROLE_STRUCTURE_ADMIN) && structureId != null && structureId.equals(id));
    }

    // Peut-il scanner des billets en général (avant même de savoir de quel événement il s'agit) ?
    public boolean canScanTickets() {
        return isAdmin() || roles.contains(ROLE_STRUCTURE_ADMIN) || roles.contains(ROLE_AGENT);
    }

    // Peut-il scanner un billet d'un événement de cette structure ?
    public boolean canScanForStructure(Long id) {
        return canManageStructure(id)
                || (roles.contains(ROLE_AGENT) && structureId != null && structureId.equals(id));
    }
}
