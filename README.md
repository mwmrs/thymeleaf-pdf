# thymeleaf-pdf

Beispiel für **serverseitig erzeugte Reports** (PDF, Word, Excel) in einem Jakarta-EE-Backend:

```
                   ┌─► HTML (Thymeleaf + CSS) ─┬─► PDF   (OpenHTMLtoPDF)
OrderDto ──────────┤                           └─► DOCX  (docx4j-ImportXHTML)
                   └─► XLSX  (Apache POI, direkt aus den Daten)
```

Kein proprietärer Report-Designer: PDF und Word entstehen aus **einem** HTML/CSS-Template,
versionierbar in Git und im Browser/IDE-Preview gestaltbar. Thymeleaf läuft ohne
Servlet/HTTP-Session und kann daher in Batch-Jobs, `@Schedule`-Timern, MDBs oder REST-Ressourcen
genutzt werden.

## Ausführen

```bash
mvn test
```

Die Tests starten einen echten CDI-Container (Weld SE via `weld-junit5`), erzeugen die Dateien
und lesen sie zur Prüfung wieder ein (PDFBox, docx4j, Apache POI). Ergebnis zum Anschauen
unter `target/reports/`:

| Datei | Inhalt |
|---|---|
| `rechnung.pdf` / `.docx` / `.xlsx` | einseitige Rechnung |
| `rechnung-mehrseitig.pdf` / `.docx` / `.xlsx` | 60 Positionen, Seitenumbruch mit wiederholtem Tabellenkopf |

## Aufbau

| Datei | Zweck |
|---|---|
| `ThymeleafConfig` | CDI-Producer für die `ITemplateEngine` (Templates aus dem Classpath, gecacht) |
| `InvoiceHtmlRenderer` | Daten → HTML (String oder W3C-DOM) – gemeinsame Basis für PDF und DOCX |
| `PdfGeneratorService` | HTML → PDF mit OpenHTMLtoPDF |
| `DocxGeneratorService` | HTML → DOCX mit docx4j-ImportXHTML (+ Seitenränder, Fußzeile) |
| `XlsxGeneratorService` | Daten → XLSX mit Apache POI (Zahlen, Datum, Formeln) |
| `InvoiceReportService` + `ReportFormat` | ein Einstiegspunkt: `generate(order, PDF/DOCX/XLSX)` inkl. Media-Type |
| `OrderDto`, `OrderItemDto` | Datenmodell (Records) – in der Praxis aus JPA-Entitäten befüllt |
| `templates/invoice.html` | Thymeleaf *Natural Template* (öffnet im Browser mit Beispieldaten) |
| `templates/invoice.css` | Layout inkl. CSS Paged Media (`@page`, Seitenzahlen, Ränder) |
| `docx4j-ImportXHTML.properties` | Konfiguration des HTML→DOCX-Imports |

### Template gestalten

`src/main/resources/templates/invoice.html` direkt im Browser öffnen (oder Live Server /
IntelliJ Browser Preview). Dank der `th:*`-Attribute zeigt die Datei Beispielwerte an, das CSS
wird über den relativen Pfad `invoice.css` geladen – genauso wie später im PDF- und DOCX-Renderer.

## Warum welches Format so erzeugt wird

**DOCX aus dem HTML.** docx4j-ImportXHTML nutzt intern dieselbe CSS-Engine wie OpenHTMLtoPDF und
erzeugt ein echtes, editierbares Word-Dokument (Absätze, Tabellen, Farben, Rahmen, Schriften).
Was Word anders abbildet, setzt `DocxGeneratorService` im Code:

- `@page`-Ränder → Word-Seitenränder, `@bottom-right` → Fußzeile mit `PAGE`/`NUMPAGES`-Feldern
- `<th>`-Zeilen → Word-Kopfzeilen, die auf jeder Seite wiederholt werden (Properties-Datei)
- Tabellenbreiten: Das Importmodul rechnet Prozentbreiten in den falschen Einheiten um (Faktor 4/3),
  zu breite Tabellen werden daher auf die Satzspiegelbreite skaliert (`fitTablesToPage`).

Nicht geeignet für pixelgenaue Word-Layouts – dafür wäre eine Word-Vorlage mit Platzhaltern
(z. B. poi-tl, docx-stamper) die bessere, aber doppelt zu pflegende Alternative.

