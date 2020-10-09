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

package tech.pegasys.teku.validator.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import java.net.URI;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;

public class WebSocketBeaconChainEventAdapter implements BeaconChainEventAdapter {

  private static final Logger LOG = LogManager.getLogger();

  private final JsonProvider jsonProvider = new JsonProvider();
  private final URI endpoint;
  private final Vertx vertx;
  private final BeaconChainEventMapper beaconChainEventMapper;

  private HttpClient httpClient;

  public WebSocketBeaconChainEventAdapter(final ServiceConfig config) {
    endpoint = URI.create(config.getConfig().getBeaconNodeEventsWsEndpoint());

    beaconChainEventMapper =
        new BeaconChainEventMapper(
            config.getEventChannels().getPublisher(ValidatorTimingChannel.class));
    vertx = Vertx.vertx();
  }

  @Override
  public SafeFuture<Void> start() {
    final SafeFuture<Void> future = new SafeFuture<>();

    httpClient = vertx.createHttpClient();

    httpClient.webSocket(
        endpoint.getPort(),
        endpoint.getHost(),
        endpoint.getPath(),
        ws -> {
          if (ws.succeeded()) {
            LOG.debug("Listening for remote BeaconChain events on {}", endpoint);

            ws.result().textMessageHandler(this::handleTextMessage);

            future.complete(null);
          } else {
            LOG.error("Error connecting to remove validator service", ws.cause());
            future.completeExceptionally(ws.cause());
          }
        });

    return future;
  }

  @Override
  public SafeFuture<Void> stop() {
    final SafeFuture<Void> future = new SafeFuture<>();

    httpClient.close();

    vertx.close(
        r -> {
          if (r.succeeded()) {
            future.complete(null);
          } else {
            future.completeExceptionally(r.cause());
          }
        });

    return future;
  }

  @SuppressWarnings("unchecked")
  private void handleTextMessage(final String msg) {
    final Map<String, Object> event;
    try {
      event = jsonProvider.jsonToObject(msg, Map.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    LOG.trace("Received remote BeaconChain event {}", msg);

    beaconChainEventMapper.map(event);
  }
}
