package com.tchelo.ticket_service_ticket241.service.impl;



import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.tchelo.ticket_service_ticket241.service.QRCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class QRCodeServiceImpl implements QRCodeService {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String buildQrPayload(String ticketUuid, String ticketNumber, String validationToken) {
        return String.format("%s|%s|%s", ticketUuid, ticketNumber, validationToken);
    }

    @Override
    public String newValidationToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        // URL-safe et sans "|" : ne peut pas casser le découpage du contenu du QR code
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String generateQRCodeImage(String payload) {
        return generateQRCodeWithSize(payload, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    public String generateQRCodeWithSize(String data, int width, int height) {
        // Encodage en Base64
        String base64Image = Base64.getEncoder().encodeToString(generateQRCodePng(data, width, height));

        return "data:image/png;base64," + base64Image;
    }

    @Override
    public byte[] generateQRCodePng(String data, int width, int height) {
        try {
            // Configuration du QR Code
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            // Génération de la matrice
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height, hints);

            // Conversion en image PNG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return outputStream.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("Erreur lors de la génération du QR Code : {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la génération du QR Code", e);
        }
    }

    @Override
    public boolean validateQRCode(String qrCodeData) {
        if (qrCodeData == null || qrCodeData.isEmpty()) {
            return false;
        }

        // "uuid|numéro|xxx" ; la limite à 3 morceaux tolère un "|" dans le nom d'événement des anciens QR codes
        String[] parts = qrCodeData.split("\\|", 3);
        if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
            log.warn("Format QR Code invalide");
            return false;
        }

        // Vérifier que l'UUID est valide
        try {
            java.util.UUID.fromString(parts[0]);
            return true;
        } catch (IllegalArgumentException e) {
            log.warn("UUID invalide dans le QR Code : {}", parts[0]);
            return false;
        }
    }
}
