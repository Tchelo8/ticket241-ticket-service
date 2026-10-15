package com.tchelo.ticket_service_ticket241.service.impl;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.service.QRCodeService;
import com.tchelo.ticket_service_ticket241.service.TicketPdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Une page A4 par billet, aux couleurs des emails Ticket241. Le QR code imprimé est le contenu exact
// attendu par /api/tickets/validate : le PDF suffit pour entrer, imprimé ou affiché sur téléphone.
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketPdfServiceImpl implements TicketPdfService {

    private static final Color BRAND = new Color(0x66, 0x7E, 0xEA);
    private static final Color TEXT_DARK = new Color(0x33, 0x33, 0x33);
    private static final Color TEXT_MUTED = new Color(0x66, 0x66, 0x66);
    private static final Color PANEL = new Color(0xF8, 0xF9, 0xFA);
    private static final Color WARNING_BG = new Color(0xFF, 0xF3, 0xCD);
    private static final Color WARNING_TEXT = new Color(0x85, 0x64, 0x04);

    private static final Font HEADER_TITLE = new Font(Font.HELVETICA, 24, Font.BOLD, Color.WHITE);
    private static final Font HEADER_SUBTITLE = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.WHITE);
    private static final Font EVENT_NAME = new Font(Font.HELVETICA, 20, Font.BOLD, TEXT_DARK);
    private static final Font EVENT_INFO = new Font(Font.HELVETICA, 12, Font.NORMAL, TEXT_MUTED);
    private static final Font LABEL = new Font(Font.HELVETICA, 11, Font.BOLD, TEXT_MUTED);
    private static final Font VALUE = new Font(Font.HELVETICA, 11, Font.NORMAL, TEXT_DARK);
    private static final Font TICKET_NUMBER = new Font(Font.COURIER, 13, Font.BOLD, TEXT_DARK);
    private static final Font WARNING = new Font(Font.HELVETICA, 10, Font.NORMAL, WARNING_TEXT);
    private static final Font FOOTER = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_MUTED);

    private static final DateTimeFormatter EVENT_DATE =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH);
    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH);

    // QR généré en haute définition puis réduit : reste net à l'impression
    private static final int QR_PIXELS = 600;
    private static final float QR_SIZE_POINTS = 200f;

    private final QRCodeService qrCodeService;

    @Override
    public byte[] generateTicketsPdf(List<Ticket> tickets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("Billets Ticket241");
            document.addCreator("Ticket241");
            document.open();

            for (int i = 0; i < tickets.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                addTicketPage(document, tickets.get(i));
            }

            document.close();
        } catch (DocumentException | IOException e) {
            log.error("Erreur lors de la génération du PDF des billets : {}", e.getMessage());
            throw new IllegalStateException("Erreur lors de la génération du PDF des billets", e);
        }

        log.info("PDF généré pour {} billet(s)", tickets.size());
        return out.toByteArray();
    }

    private void addTicketPage(Document document, Ticket ticket) throws DocumentException, IOException {
        // Bandeau
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        PdfPCell headerCell = new PdfPCell();
        headerCell.setBackgroundColor(BRAND);
        headerCell.setBorder(Rectangle.NO_BORDER);
        headerCell.setPadding(18);
        headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph title = new Paragraph("TICKET241", HEADER_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        Paragraph subtitle = new Paragraph("Billet électronique", HEADER_SUBTITLE);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        headerCell.addElement(title);
        headerCell.addElement(subtitle);
        header.addCell(headerCell);
        document.add(header);

        // Événement
        Paragraph eventName = new Paragraph(ticket.getEventName(), EVENT_NAME);
        eventName.setSpacingBefore(24);
        eventName.setSpacingAfter(6);
        document.add(eventName);
        document.add(new Paragraph(formatEventDate(ticket.getEventStartDate()), EVENT_INFO));
        document.add(new Paragraph(formatVenue(ticket), EVENT_INFO));

        // Détails du billet
        PdfPTable details = new PdfPTable(new float[]{1.2f, 2f});
        details.setWidthPercentage(100);
        details.setSpacingBefore(20);
        addDetailRow(details, "Numéro de billet", ticket.getTicketNumber());
        addDetailRow(details, "Catégorie", ticket.getTicketCategoryName());
        addDetailRow(details, "Titulaire", holderName(ticket));
        addDetailRow(details, "Prix payé", formatPrice(ticket.getFinalPrice(), ticket.getCurrency()));
        addDetailRow(details, "Référence commande", ticket.getOrderId());
        document.add(details);

        // QR code d'entrée
        Image qrCode = Image.getInstance(qrCodeService.generateQRCodePng(ticket.getQrCode(), QR_PIXELS, QR_PIXELS));
        qrCode.scaleAbsolute(QR_SIZE_POINTS, QR_SIZE_POINTS);
        qrCode.setAlignment(Element.ALIGN_CENTER);
        qrCode.setSpacingBefore(24);
        document.add(qrCode);

        Paragraph number = new Paragraph(ticket.getTicketNumber(), TICKET_NUMBER);
        number.setAlignment(Element.ALIGN_CENTER);
        number.setSpacingAfter(20);
        document.add(number);

        // Consignes
        PdfPTable warning = new PdfPTable(1);
        warning.setWidthPercentage(100);
        PdfPCell warningCell = new PdfPCell(new Phrase(
                "Présentez ce QR code à l'entrée, sur votre téléphone ou imprimé. Il est unique et ne peut "
                        + "être scanné qu'une seule fois : ne le partagez pas et ne le publiez pas.", WARNING));
        warningCell.setBackgroundColor(WARNING_BG);
        warningCell.setBorder(Rectangle.LEFT);
        warningCell.setBorderColorLeft(new Color(0xFF, 0xC1, 0x07));
        warningCell.setBorderWidthLeft(4);
        warningCell.setPadding(12);
        warning.addCell(warningCell);
        document.add(warning);

        Paragraph footer = new Paragraph(
                "Billet émis par Ticket241 - généré le " + LocalDateTime.now().format(GENERATED_AT), FOOTER);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(16);
        document.add(footer);
    }

    private void addDetailRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL));
        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "-", VALUE));
        for (PdfPCell cell : List.of(labelCell, valueCell)) {
            cell.setBackgroundColor(PANEL);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(new Color(0xE0, 0xE0, 0xE0));
            cell.setPaddingTop(9);
            cell.setPaddingBottom(9);
            cell.setPaddingLeft(10);
            table.addCell(cell);
        }
    }

    // Le bénéficiaire (cadeau) prime sur l'acheteur ; à défaut de nom, le téléphone du compte
    private String holderName(Ticket ticket) {
        String beneficiary = joinNames(ticket.getBeneficiaryFirstName(), ticket.getBeneficiaryLastName());
        if (beneficiary != null) {
            return beneficiary;
        }
        String owner = joinNames(ticket.getUserFirstName(), ticket.getUserLastName());
        return owner != null ? owner : ticket.getUserId();
    }

    private String joinNames(String firstName, String lastName) {
        String joined = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        return joined.isEmpty() ? null : joined;
    }

    private String formatEventDate(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        String formatted = date.format(EVENT_DATE);
        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    private String formatVenue(Ticket ticket) {
        String venue = ticket.getEventVenueName() != null ? ticket.getEventVenueName() : "";
        if (ticket.getEventVenueCityName() != null) {
            venue = venue.isEmpty() ? ticket.getEventVenueCityName() : venue + ", " + ticket.getEventVenueCityName();
        }
        return venue;
    }

    private String formatPrice(BigDecimal amount, String currency) {
        if (amount == null) {
            return "-";
        }
        // Le séparateur de milliers français (espace insécable fine) n'existe pas dans les polices PDF de base
        String formatted = NumberFormat.getIntegerInstance(Locale.FRENCH).format(amount)
                .replace(' ', ' ')
                .replace(' ', ' ');
        return formatted + " " + (currency != null ? currency : "XAF");
    }
}
