package com.tchelo.ticket_service_ticket241.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Jetons des achats invités : générés aléatoirement (256 bits), donnés une seule fois au client,
// stockés uniquement sous forme de hash SHA-256 (une fuite de la base ne donne accès à aucun billet)
public final class GuestTokens {

    public static final String GUEST_USER_PREFIX = "guest:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private GuestTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Code à 6 chiffres envoyé pour retrouver ses billets
    public static String newNumericCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    // Comparaison en temps constant du hash d'un jeton reçu avec le hash stocké
    public static boolean matches(String providedToken, String storedHash) {
        if (providedToken == null || providedToken.isBlank() || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256(providedToken).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    // Même numéro saisi de plusieurs façons ("077 12 34 56", "077-12-34-56", "00241…") → une seule forme
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String compact = phone.replaceAll("[\\s.\\-()]", "");
        return compact.startsWith("00") ? "+" + compact.substring(2) : compact;
    }

    public static String guestUserId(String normalizedPhone) {
        return GUEST_USER_PREFIX + normalizedPhone;
    }
}
