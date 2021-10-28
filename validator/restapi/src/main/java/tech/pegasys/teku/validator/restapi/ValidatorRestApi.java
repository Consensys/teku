/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.validator.restapi;

import static tech.pegasys.teku.infrastructure.http.HostAllowlistUtils.isHostAuthorized;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;

import com.google.common.base.Throwables;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.plugin.openapi.OpenApiOptions;
import io.javalin.plugin.openapi.OpenApiPlugin;
import io.javalin.plugin.openapi.jackson.JacksonModelConverterFactory;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.net.BindException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.provider.JsonProvider;

public class ValidatorRestApi {

  private Server jettyServer;
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();
  private static final Logger LOG = LogManager.getLogger();

  private void initialize(final ValidatorRestApiConfig configuration) {
    if (app._conf != null) {
      app._conf.server(
          () -> {
            jettyServer = new Server(configuration.getRestApiPort());
            LOG.debug("Setting Max URL length to {}", configuration.getMaxUrlLength());
            for (Connector c : jettyServer.getConnectors()) {
              final HttpConfiguration httpConfiguration =
                  c.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();

              if (httpConfiguration != null) {
                httpConfiguration.setRequestHeaderSize(configuration.getMaxUrlLength());
              }
            }
            return jettyServer;
          });
    }
    app.jettyServer().setServerHost(configuration.getRestApiInterface());
    app.jettyServer().setServerPort(configuration.getRestApiPort());

    addHostAllowlistHandler(configuration);

    addExceptionHandlers();
  }

  private void addHostAllowlistHandler(final ValidatorRestApiConfig configuration) {
    app.before(
        (ctx) -> {
          String header = ctx.host();
          if (!isHostAuthorized(configuration.getRestApiHostAllowlist(), header)) {
            LOG.debug("Host not authorized " + header);
            throw new ForbiddenResponse("Host not authorized");
          }
        });
  }

  private void addExceptionHandlers() {
    app.exception(ServiceUnavailableException.class, this::serviceUnavailable);
    app.exception(BadRequestException.class, this::badRequest);
    // Add catch-all handler
    app.exception(
        Exception.class,
        (e, ctx) -> {
          LOG.error("Failed to process request to URL {}", ctx.url(), e);
          ctx.status(SC_INTERNAL_SERVER_ERROR);
          setErrorBody(
              ctx,
              () ->
                  jsonProvider.objectToJSON(
                      new HttpErrorResponse(
                          SC_INTERNAL_SERVER_ERROR, "An unexpected error occurred")));
        });
  }

  private void serviceUnavailable(final Throwable throwable, final Context context) {
    context.status(SC_SERVICE_UNAVAILABLE);
    setErrorBody(
        context,
        () ->
            jsonProvider.objectToJSON(
                new HttpErrorResponse(SC_SERVICE_UNAVAILABLE, "Service unavailable")));
  }

  private void badRequest(final Throwable throwable, final Context context) {
    context.status(SC_BAD_REQUEST);
    setErrorBody(
        context,
        () ->
            jsonProvider.objectToJSON(
                new HttpErrorResponse(SC_BAD_REQUEST, throwable.getMessage())));
  }

  private void setErrorBody(final Context ctx, final ExceptionThrowingSupplier<String> body) {
    try {
      ctx.json(body.get());
    } catch (final Throwable e) {
      LOG.error("Failed to serialize internal server error response", e);
    }
  }

  public ValidatorRestApi(final ValidatorRestApiConfig configuration) {
    this(
        configuration,
        Javalin.create(
            config -> {
              config.registerPlugin(new OpenApiPlugin(getOpenApiOptions(configuration)));
              config.defaultContentType = "application/json";
              config.showJavalinBanner = false;
              if (configuration.getRestApiCorsAllowedOrigins() != null
                  && !configuration.getRestApiCorsAllowedOrigins().isEmpty()) {
                if (configuration.getRestApiCorsAllowedOrigins().contains("*")) {
                  config.enableCorsForAllOrigins();
                } else {
                  config.enableCorsForOrigin(
                      configuration.getRestApiCorsAllowedOrigins().toArray(new String[0]));
                }
              }
            }));
  }

  ValidatorRestApi(final ValidatorRestApiConfig configuration, final Javalin app) {
    this.app = app;
    initialize(configuration);
  }

  public void start() {
    try {
      app.start();
    } catch (RuntimeException ex) {
      if (Throwables.getRootCause(ex) instanceof BindException) {
        throw new InvalidConfigurationException(
            String.format(
                "TCP Port %d is already in use. "
                    + "You may need to stop another process or change the HTTP port for this process.",
                getListenPort()));
      }
    }
  }

  public int getListenPort() {
    return app.jettyServer().getServerPort();
  }

  private static OpenApiOptions getOpenApiOptions(final ValidatorRestApiConfig config) {
    final JsonProvider jsonProvider = new JsonProvider();
    final JacksonModelConverterFactory factory =
        new JacksonModelConverterFactory(jsonProvider.getObjectMapper());

    final Info applicationInfo =
        new Info()
            .title(StringUtils.capitalize(VersionProvider.CLIENT_IDENTITY))
            .version(VersionProvider.IMPLEMENTATION_VERSION)
            .description("An implementation of the key management standard Rest API.")
            .license(
                new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html"));
    final OpenApiOptions options =
        new OpenApiOptions(applicationInfo).modelConverterFactory(factory);
    if (config.isRestApiDocsEnabled()) {
      options.path("/swagger-docs").swagger(new SwaggerOptions("/swagger-ui"));
    }
    return options;
  }

  public void stop() {
    try {
      if (jettyServer != null) {
        jettyServer.stop();
      }
    } catch (Exception ex) {
      LOG.error(ex);
    }
    app.stop();
  }
}
