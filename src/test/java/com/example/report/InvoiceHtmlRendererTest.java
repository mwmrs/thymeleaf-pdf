package com.example.report;

import jakarta.inject.Inject;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Das HTML lässt sich ohne PDF/DOCX schnell und direkt als String prüfen. */
@EnableAutoWeld
@AddBeanClasses(ThymeleafConfig.class)
class InvoiceHtmlRendererTest {

    @Inject
    InvoiceHtmlRenderer htmlRenderer;

    @Test
    void rendersHtmlFromTemplate() {
        String html = htmlRenderer.renderHtml(TestData.sampleOrder());

        assertTrue(html.contains("INV-2026-001"));
        assertTrue(html.contains("01.10.2026"));
        assertTrue(html.contains("Max Mustermann"));
        assertTrue(html.contains("1.020,00 €"), "Betrag im deutschen Format");
        assertFalse(html.contains("Beispielartikel"), "Natural-Template-Platzhalter ersetzt");
        assertFalse(html.matches("(?s).*\\sth:(text|each|remove)=.*"), "keine Thymeleaf-Attribute im Ergebnis");
    }
}
