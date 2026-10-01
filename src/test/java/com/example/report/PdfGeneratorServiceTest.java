package com.example.report;

import jakarta.inject.Inject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startet einen echten CDI-Container (Weld SE) – wie im Application Server:
 * {@link ThymeleafConfig} produziert die Engine, {@link PdfGeneratorService} bekommt sie injiziert.
 * <p>
 * Die erzeugten PDFs landen zum Anschauen unter {@code target/pdf/}.
 */
@EnableAutoWeld
@AddBeanClasses(ThymeleafConfig.class)
class PdfGeneratorServiceTest {

    private static final Path OUTPUT_DIR = Path.of("target", "pdf");

    @Inject
    PdfGeneratorService pdfGeneratorService;

    @Test
    void rendersHtmlFromTemplate() {
        String html = pdfGeneratorService.renderInvoiceHtml(sampleOrder());

        assertTrue(html.contains("INV-2026-001"));
        assertTrue(html.contains("01.10.2026"));
        assertTrue(html.contains("Max Mustermann"));
        assertTrue(html.contains("1.020,00 €"), "Betrag im deutschen Format");
        assertFalse(html.contains("Beispielartikel"), "Natural-Template-Platzhalter ersetzt");
        assertFalse(html.matches("(?s).*\\sth:(text|each|remove)=.*"), "keine Thymeleaf-Attribute im Ergebnis");
    }

    @Test
    void generatesInvoicePdf() throws IOException {
        byte[] pdf = pdfGeneratorService.generateInvoice(sampleOrder());
        Path file = write("rechnung.pdf", pdf);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("RECHNUNG"), text);
            assertTrue(text.contains("INV-2026-001"), text);
            assertTrue(text.contains("Empfänger"), text);
            assertTrue(text.contains("Max Mustermann"), text);
            assertTrue(text.contains("Webentwicklung Stundensatz"), text);
            assertTrue(text.contains("Hosting (12 Monate)"), text);
            assertTrue(text.contains("1.020,00 €"), text);
            // Kommt aus @page in invoice.css -> beweist, dass das externe CSS geladen wurde
            assertTrue(text.contains("Seite 1 von 1"), text);
        }
        System.out.println("PDF erzeugt: " + file.toAbsolutePath());
    }

    @Test
    void longInvoiceBreaksIntoMultiplePages() throws IOException {
        List<OrderItemDto> items = IntStream.rangeClosed(1, 60)
                .mapToObj(i -> new OrderItemDto("Position " + i, i, new BigDecimal("9.99")))
                .toList();
        OrderDto order = new OrderDto("INV-2026-002", LocalDate.of(2026, 10, 1),
                "Erika Musterfrau", "Hauptstraße 5, 80331 München", items);

        byte[] pdf = pdfGeneratorService.generateInvoice(order);
        write("rechnung-mehrseitig.pdf", pdf);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            assertTrue(pages > 1, "erwartet mehrere Seiten, war: " + pages);

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Seite " + pages + " von " + pages), text);
            assertEquals(pages, text.split("Beschreibung", -1).length - 1, "Tabellenkopf auf jeder Seite");
            assertTrue(text.contains("Position 60"), text);
            assertTrue(text.contains("18.281,70 €"), text); // 9,99 € * (1 + ... + 60)
        }
    }

    private static OrderDto sampleOrder() {
        return new OrderDto(
                "INV-2026-001",
                LocalDate.of(2026, 10, 1),
                "Max Mustermann",
                "Beispielweg 1, 12345 Musterstadt",
                List.of(
                        new OrderItemDto("Webentwicklung Stundensatz", 10, new BigDecimal("90.00")),
                        new OrderItemDto("Hosting (12 Monate)", 1, new BigDecimal("120.00"))));
    }

    private static Path write(String fileName, byte[] content) throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        return Files.write(OUTPUT_DIR.resolve(fileName), content);
    }
}