**XLSX direkt aus den Daten, nicht aus dem HTML.** Eine Tabelle ist ein Raster typisierter Zellen,
kein Seitenlayout. Im HTML stehen bereits formatierte Texte („1.020,00 €“), mit denen Excel weder
rechnen noch sortieren kann. Mit Apache POI bleiben Zahlen Zahlen (Format `#,##0.00 "€"`), Datumswerte
echte Datumswerte, Zeilensummen und Gesamtsumme sind Formeln; dazu Autofilter, fixierte
Kopfzeile und Druckeinrichtung (A4, Kopfzeile auf jeder Seite).

**Warum POI und nicht JXLS?** JXLS (Excel-Vorlage mit `${…}`-Markern) lohnt sich, wenn viele
verschiedene Excel-Reports existieren oder Fachanwender das Layout in Excel pflegen sollen. Für
einen Export dieser Größe ist POI direkter: kein zusätzlicher Template-Mechanismus, volle
Typsicherheit im Code, leicht testbar – und JXLS baut ohnehin auf POI auf, ein späterer Wechsel
ist also ohne Abhängigkeitsbruch möglich.

## Einsatz im Application Server

Die Bibliotheken werden mit der WAR ausgeliefert, die CDI-API stellt der Server (`provided`).
Beispiele:

```java
@Path("/invoices")
public class InvoiceResource {

    @Inject InvoiceReportService reportService;

    @GET
    @Path("/{id}")
    public Response download(@PathParam("id") String id,
                             @QueryParam("format") @DefaultValue("PDF") ReportFormat format) {
        byte[] content = reportService.generate(loadOrder(id), format);
        return Response.ok(content, format.mediaType())
                .header("Content-Disposition",
                        "attachment; filename=\"" + id + "." + format.fileExtension() + "\"")
                .build();
    }
}
```

```java
@Singleton
public class MonthlyInvoiceJob {

    @Inject InvoiceReportService reportService;

    @Schedule(dayOfMonth = "1", hour = "2", persistent = false)
    void run() {
        // Daten laden → reportService.generate(order, ReportFormat.PDF) → speichern / per Mail versenden
    }
}
```

## Abweichungen vom Ausgangs-Chat (bewusst)

- **`io.github.openhtmltopdf:openhtmltopdf-pdfbox`** statt `com.openhtmltopdf:…:1.0.10`:
  Das ursprüngliche Projekt ist nicht mehr gepflegt; der Fork ist API-kompatibel (gleiche
  Packages), basiert auf PDFBox 3 und wird auch von docx4j-ImportXHTML verwendet.
- **Producer liefert `ITemplateEngine`** statt `TemplateEngine`: `TemplateEngine` hat
  `public final`-Methoden und ist daher für einen `@ApplicationScoped`-Client-Proxy nicht
  proxyfähig (Deployment-Fehler).
- **jsoup als HTML5-Parser** vor OpenHTMLtoPDF/docx4j: Beide verlangen wohlgeformtes XHTML.
  Über `W3CDom` wird beliebiges HTML5 robust verarbeitet.
- **Kein Flexbox/Grid**: OpenHTMLtoPDF unterstützt CSS 2.1 + Teile von CSS3. Der Kopfbereich
  ist deshalb eine Tabelle. Für wiederholte Tabellenköpfe im PDF dient `-fs-table-paginate: paginate`.

## Hinweise für den Produktivbetrieb

- **Schriften**: Ohne eingebettete Fonts nutzt OpenHTMLtoPDF die PDF-Standardschriften
  (Helvetica etc., Zeichensatz WinAnsi – Umlaute und `€` funktionieren). Für eigene
  Corporate-Fonts oder vollen Unicode-Umfang eine TTF einbinden:
  `builder.useFont(() -> getClass().getResourceAsStream("/fonts/Inter.ttf"), "Inter");`
- **PDF/A** (Archivierung): `builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_U)` – erfordert
  eingebettete Fonts.
- **Große Excel-Exporte** (≫ 10.000 Zeilen): `SXSSFWorkbook` (Streaming) statt `XSSFWorkbook`.
- **Logging**: docx4j loggt über SLF4J, Apache POI über Log4j-API. Ohne Logging-Backend
  (z. B. in den Tests) erscheinen nur Hinweise auf der Konsole; im Application Server greift dessen
  Logging.
- **Migration nach Quarkus**: Producer und Services sind reines CDI und laufen unverändert unter
  Quarkus (ArC); die Templates bleiben gleich. Für Native Images ggf. Reflection-Registrierung
  der DTOs (`@RegisterForReflection`) – docx4j (JAXB) ist im Native Mode aufwendiger, JVM-Mode
  ist unproblematisch. Alternativ schrittweiser Wechsel auf Qute.
