package com.example.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Erzeugt Rechnungs-PDFs: Daten → Thymeleaf-Template → HTML → OpenHTMLtoPDF → {@code byte[]}.
 */
@ApplicationScoped
public class PdfGeneratorService {

    @Inject
    InvoiceHtmlRenderer htmlRenderer;

    public byte[] generateInvoice(OrderDto order) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(htmlRenderer.renderDocument(order), InvoiceHtmlRenderer.BASE_URI);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF-Erzeugung fehlgeschlagen", e);
        }
    }
}
