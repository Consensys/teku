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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.migrated.ValidatorLivenessRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostValidatorLiveness extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/liveness";

  private static final SerializableTypeDefinition<List<ValidatorLivenessAtEpoch>> RESPONSE_TYPE =
      SerializableTypeDefinition.<List<ValidatorLivenessAtEpoch>>object()
          .name("PostValidatorLivenessResponse")
          .withField(
              "data", listOf(ValidatorLivenessAtEpoch.getJsonTypeDefinition()), Function.identity())
          .build();

  public final ChainDataProvider chainDataProvider;
  public final NodeDataProvider nodeDataProvider;
  public final SyncDataProvider syncDataProvider;

  public PostValidatorLiveness(final DataProvider provider) {
    this(
        provider.getChainDataProvider(),
        provider.getNodeDataProvider(),
        provider.getSyncDataProvider());
  }

  public PostValidatorLiveness(
      final ChainDataProvider chainDataProvider,
      final NodeDataProvider nodeDataProvider,
      final SyncDataProvider syncDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postValidatorLiveness")
            .summary("Get Validator Liveness")
            .description(
                "Requests the beacon node to indicate if a validator has been"
                    + "observed to be live in a given epoch. The beacon node might detect liveness by"
                    + "observing messages from the validator on the network, in the beacon chain,"
                    + "from its API or from any other source. It is important to note that the"
                    + "values returned by the beacon node are not canonical; they are best-effort"
                    + "and based upon a subjective view of the network.")
            .tags(TAG_EXPERIMENTAL)
            .requestBodyType(ValidatorLivenessRequest.getJsonTypeDefinition())
            .response(SC_OK, "Successful Response", RESPONSE_TYPE)
            .withServiceUnavailableResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
    this.nodeDataProvider = nodeDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    if (!chainDataProvider.isStoreAvailable() || syncDataProvider.isSyncing()) {
      throw new ServiceUnavailableException();
    }

    final ValidatorLivenessRequest requestBody = request.getRequestBody();
    SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> future =
        nodeDataProvider.getValidatorLiveness(
            requestBody.getIndices(), requestBody.getEpoch(), chainDataProvider.getCurrentEpoch());

    request.respondAsync(
        future.thenApply(
            validatorLivenessAtEpoches ->
                validatorLivenessAtEpoches
                    .map(AsyncApiResponse::respondOk)
                    .orElse(AsyncApiResponse.respondServiceUnavailable())));
  }
}
