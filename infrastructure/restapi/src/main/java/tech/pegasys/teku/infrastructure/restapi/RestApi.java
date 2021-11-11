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

import com.google.common.base.Throwables;
import io.javalin.Javalin;
import java.net.BindException;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.Service;

public class RestApi extends Service {
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

  @Override
  protected SafeFuture<?> doStop() {
    app.stop();
    return SafeFuture.COMPLETE;
  }
}
