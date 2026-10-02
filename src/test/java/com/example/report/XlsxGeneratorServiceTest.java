package com.example.report;

import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

import static com.example.report.XlsxGeneratorService.ITEM_HEADER_ROW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Erzeugt die Rechnung als Excel-Datei und liest sie mit Apache POI wieder ein. */
@EnableAutoWeld
class XlsxGeneratorServiceTest {

    @Inject
    XlsxGeneratorService xlsxGeneratorService;

    private final DataFormatter germanFormatter = new DataFormatter(Locale.GERMANY);

    @Test
    void generatesInvoiceXlsx() throws IOException {
        byte[] xlsx = xlsxGeneratorService.generateInvoice(TestData.sampleOrder());
        TestData.write("rechnung.xlsx", xlsx);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet(XlsxGeneratorService.SHEET_NAME);

            assertEquals("INV-2026-001", sheet.getRow(2).getCell(1).getStringCellValue());
            // Datum ist ein echter Datumswert, kein Text
            assertTrue(DateUtil.isCellDateFormatted(sheet.getRow(3).getCell(1)));
            assertEquals(LocalDate.of(2026, 10, 1), sheet.getRow(3).getCell(1).getLocalDateTimeCellValue().toLocalDate());
            assertEquals("01.10.2026", germanFormatter.formatCellValue(sheet.getRow(3).getCell(1)));

            Row firstItem = sheet.getRow(ITEM_HEADER_ROW + 1);
            assertEquals("Webentwicklung Stundensatz", firstItem.getCell(0).getStringCellValue());
            assertEquals(10, firstItem.getCell(1).getNumericCellValue());
            assertEquals(90.0, firstItem.getCell(2).getNumericCellValue());
            assertEquals(CellType.FORMULA, firstItem.getCell(3).getCellType());
            assertEquals("B9*C9", firstItem.getCell(3).getCellFormula());
            assertEquals(900.0, firstItem.getCell(3).getNumericCellValue());

            Row totalRow = sheet.getRow(ITEM_HEADER_ROW + 4);
            assertEquals("Gesamtsumme", totalRow.getCell(2).getStringCellValue());
            assertEquals("SUM(D9:D10)", totalRow.getCell(3).getCellFormula());
            assertEquals(1020.0, totalRow.getCell(3).getNumericCellValue());
            assertEquals("1.020,00 €", germanFormatter.formatCellValue(totalRow.getCell(3),
                    workbook.getCreationHelper().createFormulaEvaluator()));
        }
    }

    @Test
    void longInvoiceSumsAllItems() throws IOException {
        byte[] xlsx = xlsxGeneratorService.generateInvoice(TestData.longOrder());
        TestData.write("rechnung-mehrseitig.xlsx", xlsx);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet(XlsxGeneratorService.SHEET_NAME);
            Row totalRow = sheet.getRow(ITEM_HEADER_ROW + 60 + 2);
            assertEquals("SUM(D9:D68)", totalRow.getCell(3).getCellFormula());
            assertEquals(18281.70, totalRow.getCell(3).getNumericCellValue(), 0.001);
        }
    }
}
