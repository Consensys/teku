/*
 * Copyright Consensys Software Inc., 2025
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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;

public class GetInclusionList extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/inclusion_list/{epoch}";

  private final ValidatorDataProvider validatorDataProvider;

  public GetInclusionList(final DataProvider provider, final Spec spec) {
    this(provider.getValidatorDataProvider(), spec);
  }

  public GetInclusionList(final ValidatorDataProvider validatorDataProvider, final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("produceInclusionList")
            .summary("Produce an inclusion list")
            .description("Requests the beacon node to produce an inclusion list")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .pathParam(
                SLOT_PARAMETER.withDescription(
                    "The slot for which an inclusion list should be created."))
            .response(SC_OK, "Request successful", getResponseType(spec))
            .withServiceUnavailableResponse()
            .build());
    this.validatorDataProvider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    if (!validatorDataProvider.isStoreAvailable()) {
      request.respondError(SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE);
      return;
    }

    final UInt64 slot = request.getPathParameter(SLOT_PARAMETER);

    final SafeFuture<Optional<List<Transaction>>> future =
        validatorDataProvider.getInclusionList(slot);

    request.respondAsync(
        future.thenApply(
            maybeTransactions ->
                maybeTransactions
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  private static SerializableTypeDefinition<List<Transaction>> getResponseType(final Spec spec) {
    return SerializableTypeDefinition.<List<Transaction>>object()
        .name("ProduceInclusionListResponse")
        .withField(
            "data",
            listOf(
                spec.getGenesisSchemaDefinitions()
                    .toVersionEip7805()
                    .orElseThrow()
                    .getInclusionListSchema()
                    .getTransactionSchema()
                    .getJsonTypeDefinition()),
            Function.identity())
        .build();
  }
}
