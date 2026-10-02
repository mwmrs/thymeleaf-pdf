package com.example.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Beispieldaten und Ablage der erzeugten Dateien unter {@code target/reports/}. */
final class TestData {

    static final Path OUTPUT_DIR = Path.of("target", "reports");

    private TestData() {
    }

    /** Einseitige Rechnung, Gesamtsumme 1.020,00 €. */
    static OrderDto sampleOrder() {
        return new OrderDto(
                "INV-2026-001",
                LocalDate.of(2026, 10, 1),
                "Max Mustermann",
                "Beispielweg 1, 12345 Musterstadt",
                List.of(
                        new OrderItemDto("Webentwicklung Stundensatz", 10, new BigDecimal("90.00")),
                        new OrderItemDto("Hosting (12 Monate)", 1, new BigDecimal("120.00"))));
    }

    /** 60 Positionen à 9,99 € × i, Gesamtsumme 18.281,70 € (9,99 € × (1 + … + 60)). */
    static OrderDto longOrder() {
        List<OrderItemDto> items = IntStream.rangeClosed(1, 60)
                .mapToObj(i -> new OrderItemDto("Position " + i, i, new BigDecimal("9.99")))
                .toList();
        return new OrderDto("INV-2026-002", LocalDate.of(2026, 10, 1),
                "Erika Musterfrau", "Hauptstraße 5, 80331 München", items);
    }

    static Path write(String fileName, byte[] content) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            Path file = Files.write(OUTPUT_DIR.resolve(fileName), content);
            System.out.println("Erzeugt: " + file.toAbsolutePath());
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
