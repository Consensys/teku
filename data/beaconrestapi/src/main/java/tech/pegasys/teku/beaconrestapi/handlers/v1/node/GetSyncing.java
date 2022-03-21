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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.core.util.Header;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.v1.node.SyncingResponse;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetSyncing extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/node/syncing";

  private static final SerializableTypeDefinition<SyncingStatus> DATA_TYPE =
      SerializableTypeDefinition.object(SyncingStatus.class)
          .withField("head_slot", UINT64_TYPE, SyncingStatus::getCurrentSlot)
          .withField("sync_distance", UINT64_TYPE, GetSyncing::getSlotsBehind)
          .withField("is_syncing", BOOLEAN_TYPE, SyncingStatus::isSyncing)
          .build();

  private static final SerializableTypeDefinition<SyncingStatus> SYNCING_RESPONSE_TYPE =
      SerializableTypeDefinition.object(SyncingStatus.class)
          .name("GetVersionResponse")
          .withField("data", DATA_TYPE, Function.identity())
          .build();

  private final SyncDataProvider syncProvider;

  public GetSyncing(final DataProvider provider) {
    this(provider.getSyncDataProvider());
  }

  GetSyncing(final SyncDataProvider syncProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNodeSyncingStatus")
            .summary("Get node syncing status")
            .description(
                "Requests the beacon node to describe if it's currently syncing or not, "
                    + "and if it is, what block it is up to.")
            .tags(TAG_NODE, TAG_VALIDATOR_REQUIRED)
            .response(SC_OK, "Request successful", SYNCING_RESPONSE_TYPE)
            .response(SC_INTERNAL_SERVER_ERROR, "Internal error")
            .build());
    this.syncProvider = syncProvider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Get node syncing status",
      description =
          "Requests the beacon node to describe if it's currently syncing or not, "
              + "and if it is, what block it is up to.",
      tags = {TAG_NODE, TAG_VALIDATOR_REQUIRED},
      responses = {
        @OpenApiResponse(status = RES_OK, content = @OpenApiContent(from = SyncingResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    ctx.header(Header.CACHE_CONTROL, CACHE_NONE);
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.respondOk(syncProvider.getSyncingStatus());
  }

  private static UInt64 getSlotsBehind(final SyncingStatus syncingStatus) {
    if (syncingStatus.isSyncing() && syncingStatus.getHighestSlot().isPresent()) {
      final UInt64 highestSlot = syncingStatus.getHighestSlot().get();
      return highestSlot.minusMinZero(syncingStatus.getCurrentSlot());
    }
    return UInt64.ZERO;
  }
}
