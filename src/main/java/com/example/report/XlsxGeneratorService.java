package com.example.report;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Erzeugt Rechnungen als Excel-Arbeitsmappe – bewusst <em>nicht</em> aus dem HTML, sondern direkt
 * aus den Daten: Zahlen bleiben Zahlen, Datumswerte bleiben Datumswerte, Summen sind Formeln.
 * <p>
 * Für sehr große Listen (≫ 10.000 Zeilen) statt {@link XSSFWorkbook} die Streaming-Variante
 * {@code SXSSFWorkbook} verwenden.
 */
@ApplicationScoped
public class XlsxGeneratorService {

    static final String SHEET_NAME = "Rechnung";

    /** Zeile (0-basiert) der Positions-Überschriften. */
    static final int ITEM_HEADER_ROW = 7;

    private static final String CURRENCY_FORMAT = "#,##0.00 \"€\"";
    private static final String DATE_FORMAT = "dd.mm.yyyy";

    public byte[] generateInvoice(OrderDto order) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            writeHeader(sheet, styles, order);
            int lastItemRow = writeItems(sheet, styles, order.items());
            writeTotal(sheet, styles, lastItemRow);
            layout(sheet, lastItemRow);

            // Formelergebnisse mitspeichern – sonst zeigen Vorschauen/Viewer ohne Rechenkern leere Zellen
            XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("XLSX-Erzeugung fehlgeschlagen", e);
        }
    }

    private static void writeHeader(Sheet sheet, Styles styles, OrderDto order) {
        Cell title = sheet.createRow(0).createCell(0);
        title.setCellValue("Rechnung – Acme GmbH");
        title.setCellStyle(styles.title);

        labeledRow(sheet, 2, "Rechnungs-Nr.", styles).setCellValue(order.id());

        Cell date = labeledRow(sheet, 3, "Datum", styles);
        date.setCellValue(order.date());
        date.setCellStyle(styles.date);

        labeledRow(sheet, 4, "Kunde", styles).setCellValue(order.customerName());
        labeledRow(sheet, 5, "Adresse", styles).setCellValue(order.customerAddress());
    }

    private static Cell labeledRow(Sheet sheet, int rowIndex, String label, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        return row.createCell(1);
    }

    /** @return Index der letzten Positionszeile (0-basiert) */
    private static int writeItems(Sheet sheet, Styles styles, List<OrderItemDto> items) {
        Row header = sheet.createRow(ITEM_HEADER_ROW);
        String[] titles = {"Beschreibung", "Menge", "Einzelpreis", "Gesamt"};
        for (int i = 0; i < titles.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(i == 0 ? styles.tableHeader : styles.tableHeaderRight);
        }

        int rowIndex = ITEM_HEADER_ROW;
        for (OrderItemDto item : items) {
            Row row = sheet.createRow(++rowIndex);
            int excelRow = rowIndex + 1; // Excel-Zeilennummern sind 1-basiert

            row.createCell(0).setCellValue(item.description());

            Cell quantity = row.createCell(1);
            quantity.setCellValue(item.quantity());
            quantity.setCellStyle(styles.integer);

            Cell price = row.createCell(2);
            price.setCellValue(item.price().doubleValue());
            price.setCellStyle(styles.currency);

            Cell total = row.createCell(3);
            total.setCellFormula("B" + excelRow + "*C" + excelRow);
            total.setCellStyle(styles.currency);
        }
        return rowIndex;
    }

    private static void writeTotal(Sheet sheet, Styles styles, int lastItemRow) {
        Row row = sheet.createRow(lastItemRow + 2);
        Cell label = row.createCell(2);
        label.setCellValue("Gesamtsumme");
        label.setCellStyle(styles.totalLabel);

        Cell sum = row.createCell(3);
        sum.setCellFormula("SUM(D" + (ITEM_HEADER_ROW + 2) + ":D" + (lastItemRow + 1) + ")");
        sum.setCellStyle(styles.total);
    }

    private static void layout(Sheet sheet, int lastItemRow) {
        // Feste Breiten statt autoSizeColumn(): das bräuchte AWT-Fonts auf dem Server
        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 15 * 256);
        sheet.setColumnWidth(3, 15 * 256);

        sheet.createFreezePane(0, ITEM_HEADER_ROW + 1);
        sheet.setAutoFilter(new CellRangeAddress(ITEM_HEADER_ROW, lastItemRow, 0, 3));

        PrintSetup print = sheet.getPrintSetup();
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.setFitToPage(true);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setRepeatingRows(new CellRangeAddress(ITEM_HEADER_ROW, ITEM_HEADER_ROW, -1, -1));
    }

    /** Zellformate einmal pro Arbeitsmappe anlegen (Excel erlaubt nur begrenzt viele). */
    private static final class Styles {
        final CellStyle title;
        final CellStyle label;
        final CellStyle date;
        final CellStyle tableHeader;
        final CellStyle tableHeaderRight;
        final CellStyle integer;
        final CellStyle currency;
        final CellStyle totalLabel;
        final CellStyle total;

        Styles(Workbook workbook) {
            short currencyFormat = workbook.createDataFormat().getFormat(CURRENCY_FORMAT);
            Font bold = workbook.createFont();
            bold.setBold(true);

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.ROYAL_BLUE.getIndex());
            title = workbook.createCellStyle();
            title.setFont(titleFont);

            label = workbook.createCellStyle();
            label.setFont(bold);

            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat(DATE_FORMAT));
            date.setAlignment(HorizontalAlignment.LEFT);

            tableHeader = workbook.createCellStyle();
            tableHeader.setFont(bold);
            tableHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tableHeader.setBorderBottom(BorderStyle.THIN);

            tableHeaderRight = workbook.createCellStyle();
            tableHeaderRight.cloneStyleFrom(tableHeader);
            tableHeaderRight.setAlignment(HorizontalAlignment.RIGHT);

            integer = workbook.createCellStyle();
            integer.setDataFormat(workbook.createDataFormat().getFormat("0"));

            currency = workbook.createCellStyle();
            currency.setDataFormat(currencyFormat);

            totalLabel = workbook.createCellStyle();
            totalLabel.setFont(bold);
            totalLabel.setAlignment(HorizontalAlignment.RIGHT);

            total = workbook.createCellStyle();
            total.setFont(bold);
            total.setDataFormat(currencyFormat);
            total.setBorderTop(BorderStyle.THIN);
        }
    }
}
