# thymeleaf-pdf

Beispiel für **serverseitig erzeugte PDF-Reports** in einem Jakarta-EE-Backend:

```
Daten (DTO) ──► Thymeleaf (HTML-Template + CSS) ──► HTML-String ──► OpenHTMLtoPDF ──► byte[]
```

Kein proprietärer Report-Designer: Das Layout ist reines HTML/CSS, versionierbar in Git und
im Browser/IDE-Preview gestaltbar. Thymeleaf läuft ohne Servlet/HTTP-Session und kann daher
in Batch-Jobs, `@Schedule`-Timern, MDBs oder REST-Ressourcen genutzt werden.

## Ausführen

```bash
mvn test
```

Der Test startet einen echten CDI-Container (Weld SE via `weld-junit5`), erzeugt die PDFs und
prüft deren Textinhalt mit PDFBox. Ergebnis zum Anschauen:

- `target/pdf/rechnung.pdf` – einseitige Rechnung
- `target/pdf/rechnung-mehrseitig.pdf` – 60 Positionen, Seitenumbruch mit wiederholtem Tabellenkopf

## Aufbau

| Datei | Zweck |
|---|---|
| `ThymeleafConfig` | CDI-Producer für die `ITemplateEngine` (Templates aus dem Classpath, gecacht) |
| `PdfGeneratorService` | `renderInvoiceHtml(...)` → HTML, `generateInvoice(...)` → PDF-`byte[]` |
| `OrderDto`, `OrderItemDto` | Datenmodell (Records) – in der Praxis aus JPA-Entitäten befüllt |
| `templates/invoice.html` | Thymeleaf *Natural Template* (öffnet im Browser mit Beispieldaten) |
| `templates/invoice.css` | Layout inkl. CSS Paged Media (`@page`, Seitenzahlen, Ränder) |
| `PdfGeneratorServiceTest` | ausführbares Beispiel |

### Template gestalten

`src/main/resources/templates/invoice.html` direkt im Browser öffnen (oder Live Server /
IntelliJ Browser Preview). Dank der `th:*`-Attribute zeigt die Datei Beispielwerte an, das CSS
wird über den relativen Pfad `invoice.css` geladen – genauso wie später im PDF-Renderer.

## Einsatz im Application Server

`thymeleaf`, `openhtmltopdf-pdfbox` und `jsoup` werden mit der WAR ausgeliefert, die CDI-API
stellt der Server (`provided`). Beispiele:

```java
@Path("/invoices")
public class InvoiceResource {

    @Inject PdfGeneratorService pdfGeneratorService;

    @GET
    @Path("/{id}/pdf")
    @Produces("application/pdf")
    public Response pdf(@PathParam("id") String id) {
        byte[] pdf = pdfGeneratorService.generateInvoice(loadOrder(id));
        return Response.ok(pdf)
                .header("Content-Disposition", "attachment; filename=\"" + id + ".pdf\"")
                .build();
    }
}
```

```java
@Singleton
public class MonthlyInvoiceJob {

    @Inject PdfGeneratorService pdfGeneratorService;

    @Schedule(dayOfMonth = "1", hour = "2", persistent = false)
    void run() {
        // Daten laden → generateInvoice(...) → speichern / per Mail versenden
    }
}
```

## Abweichungen vom Ausgangs-Chat (bewusst)

- **`io.github.openhtmltopdf:openhtmltopdf-pdfbox`** statt `com.openhtmltopdf:…:1.0.10`:
  Das ursprüngliche Projekt ist nicht mehr gepflegt; der Fork ist API-kompatibel (gleiche
  Packages) und basiert auf PDFBox 3.
- **Producer liefert `ITemplateEngine`** statt `TemplateEngine`: `TemplateEngine` hat
  `public final`-Methoden und ist daher für einen `@ApplicationScoped`-Client-Proxy nicht
  proxyfähig (Deployment-Fehler).
- **jsoup als HTML5-Parser** vor OpenHTMLtoPDF: `withHtmlContent(...)` verlangt wohlgeformtes
  XHTML. Über `W3CDom` wird beliebiges HTML5 robust verarbeitet.
- **Kein Flexbox/Grid**: OpenHTMLtoPDF unterstützt CSS 2.1 + Teile von CSS3. Der Kopfbereich
  ist deshalb eine Tabelle. Für wiederholte Tabellenköpfe dient `-fs-table-paginate: paginate`.

## Hinweise für den Produktivbetrieb

- **Schriften**: Ohne eingebettete Fonts nutzt OpenHTMLtoPDF die PDF-Standardschriften
  (Helvetica etc., Zeichensatz WinAnsi – Umlaute und `€` funktionieren). Für eigene
  Corporate-Fonts oder vollen Unicode-Umfang eine TTF einbinden:
  `builder.useFont(() -> getClass().getResourceAsStream("/fonts/Inter.ttf"), "Inter");`
- **PDF/A** (Archivierung): `builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_U)` – erfordert
  eingebettete Fonts.
- **Migration nach Quarkus**: Producer und Service sind reines CDI und laufen unverändert unter
  Quarkus (ArC); die Templates bleiben gleich. Für Native Images ggf. Reflection-Registrierung
  der DTOs (`@RegisterForReflection`). Alternativ schrittweiser Wechsel auf Qute.
