/*
 * Copyright Consensys Software Inc., 2026
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

import com.google.common.base.Throwables;
import io.javalin.Javalin;
import io.javalin.util.JavalinBindException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.Service;

public class RestApi extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private final Javalin app;
  private final Optional<String> restApiDocs;
  private final Optional<Path> passwordPath;

  public RestApi(
      final Javalin app,
      final Optional<String> restApiDocs,
      final Optional<Path> passwordFilePath) {
    this.app = app;
    this.restApiDocs = restApiDocs;
    this.passwordPath = passwordFilePath;
  }

  public Optional<String> getRestApiDocs() {
    return restApiDocs;
  }

  @Override
  protected SafeFuture<?> doStart() {
    try {
      passwordPath.ifPresent(this::checkAccessFile);
      app.start();
      LOG.info("Listening on {}", app.jettyServer().server().getURI());
    } catch (final RuntimeException e) {
      if (e instanceof JavalinBindException) {
        // The message in JavalinBindException has the port number in conflict
        throw new InvalidConfigurationException(e.getMessage());
      } else if (e instanceof IllegalStateException
          || Throwables.getRootCause(e) instanceof IllegalStateException) {
        // IllegalStateException is a sign that something needed has failed to be initialised.
        // throwing it here will terminate the process effectively.
        LOG.error("Failed to start Rest API", e);
        throw e;
      } else if (app.jettyServer() == null || !app.jettyServer().started()) {
        // failing to create the jetty server or start the jetty server is fatal.
        throw new IllegalStateException("Rest API failed to start", e);
      } else {
        // there may be non fatal exceptions, lets at least see an error.
        LOG.error("Error encountered starting Rest API", e);
      }
    }
    return SafeFuture.COMPLETE;
  }

  public int getListenPort() {
    return app.port();
  }

  private void checkAccessFile(final Path path) {
    if (!path.toFile().exists()) {
      try {
        if (!path.getParent().toFile().mkdirs() && !path.getParent().toFile().isDirectory()) {
          LOG.error("Could not mkdirs for file {}", path.toAbsolutePath());
          throw new IllegalStateException(
              String.format("Cannot create directories %s", path.getParent().toAbsolutePath()));
        }
        final Bytes generated = Bytes.random(16);
        LOG.info("Initializing API auth access file {}", path.toAbsolutePath());
        Files.writeString(path, generated.toUnprefixedHexString(), UTF_8);
      } catch (IOException e) {
        LOG.error("Failed to write auth file to {}", path, e);
        throw new IllegalStateException("Failed to initialise access file for validator-api.");
      }
    }
    app.beforeMatched(new AuthorizationHandler(path));
  }

  @Override
  protected SafeFuture<?> doStop() {
    app.stop();
    return SafeFuture.COMPLETE;
  }
}
