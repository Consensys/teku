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

package tech.pegasys.teku.beaconrestapi.handlers.v1;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.RES_OK;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_EVENTS;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TOPICS;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.sse.SseClient;
import io.javalin.http.sse.SseHandler;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.provider.JsonProvider;

public class GetEvents implements Handler {
  private static final Logger LOG = LogManager.getLogger();
  public static final String ROUTE = "/eth/v1/events";
  private final JsonProvider jsonProvider;
  private final EventSubscriptionManager eventSubscriptionManager;

  public GetEvents(
      final DataProvider dataProvider,
      final JsonProvider jsonProvider,
      final EventChannels eventChannels) {
    this(dataProvider.getChainDataProvider(), jsonProvider, eventChannels);
  }

  GetEvents(
      final ChainDataProvider provider,
      final JsonProvider jsonProvider,
      final EventChannels eventChannels) {
    this.jsonProvider = jsonProvider;
    eventSubscriptionManager = new EventSubscriptionManager(provider, jsonProvider, eventChannels);
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Subscribe to node events",
      tags = {TAG_EVENTS, TAG_VALIDATOR_REQUIRED},
      description =
          "Provides endpoint to subscribe to beacon node Server-Sent-Events stream. Consumers should use"
              + " [eventsource](https://html.spec.whatwg.org/multipage/server-sent-events.html#the-eventsource-interface)"
              + " implementation to listen on those events.",
      queryParams = {
        @OpenApiParam(
            name = TOPICS,
            required = true,
            description =
                "Event types to subscribe to."
                    + "Available values: head, block, attestation, voluntary_exit, finalized_checkpoint, chain_reorg"),
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(type = "text/event-stream", from = String.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    SseHandler sseHandler = new SseHandler(this::sseEventHandler);
    sseHandler.handle(ctx);
  }

  public void sseEventHandler(final SseClient sseClient) {
    try {
      eventSubscriptionManager.registerClient(sseClient);
    } catch (IllegalArgumentException ex) {
      LOG.trace(ex);
      sseClient.ctx.status(SC_BAD_REQUEST);
      sseClient.ctx.result(getBadRequestString(ex.getMessage()));
    }
  }

  private String getBadRequestString(final String message) {
    try {
      return jsonProvider.objectToJSON(new BadRequest(message));
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
    // manually construct a very basic message
    return "{\"message\": \"" + message + "\"}";
  }
}
