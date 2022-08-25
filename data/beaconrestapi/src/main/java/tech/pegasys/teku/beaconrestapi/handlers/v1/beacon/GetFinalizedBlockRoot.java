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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ROOT_TYPE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetFinalizedBlockRoot extends MigratingEndpointAdapter {

  public static final String ROUTE = "/eth/v1/checkpoint/finalized_blocks/{slot}/root";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<ObjectAndMetaData<Bytes32>> RESPONSE_TYPE =
      SerializableTypeDefinition.<ObjectAndMetaData<Bytes32>>object()
          .name("GetFinalizedBlockRootResponse")
          .withField("data", ROOT_TYPE, ObjectAndMetaData::getData)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, ObjectAndMetaData::isExecutionOptimistic)
          .build();

  public GetFinalizedBlockRoot(DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetFinalizedBlockRoot(ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getFinalizedBlockRoot")
            .summary("Get finalized block root")
            .description(
                "Retrieves hashTreeRoot of finalized BeaconBlock/BeaconBlockHeader.\n"
                    + "Responds with 404 if block at a slot is either unavailable or not yet finalized.")
            .tags(TAG_EXPERIMENTAL)
            .pathParam(SLOT_PARAMETER)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handle(@NotNull Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getPathParameter(SLOT_PARAMETER);
    final SafeFuture<Optional<SignedBeaconBlock>> eventuallyFinalizedBlock =
        chainDataProvider.getFinalizedBlockInEffectAtSlot(slot);
    request.respondAsync(
        eventuallyFinalizedBlock.thenApply(
            maybeFinalizedBlock ->
                maybeFinalizedBlock
                    .map(
                        finalizedBlock -> {
                          if (finalizedBlock.getSlot().equals(slot)) {
                            chainDataProvider
                                .getBlockRoot(slot.toString())
                                .thenApply(
                                    maybeFinalizedBlockRoot ->
                                        maybeFinalizedBlockRoot
                                            .map(AsyncApiResponse::respondOk)
                                            .orElse(AsyncApiResponse.respondNotFound()));
                          }
                          return AsyncApiResponse.respondNotFound();
                        })
                    .orElse(AsyncApiResponse.respondNotFound())));
  }
}
