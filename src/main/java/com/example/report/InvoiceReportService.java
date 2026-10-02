package com.example.report;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Einstiegspunkt für Aufrufer (REST, Batch, Timer): eine Rechnung in einem beliebigen Format. */
@ApplicationScoped
public class InvoiceReportService {

    @Inject
    PdfGeneratorService pdfGenerator;

    @Inject
    DocxGeneratorService docxGenerator;

    @Inject
    XlsxGeneratorService xlsxGenerator;

    public byte[] generate(OrderDto order, ReportFormat format) {
        return switch (format) {
            case PDF -> pdfGenerator.generateInvoice(order);
            case DOCX -> docxGenerator.generateInvoice(order);
            case XLSX -> xlsxGenerator.generateInvoice(order);
        };
    }
}
