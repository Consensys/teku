/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.restapi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import io.javalin.Javalin;
import io.javalin.core.JavalinConfig;
import io.javalin.jetty.JettyUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.JavalinEndpointAdapter;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.openapi.OpenApiDocBuilder;

public class RestApiBuilder {
  private static final Logger LOG = LogManager.getLogger();

  private int port;
  private String listenAddress = "127.0.0.1";
  private OptionalInt maxUrlLength = OptionalInt.empty();
  private List<String> corsAllowedOrigins = emptyList();
  private List<String> hostAllowlist = emptyList();
  private final Map<Class<? extends Exception>, RestApiExceptionHandler<?>> exceptionHandlers =
      new HashMap<>();
  private final List<RestApiEndpoint> endpoints = new ArrayList<>();

  private final OpenApiDocBuilder openApiDocBuilder = new OpenApiDocBuilder();
  private boolean openApiDocsEnabled = false;
  private Optional<Path> maybeKeystorePath = Optional.empty();
  private Optional<Path> maybePasswordPath = Optional.empty();
  private Optional<Path> passwordFilePath = Optional.empty();

  public RestApiBuilder listenAddress(final String listenAddress) {
    this.listenAddress = listenAddress;
    return this;
  }

  public RestApiBuilder port(final int port) {
    this.port = port;
    return this;
  }

  public RestApiBuilder sslCertificate(
      final Optional<Path> sslPath, final Optional<Path> sslPasswordPath) {
    this.maybeKeystorePath = sslPath;
    this.maybePasswordPath = sslPasswordPath;
    return this;
  }

  public RestApiBuilder passwordFilePath(final Path passwordFilePath) {
    this.passwordFilePath = Optional.ofNullable(passwordFilePath);
    return this;
  }

  public RestApiBuilder maxUrlLength(final int maxUrlLength) {
    this.maxUrlLength = OptionalInt.of(maxUrlLength);
    return this;
  }

  public RestApiBuilder corsAllowedOrigins(final List<String> corsAllowedOrigins) {
    this.corsAllowedOrigins = corsAllowedOrigins;
    return this;
  }

  public RestApiBuilder hostAllowlist(final List<String> hostAllowlist) {
    this.hostAllowlist = hostAllowlist;
    return this;
  }

  public <T extends Exception> RestApiBuilder exceptionHandler(
      final Class<T> exceptionType, final RestApiExceptionHandler<T> handler) {
    this.exceptionHandlers.put(exceptionType, handler);
    return this;
  }

  public RestApiBuilder openApiDocsEnabled(final boolean openApiDocsEnabled) {
    this.openApiDocsEnabled = openApiDocsEnabled;
    return this;
  }

  public RestApiBuilder openApiInfo(final Consumer<OpenApiDocBuilder> handler) {
    handler.accept(openApiDocBuilder);
    return this;
  }

  public RestApiBuilder endpoint(final RestApiEndpoint endpoint) {
    this.openApiDocBuilder.endpoint(endpoint);
    this.endpoints.add(endpoint);
    return this;
  }

  public RestApi build() {
    final SwaggerUIBuilder swaggerBuilder = new SwaggerUIBuilder(openApiDocsEnabled);
    final Javalin app =
        Javalin.create(
            config -> {
              config.defaultContentType = "application/json";
              config.showJavalinBanner = false;
              configureCors(config);
              swaggerBuilder.configureUI(config);
              config.server(this::createJettyServer);
            });

    if (!hostAllowlist.isEmpty()) {
      app.before(new HostAllowlistHandler(hostAllowlist));
    }

    endpoints.forEach(endpoint -> JavalinEndpointAdapter.addEndpoint(app, endpoint));

    addExceptionHandlers(app);
    Optional<String> restApiDocs = swaggerBuilder.configureDocs(app, openApiDocBuilder);
    return new RestApi(app, restApiDocs, passwordFilePath);
  }

  private void addExceptionHandlers(final Javalin app) {
    exceptionHandlers.forEach(
        (exceptionType, handler) -> addExceptionHandler(app, exceptionType, handler));
    // Always register a catch-all exception handler
    app.exception(Exception.class, new DefaultExceptionHandler<>());
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // builder API guarantees types match up
  private void addExceptionHandler(
      final Javalin app, final Class<?> exceptionType, final RestApiExceptionHandler<?> handler) {
    app.exception(
        (Class) exceptionType,
        (exception, ctx) -> {
          try {
            final HttpErrorResponse response =
                ((RestApiExceptionHandler) handler).handleException(exception);
            ctx.status(response.getCode());
            if (response.getCode() != SC_NO_CONTENT) {
              ctx.json(JsonUtil.serialize(response, HTTP_ERROR_RESPONSE_TYPE));
            }
          } catch (final Throwable t) {
            ctx.status(SC_INTERNAL_SERVER_ERROR);
            LOG.error("Exception handler throw exception", t);
          }
        });
  }

  private void configureCors(final JavalinConfig config) {
    if (!corsAllowedOrigins.isEmpty()) {
      if (corsAllowedOrigins.contains("*")) {
        config.enableCorsForAllOrigins();
      } else {
        config.enableCorsForOrigin(corsAllowedOrigins.toArray(new String[0]));
      }
    }
  }

  private Server createJettyServer() {
    final Server server = new Server();
    final ServerConnector connector;
    if (maybeKeystorePath.isPresent()) {
      connector = new ServerConnector(server, getSslContextFactory());
    } else {
      connector = new ServerConnector(server);
    }
    connector.setPort(port);
    connector.setHost(listenAddress);
    server.setConnectors(new Connector[] {connector});

    maxUrlLength.ifPresent(
        maxLength -> {
          LOG.debug("Setting Max URL length to {}", maxLength);
          for (Connector c : server.getConnectors()) {
            final HttpConfiguration httpConfiguration =
                c.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();

            if (httpConfiguration != null) {
              httpConfiguration.setRequestHeaderSize(maxLength);
            }
          }
        });
    JettyUtil.INSTANCE.setLogIfNotStarted(false);
    return server;
  }

  private SslContextFactory getSslContextFactory() {
    SslContextFactory sslContextFactory = new SslContextFactory.Server();
    maybeKeystorePath.ifPresent(
        keystorePath -> sslContextFactory.setKeyStorePath(keystorePath.toString()));
    maybePasswordPath.ifPresentOrElse(
        passwordPath -> {
          try {
            sslContextFactory.setKeyStorePassword(Files.readString(passwordPath, UTF_8).trim());
          } catch (IOException e) {
            LOG.error("Failed to read password file for validator api keystore", e);
          }
        },
        () -> sslContextFactory.setKeyStorePassword(""));

    sslContextFactory.setProvider("Conscrypt");

    return sslContextFactory;
  }

  public interface RestApiExceptionHandler<T extends Exception> {
    HttpErrorResponse handleException(T t);
  }
}
