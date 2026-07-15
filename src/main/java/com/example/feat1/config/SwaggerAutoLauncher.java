package com.example.feat1.config;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Opens the Swagger UI in the default browser once the backend has started successfully.
 *
 * <p>Enabled by default; disable with {@code springdoc.auto-open.enabled=false} (recommended for
 * production/CI). Silently skips when running headless (no desktop available).
 */
@Component
@ConditionalOnProperty(
    name = "springdoc.auto-open.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SwaggerAutoLauncher implements ApplicationListener<ApplicationReadyEvent> {

  private static final Logger log = LoggerFactory.getLogger(SwaggerAutoLauncher.class);

  private final Environment environment;

  @Value("${springdoc.swagger-ui.path:/swagger-ui.html}")
  private String swaggerPath;

  public SwaggerAutoLauncher(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    String url = buildSwaggerUrl(event);
    log.info("Swagger UI available at {}", url);
    openInBrowser(url);
  }

  private String buildSwaggerUrl(ApplicationReadyEvent event) {
    int port = 8080;
    String contextPath = "";
    if (event.getApplicationContext() instanceof WebServerApplicationContext webContext) {
      port = webContext.getWebServer().getPort();
    }
    String configuredContextPath = environment.getProperty("server.servlet.context-path", "");
    if (configuredContextPath != null && !configuredContextPath.isBlank()) {
      contextPath = configuredContextPath;
    }
    String path = swaggerPath.startsWith("/") ? swaggerPath : "/" + swaggerPath;
    return "http://localhost:" + port + contextPath + path;
  }

  private void openInBrowser(String url) {
    try {
      if (!Desktop.isDesktopSupported()
          || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        openViaOsCommand(url);
        return;
      }
      Desktop.getDesktop().browse(URI.create(url));
    } catch (Exception e) {
      // Desktop API can fail on headless/Linux JVMs — fall back to the OS opener.
      openViaOsCommand(url);
    }
  }

  private void openViaOsCommand(String url) {
    String os = System.getProperty("os.name", "").toLowerCase();
    String[] command;
    if (os.contains("win")) {
      command = new String[] {"rundll32", "url.dll,FileProtocolHandler", url};
    } else if (os.contains("mac")) {
      command = new String[] {"open", url};
    } else {
      command = new String[] {"xdg-open", url};
    }
    try {
      new ProcessBuilder(command).start();
    } catch (IOException e) {
      log.warn("Could not auto-open Swagger UI in a browser. Open it manually at {}", url);
    }
  }
}
