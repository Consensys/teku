/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.services.remotevalidator;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import java.io.IOException;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.api.StatusCode;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.services.remotevalidator.RemoteValidatorSubscriptions.SubscriptionStatus;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class RemoteValidatorApi {

  private static final Logger LOG = LogManager.getLogger();

  private final RemoteValidatorSubscriptions subscriptionManager;
  private final Javalin app;
  private final JsonProvider jsonProvider = new JsonProvider();

  public RemoteValidatorApi(
      final TekuConfiguration configuration,
      final RemoteValidatorSubscriptions subscriptionManager) {
    checkNotNull(configuration, "TekuConfiguration can't be null");
    checkNotNull(subscriptionManager, "RemoteValidatorSubscriptions can't be null");

    this.subscriptionManager = subscriptionManager;

    this.app =
        Javalin.create(
            config -> {
              config.defaultContentType = "application/json";
              config.logIfServerNotStarted = false;
              config.showJavalinBanner = false;
            });

    configureServer(configuration);
  }

  private void configureServer(final TekuConfiguration configuration) {
    Objects.requireNonNull(this.app.server())
        .setServerHost(configuration.getRemoteValidatorApiInterface());
    Objects.requireNonNull(this.app.server())
        .setServerPort(configuration.getRemoteValidatorApiPort());

    app.ws(
        "/",
        (ws) -> {
          ws.onConnect(this::subscribeValidator);

          ws.onClose(this::unsubscribeValidator);

          /*
           The server should not receive any messages from the remote validators
          */
          ws.onMessage(this::unsubscribeValidator);
        });
  }

  @VisibleForTesting
  void subscribeValidator(final WsConnectContext handler) {
    final SubscriptionStatus subscriptionStatus =
        subscriptionManager.subscribe(
            handler.getSessionId(),
            (msg) -> {
              try {
                final String json = jsonProvider.objectToJSON(msg);
                handler.session.getRemote().sendString(json);
              } catch (IOException e) {
                LOG.error("Error sending msg to validator {}", handler.getSessionId(), e);
                handler.session.close(
                    StatusCode.SERVER_ERROR, "Unexpected error on Remote Validator server");
              }
            });

    if (!subscriptionStatus.hasSubscribed()) {
      handler.session.close(StatusCode.NORMAL, subscriptionStatus.getInfo());
    }
  }

  @VisibleForTesting
  void unsubscribeValidator(final WsContext handler) {
    handler.session.close();
    subscriptionManager.unsubscribe(handler.getSessionId());
  }

  public void start() {
    app.start();
  }

  public void stop() {
    app.stop();
  }
}
