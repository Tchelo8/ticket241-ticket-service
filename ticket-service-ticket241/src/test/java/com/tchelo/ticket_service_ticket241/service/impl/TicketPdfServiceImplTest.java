package com.tchelo.ticket_service_ticket241.service.impl;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.tchelo.ticket_service_ticket241.entity.Ticket;
import com.tchelo.ticket_service_ticket241.entity.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPdfServiceImplTest {

    private final TicketPdfServiceImpl pdfService = new TicketPdfServiceImpl(new QRCodeServiceImpl());

    private Ticket ticket(String number, String firstName) {
        Ticket ticket = Ticket.builder()
                .ticketNumber(number)
                .qrCode("3f2b1c4d-1111-2222-3333-444455556666|" + number + "|jeton")
                .eventName("Festival des Musiques Urbaines")
                .eventStartDate(LocalDateTime.of(2026, 12, 12, 20, 0))
                .eventVenueName("Stade de l'Amitié")
                .eventVenueCityName("Libreville")
                .ticketCategoryName("VIP")
                .userId("+24100000001")
                .userFirstName(firstName)
                .userLastName("Mba")
                .finalPrice(new BigDecimal("12500"))
                .currency("XAF")
                .orderId("a1b2c3d4-0000-0000-0000-000000000000")
                .status(TicketStatus.PAID)
                .build();
        return ticket;
    }

    @Test
    void generatesOnePagePerTicketWithItsDetails() throws Exception {
        byte[] pdf = pdfService.generateTicketsPdf(List.of(
                ticket("TKT-2026-AAAAAAAAAA", "Awa"),
                ticket("TKT-2026-BBBBBBBBBB", "Junior")));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(2);

            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            String firstPage = extractor.getTextFromPage(1);
            String secondPage = extractor.getTextFromPage(2);

            assertThat(firstPage)
                    .contains("Festival des Musiques Urbaines")
                    .contains("Samedi 12 décembre 2026 à 20h00")
                    .contains("TKT-2026-AAAAAAAAAA")
                    .contains("Awa Mba")
                    .contains("12 500 XAF");
            assertThat(secondPage).contains("TKT-2026-BBBBBBBBBB").contains("Junior Mba");
        } finally {
            reader.close();
        }
    }
}
