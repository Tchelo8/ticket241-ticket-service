package com.tchelo.ticket_service_ticket241.service;

public interface QRCodeService {

    // Contenu du QR code d'un billet : "uuid|numéro|jeton". Le jeton aléatoire rend le contenu
    // impossible à deviner à partir de l'UUID et du numéro, et change à chaque transfert pour que
    // l'ancien QR code ne passe plus à l'entrée.
    String buildQrPayload(String ticketUuid, String ticketNumber, String validationToken);

    // Nouveau jeton de validation aléatoire
    String newValidationToken();

    // Image PNG du QR code encodée en Base64 (data URI), pour un contenu donné
    String generateQRCodeImage(String payload);

    // Génère un QR Code avec dimensions personnalisées
    String generateQRCodeWithSize(String data, int width, int height);

    // Image PNG brute du QR code (pour l'intégrer dans le PDF du billet)
    byte[] generateQRCodePng(String data, int width, int height);

    // Vérifie le format d'un QR code scanné (nouveau format "uuid|numéro|jeton" ou ancien
    // "uuid|numéro|nom de l'événement"). Ne dit rien de la validité du billet lui-même.
    boolean validateQRCode(String qrCodeData);
}
