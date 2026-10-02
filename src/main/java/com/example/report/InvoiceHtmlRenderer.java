package com.example.report;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Objects;

/**
 * Rendert die Rechnung mit Thymeleaf zu HTML – gemeinsame Grundlage für PDF und DOCX.
 */
@ApplicationScoped
public class InvoiceHtmlRenderer {

    static final String INVOICE_TEMPLATE = "invoice";

    private static final Locale LOCALE = Locale.GERMANY;

    /**
     * Basis-URI, gegen die relative Pfade im HTML (z. B. {@code invoice.css}, Bilder)
     * aufgelöst werden – der Template-Ordner im Classpath.
     */
    static final String BASE_URI = Objects.requireNonNull(
            InvoiceHtmlRenderer.class.getClassLoader().getResource(ThymeleafConfig.TEMPLATE_FOLDER),
            "Template-Ordner nicht im Classpath gefunden").toExternalForm();

    @Inject
    ITemplateEngine templateEngine;

    /** Daten in den Thymeleaf-Kontext packen und HTML rendern. */
    public String renderHtml(OrderDto order) {
        Context context = new Context(LOCALE);
        context.setVariable("order", order);
        return templateEngine.process(INVOICE_TEMPLATE, context);
    }

    /**
     * Wie {@link #renderHtml(OrderDto)}, aber als W3C-DOM. OpenHTMLtoPDF und docx4j erwarten
     * wohlgeformtes XHTML; jsoup parst beliebiges HTML5 (z. B. {@code <meta charset="UTF-8">}
     * ohne Slash) robust in ein DOM.
     */
    public org.w3c.dom.Document renderDocument(OrderDto order) {
        return new W3CDom().fromJsoup(Jsoup.parse(renderHtml(order), BASE_URI));
    }
}
