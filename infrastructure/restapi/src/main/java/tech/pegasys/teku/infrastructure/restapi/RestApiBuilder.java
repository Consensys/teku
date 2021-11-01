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

package tech.pegasys.teku.infrastructure.restapi;

import static java.util.Collections.emptyList;

import io.javalin.Javalin;
import io.javalin.core.JavalinConfig;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.OptionalInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;

public class RestApiBuilder {
  private static final Logger LOG = LogManager.getLogger();

  private int port;
  private String listenAddress = "127.0.0.1";
  private OptionalInt maxUrlLength = OptionalInt.empty();
  private List<String> corsAllowedOrigins = emptyList();
  private List<String> hostAllowlist = emptyList();

  public RestApiBuilder listenAddress(final String listenAddress) {
    this.listenAddress = listenAddress;
    return this;
  }

  public RestApiBuilder port(final int port) {
    this.port = port;
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

  public RestApi build() {
    final Javalin app =
        Javalin.create(
            config -> {
              config.defaultContentType = "application/json";
              config.showJavalinBanner = false;
              configureCors(config);
              config.server(this::createJettyServer);
            });

    if (!hostAllowlist.isEmpty()) {
      app.before(new HostAllowlistHandler(hostAllowlist));
    }

    // TODO: Support adding error handlers
    // TODO: Register /swagger-docs handler to expose OpenAPI spec
    return new RestApi(app);
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
    final Server server = new Server(InetSocketAddress.createUnresolved(listenAddress, port));
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
    return server;
  }
}
