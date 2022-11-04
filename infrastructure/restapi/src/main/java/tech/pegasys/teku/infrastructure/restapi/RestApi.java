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

import com.google.common.base.Throwables;
import io.javalin.Javalin;
import java.io.IOException;
import java.net.BindException;
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
    } catch (final RuntimeException e) {
      if (Throwables.getRootCause(e) instanceof BindException) {
        throw new InvalidConfigurationException(
            String.format(
                "TCP Port %d is already in use. "
                    + "You may need to stop another process or change the HTTP port for this process.",
                app.port()));
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
    app.updateConfig(config -> config.accessManager(new AuthorizationManager(path)));
  }

  @Override
  protected SafeFuture<?> doStop() {
    app.stop();
    return SafeFuture.COMPLETE;
  }
}
