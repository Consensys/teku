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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOCK_ROOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.EPOCH_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GetCheckpointState extends RestApiEndpoint {
  public static final String ROUTE =
      "/teku/v1/beacon/retrieve_checkpoint_state/{epoch}/{block_root}";

  private static final Logger LOG = LogManager.getLogger();

  private final ChainDataProvider chainDataProvider;

  public GetCheckpointState(final DataProvider provider) {
    this(provider.getChainDataProvider());
  }

  GetCheckpointState(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getCheckpointState")
            .summary("...")
            .description("...")
            .tags(TAG_EXPERIMENTAL)
            .pathParam(EPOCH_PARAMETER)
            .pathParam(BLOCK_ROOT_PARAMETER)
            .response(SC_OK, "Request successful")
            .withServiceUnavailableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    request.header(CACHE_CONTROL, CACHE_NONE);

    final Checkpoint checkpoint =
        new Checkpoint(
            request.getPathParameter(EPOCH_PARAMETER),
            request.getPathParameter(BLOCK_ROOT_PARAMETER));

    final long start = System.currentTimeMillis();

    LOG.info("request: " + checkpoint);

    request.respondAsync(
        chainDataProvider
            .retrieveCheckpointState(checkpoint)
            .thenApply(
                state -> {
                  LOG.info(
                      "response: state at slot {}, time {} ms",
                      state.map(BeaconState::getSlot),
                      System.currentTimeMillis() - start);
                  return AsyncApiResponse.respondWithCode(SC_OK);
                }));
  }
}
