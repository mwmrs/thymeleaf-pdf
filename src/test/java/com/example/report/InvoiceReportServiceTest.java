package com.example.report;

import jakarta.inject.Inject;
import org.jboss.weld.junit5.auto.AddBeanClasses;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ein Einstiegspunkt, drei Formate. */
@EnableAutoWeld
@AddBeanClasses(ThymeleafConfig.class)
class InvoiceReportServiceTest {

    @Inject
    InvoiceReportService reportService;

    @ParameterizedTest
    @EnumSource(ReportFormat.class)
    void generatesEveryFormat(ReportFormat format) {
        byte[] content = reportService.generate(TestData.sampleOrder(), format);

        // PDF beginnt mit "%PDF", DOCX/XLSX sind ZIP-Container ("PK")
        String expectedMagic = format == ReportFormat.PDF ? "%PDF" : "PK";
        String actualMagic = new String(Arrays.copyOf(content, expectedMagic.length()), StandardCharsets.ISO_8859_1);
        assertEquals(expectedMagic, actualMagic);
    }
}
