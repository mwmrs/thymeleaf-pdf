package com.example.report;

import jakarta.inject.Inject;
import org.docx4j.TextUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tr;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Erzeugt die Rechnung als Word-Dokument und liest sie mit docx4j wieder ein. */
@EnableAutoWeld
@AddBeanClasses(ThymeleafConfig.class)
class DocxGeneratorServiceTest {

    @Inject
    DocxGeneratorService docxGeneratorService;

    @Test
    void generatesInvoiceDocx() throws Exception {
        byte[] docx = docxGeneratorService.generateInvoice(TestData.sampleOrder());
        TestData.write("rechnung.docx", docx);

        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docx));
        MainDocumentPart main = wordPackage.getMainDocumentPart();

        String text = text(main.getJaxbElement());
        assertTrue(text.contains("RECHNUNG"), text);
        assertTrue(text.contains("INV-2026-001"), text);
        assertTrue(text.contains("01.10.2026"), text);
        assertTrue(text.contains("Empfänger"), text);
        assertTrue(text.contains("Max Mustermann"), text);
        assertTrue(text.contains("Webentwicklung Stundensatz"), text);
        assertTrue(text.contains("1.020,00 €"), text);

        // Kopfbereich + Positionen sind echte Word-Tabellen
        List<Tbl> tables = tables(main);
        assertEquals(2, tables.size());
        long itemRows = tables.get(1).getContent().stream().map(XmlUtils::unwrap).filter(Tr.class::isInstance).count();
        assertEquals(3, itemRows, "Kopfzeile + 2 Positionen");
        long writableWidth = 11907 - 2 * 1134;
        for (Tbl table : tables) {
            long width = table.getTblGrid().getGridCol().stream().mapToLong(col -> col.getW().longValue()).sum();
            assertTrue(width <= writableWidth && width > writableWidth - 20,
                    "Tabelle füllt den Satzspiegel aus, ragt aber nicht hinaus: " + width);
        }

        // A4 hochkant, Ränder und Fußzeile mit Seitenzahl-Feldern
        SectPr sectPr = wordPackage.getDocumentModel().getSections().get(0).getSectPr();
        assertEquals(BigInteger.valueOf(11907), sectPr.getPgSz().getW()); // 210 mm
        assertEquals(BigInteger.valueOf(1134), sectPr.getPgMar().getLeft()); // 20 mm
        FooterPart footer = wordPackage.getDocumentModel().getSections().get(0)
                .getHeaderFooterPolicy().getDefaultFooter();
        assertNotNull(footer);
        String footerXml = XmlUtils.marshaltoString(footer.getJaxbElement());
        assertTrue(footerXml.contains("PAGE") && footerXml.contains("NUMPAGES"), footerXml);
    }

    @Test
    void longInvoiceRepeatsTableHeader() throws Exception {
        byte[] docx = docxGeneratorService.generateInvoice(TestData.longOrder());
        TestData.write("rechnung-mehrseitig.docx", docx);

        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docx));
        Tbl items = tables(wordPackage.getMainDocumentPart()).get(1);
        List<Tr> rows = items.getContent().stream().map(XmlUtils::unwrap)
                .filter(Tr.class::isInstance).map(Tr.class::cast).toList();

        assertEquals(61, rows.size(), "Kopfzeile + 60 Positionen");
        // <th>-Zeile wird zur Word-Kopfzeile (siehe docx4j-ImportXHTML.properties)
        assertTrue(XmlUtils.marshaltoString(rows.get(0)).contains("tblHeader"));
        assertFalse(XmlUtils.marshaltoString(rows.get(1)).contains("tblHeader"));
        assertTrue(text(wordPackage.getMainDocumentPart().getJaxbElement()).contains("18.281,70 €"));
    }

    private static List<Tbl> tables(MainDocumentPart main) {
        return main.getContent().stream()
                .map(XmlUtils::unwrap)
                .filter(Tbl.class::isInstance)
                .map(Tbl.class::cast)
                .toList();
    }

    private static String text(Object jaxbElement) throws Exception {
        StringWriter writer = new StringWriter();
        TextUtils.extractText(jaxbElement, writer);
        return writer.toString();
    }
}
