package com.example.report;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Objects;

/**
 * Erzeugt Rechnungs-PDFs: Daten → Thymeleaf-Template → HTML → OpenHTMLtoPDF → {@code byte[]}.
 */
@ApplicationScoped
public class PdfGeneratorService {

    static final String INVOICE_TEMPLATE = "invoice";

    private static final Locale LOCALE = Locale.GERMANY;

    /**
     * Basis-URI, gegen die relative Pfade im HTML (z. B. {@code invoice.css}, Bilder)
     * aufgelöst werden – der Template-Ordner im Classpath.
     */
    private static final String BASE_URI = Objects.requireNonNull(
            PdfGeneratorService.class.getClassLoader().getResource(ThymeleafConfig.TEMPLATE_FOLDER),
            "Template-Ordner nicht im Classpath gefunden").toExternalForm();

    @Inject
    ITemplateEngine templateEngine;

    public byte[] generateInvoice(OrderDto order) {
        return htmlToPdf(renderInvoiceHtml(order));
    }

    /** Schritt 1+2: Daten in den Thymeleaf-Kontext packen und HTML rendern. */
    public String renderInvoiceHtml(OrderDto order) {
        Context context = new Context(LOCALE);
        context.setVariable("order", order);
        return templateEngine.process(INVOICE_TEMPLATE, context);
    }

    /** Schritt 3: HTML in PDF umwandeln (OpenHTMLtoPDF). */
    byte[] htmlToPdf(String html) {
        // OpenHTMLtoPDF erwartet wohlgeformtes XHTML. jsoup parst beliebiges HTML5
        // (z. B. <meta charset="UTF-8"> ohne Slash) robust in ein W3C-DOM.
        org.w3c.dom.Document document = new W3CDom().fromJsoup(Jsoup.parse(html, BASE_URI));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(document, BASE_URI);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF-Erzeugung fehlgeschlagen", e);
        }
    }
}
