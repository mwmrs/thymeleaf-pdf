package com.example.report;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.docx4j.XmlUtils;
import org.docx4j.convert.in.xhtml.XHTMLImporterImpl;
import org.docx4j.jaxb.Context;
import org.docx4j.model.structure.PageSizePaper;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.FooterReference;
import org.docx4j.wml.Ftr;
import org.docx4j.wml.HdrFtrRef;
import org.docx4j.wml.SectPr;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblGridCol;
import org.docx4j.wml.TblWidth;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Tr;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

/**
 * Erzeugt Rechnungen als Word-Dokument aus <em>demselben</em> HTML-Template wie das PDF:
 * Daten → Thymeleaf → HTML → docx4j-ImportXHTML → {@code byte[]}.
 * <p>
 * Inhalte und CSS-Formatierung (Tabellen, Farben, Schrift, Rahmen) werden übernommen.
 * Seitenformat, Ränder und die Fußzeile aus {@code @page} kennt Word so nicht – sie werden
 * hier einmalig im Code gesetzt.
 */
@ApplicationScoped
public class DocxGeneratorService {

    /** 1 mm in Twips (1/20 pt), der Maßeinheit von WordprocessingML. */
    private static final double TWIPS_PER_MM = 1440 / 25.4;

    private static final String FOOTER_XML = """
            <w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:p>
                <w:pPr><w:jc w:val="right"/><w:rPr><w:color w:val="777777"/><w:sz w:val="18"/></w:rPr></w:pPr>
                <w:r><w:rPr><w:color w:val="777777"/><w:sz w:val="18"/></w:rPr><w:t xml:space="preserve">Seite </w:t></w:r>
                <w:fldSimple w:instr=" PAGE "><w:r><w:rPr><w:color w:val="777777"/><w:sz w:val="18"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple>
                <w:r><w:rPr><w:color w:val="777777"/><w:sz w:val="18"/></w:rPr><w:t xml:space="preserve"> von </w:t></w:r>
                <w:fldSimple w:instr=" NUMPAGES "><w:r><w:rPr><w:color w:val="777777"/><w:sz w:val="18"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple>
              </w:p>
            </w:ftr>
            """;

    @Inject
    InvoiceHtmlRenderer htmlRenderer;

    public byte[] generateInvoice(OrderDto order) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage(PageSizePaper.A4, false);
            SectPr sectPr = wordPackage.getDocumentModel().getSections().get(0).getSectPr();
            setMargins(sectPr, 20, 20, 25, 20);
            addFooter(wordPackage, sectPr);

            XHTMLImporterImpl importer = new XHTMLImporterImpl(wordPackage);
            List<Object> content = importer.convert(htmlRenderer.renderDocument(order), InvoiceHtmlRenderer.BASE_URI);
            fitTablesToPage(content, writableWidth(sectPr));
            wordPackage.getMainDocumentPart().getContent().addAll(content);

            wordPackage.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("DOCX-Erzeugung fehlgeschlagen", e);
        }
    }

    /** Entspricht {@code @page { margin: … }} aus invoice.css. */
    private static void setMargins(SectPr sectPr, int topMm, int rightMm, int bottomMm, int leftMm) {
        SectPr.PgMar margins = sectPr.getPgMar();
        margins.setTop(twips(topMm));
        margins.setRight(twips(rightMm));
        margins.setBottom(twips(bottomMm));
        margins.setLeft(twips(leftMm));
    }

    /** Entspricht {@code @bottom-right { content: "Seite " counter(page) " von " counter(pages) }}. */
    private static void addFooter(WordprocessingMLPackage wordPackage, SectPr sectPr)
            throws InvalidFormatException, Docx4JException {
        FooterPart footerPart = new FooterPart();
        try {
            footerPart.setJaxbElement((Ftr) XmlUtils.unmarshalString(FOOTER_XML));
        } catch (jakarta.xml.bind.JAXBException e) {
            throw new Docx4JException("Fußzeile ungültig", e);
        }
        Relationship relationship = wordPackage.getMainDocumentPart().addTargetPart(footerPart);

        FooterReference footerReference = Context.getWmlObjectFactory().createFooterReference();
        footerReference.setId(relationship.getId());
        footerReference.setType(HdrFtrRef.DEFAULT);
        sectPr.getEGHdrFtrReferences().add(footerReference);
    }

    /**
     * docx4j-ImportXHTML übernimmt Spaltenbreiten aus dem CSS-Layout von OpenHTMLtoPDF, rechnet dabei
     * aber in dessen internen Einheiten (1920 pro Zoll) statt in Twips (1440 pro Zoll). Tabellen mit
     * {@code width: 100%} werden so um 4/3 zu breit und ragen über den rechten Rand. Daher werden
     * zu breite Tabellen proportional auf die Satzspiegelbreite verkleinert.
     */
    private static void fitTablesToPage(List<Object> content, long writableWidth) {
        for (Object element : content) {
            if (!(XmlUtils.unwrap(element) instanceof Tbl table) || table.getTblGrid() == null) {
                continue;
            }
            List<TblGridCol> columns = table.getTblGrid().getGridCol();
            long tableWidth = columns.stream().mapToLong(col -> col.getW().longValue()).sum();
            if (tableWidth <= writableWidth) {
                continue;
            }
            double factor = (double) writableWidth / tableWidth;
            columns.forEach(col -> col.setW(scale(col.getW(), factor)));
            for (Object row : table.getContent()) {
                if (XmlUtils.unwrap(row) instanceof Tr tr) {
                    for (Object cell : tr.getContent()) {
                        if (XmlUtils.unwrap(cell) instanceof Tc tc && tc.getTcPr() != null && tc.getTcPr().getTcW() != null
                                && TblWidth.TYPE_DXA.equals(tc.getTcPr().getTcW().getType())) {
                            tc.getTcPr().getTcW().setW(scale(tc.getTcPr().getTcW().getW(), factor));
                        }
                    }
                }
            }
        }
    }

    private static long writableWidth(SectPr sectPr) {
        return sectPr.getPgSz().getW().longValue()
                - sectPr.getPgMar().getLeft().longValue()
                - sectPr.getPgMar().getRight().longValue();
    }

    private static BigInteger scale(BigInteger value, double factor) {
        return BigInteger.valueOf((long) Math.floor(value.longValue() * factor));
    }

    private static BigInteger twips(int mm) {
        return BigInteger.valueOf(Math.round(mm * TWIPS_PER_MM));
    }
}
