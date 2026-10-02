package com.example.report;

/** Unterstützte Ausgabeformate inkl. Media-Type (z. B. für {@code Content-Type}) und Dateiendung. */
public enum ReportFormat {

    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String mediaType;
    private final String fileExtension;

    ReportFormat(String mediaType, String fileExtension) {
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String fileExtension() {
        return fileExtension;
    }
}
