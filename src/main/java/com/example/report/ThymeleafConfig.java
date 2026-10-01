package com.example.report;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Konfiguriert Thymeleaf einmalig als CDI-Bean – ohne Servlet, ohne HTTP-Session.
 * Damit kann die Engine in Batch-Jobs, Timer-Services, MDBs usw. injiziert werden.
 */
@ApplicationScoped
public class ThymeleafConfig {

    /** Ablageort der Templates im Classpath: {@code src/main/resources/templates/}. */
    static final String TEMPLATE_FOLDER = "templates/";

    /**
     * Rückgabetyp ist bewusst das Interface {@link ITemplateEngine}: {@link TemplateEngine}
     * besitzt {@code public final}-Methoden und ist damit für einen normal-scoped
     * CDI-Client-Proxy ({@code @ApplicationScoped}) nicht proxyfähig.
     */
    @Produces
    @ApplicationScoped
    public ITemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(TEMPLATE_FOLDER);
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true); // Im Produktionseinsatz cachen

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
