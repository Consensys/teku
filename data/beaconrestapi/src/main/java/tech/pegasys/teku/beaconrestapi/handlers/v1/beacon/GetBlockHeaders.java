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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOCK_HEADER_TYPE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARENT_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetBlockHeaders extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/headers";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<BlockHeadersResponse> RESPONSE_TYPE =
      SerializableTypeDefinition.object(BlockHeadersResponse.class)
          .name("GetBlockHeadersResponse")
          .withField(
              EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, BlockHeadersResponse::isExecutionOptimistic)
          .withField(FINALIZED, BOOLEAN_TYPE, BlockHeadersResponse::isFinalized)
          .withField("data", listOf(BLOCK_HEADER_TYPE), BlockHeadersResponse::getData)
          .build();

  public GetBlockHeaders(final DataProvider dataProvider) {
    this(dataProvider.getChainDataProvider());
  }

  public GetBlockHeaders(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getBlockHeaders")
            .summary("Get block headers")
            .description(
                "Retrieves block headers matching given query. By default it will fetch current head slot blocks.")
            .tags(TAG_BEACON)
            .queryParam(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION))
            .queryParam(PARENT_ROOT_PARAMETER)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final Optional<Bytes32> parentRoot = request.getOptionalQueryParameter(PARENT_ROOT_PARAMETER);
    final Optional<UInt64> slot =
        request.getOptionalQueryParameter(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION));

    final SafeFuture<BlockHeadersResponse> future =
        chainDataProvider.getBlockHeaders(parentRoot, slot);

    request.respondAsync(
        future
            .thenApplyChecked(AsyncApiResponse::respondOk)
            .exceptionallyCompose(
                error -> {
                  final Throwable rootCause = Throwables.getRootCause(error);
                  if (rootCause instanceof IllegalArgumentException) {
                    return SafeFuture.of(
                        () ->
                            AsyncApiResponse.respondWithError(SC_BAD_REQUEST, error.getMessage()));
                  }
                  return SafeFuture.failedFuture(error);
                }));
  }
}
