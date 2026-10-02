package com.example.report;

import jakarta.inject.Inject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startet einen echten CDI-Container (Weld SE) – wie im Application Server:
 * {@link ThymeleafConfig} produziert die Engine, die Services bekommen sie injiziert.
 * <p>
 * Die erzeugten PDFs landen zum Anschauen unter {@code target/reports/}.
 */
@EnableAutoWeld
@AddBeanClasses(ThymeleafConfig.class)
class PdfGeneratorServiceTest {

    @Inject
    PdfGeneratorService pdfGeneratorService;

    @Test
    void generatesInvoicePdf() throws IOException {
        byte[] pdf = pdfGeneratorService.generateInvoice(TestData.sampleOrder());
        TestData.write("rechnung.pdf", pdf);

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
    }

    @Test
    void longInvoiceBreaksIntoMultiplePages() throws IOException {
        byte[] pdf = pdfGeneratorService.generateInvoice(TestData.longOrder());
        TestData.write("rechnung-mehrseitig.pdf", pdf);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            assertTrue(pages > 1, "erwartet mehrere Seiten, war: " + pages);

            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Seite " + pages + " von " + pages), text);
            assertEquals(pages, text.split("Beschreibung", -1).length - 1, "Tabellenkopf auf jeder Seite");
            assertTrue(text.contains("Position 60"), text);
            assertTrue(text.contains("18.281,70 €"), text);
        }
    }
}
