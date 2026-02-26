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

import com.google.common.base.Throwables;
import io.javalin.Javalin;
import io.javalin.util.JavalinBindException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.Service;

public class RestApi extends Service {
  private static final Logger LOG = LogManager.getLogger();
  private final Javalin app;
  private final Optional<String> restApiDocs;

  public RestApi(final Javalin app, final Optional<String> restApiDocs) {
    this.app = app;
    this.restApiDocs = restApiDocs;
  }

  public Optional<String> getRestApiDocs() {
    return restApiDocs;
  }

  @Override
  protected SafeFuture<?> doStart() {
    try {
      app.start();
      LOG.info("Listening on port {}", app.port());
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
      } else {
        // Any other exception during startup is fatal
        throw new IllegalStateException("Rest API failed to start", e);
      }
    }
    return SafeFuture.COMPLETE;
  }

  public int getListenPort() {
    return app.port();
  }

  @Override
  protected SafeFuture<?> doStop() {
    app.stop();
    return SafeFuture.COMPLETE;
  }
}
