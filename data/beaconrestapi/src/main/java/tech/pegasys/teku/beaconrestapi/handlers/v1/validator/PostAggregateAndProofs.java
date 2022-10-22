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

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostAggregateAndProofs extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/aggregate_and_proofs";
  private final ValidatorDataProvider provider;

  public PostAggregateAndProofs(
      final DataProvider provider, final SchemaDefinitions schemaDefinitions) {
    this(provider.getValidatorDataProvider(), schemaDefinitions);
  }

  public PostAggregateAndProofs(
      final ValidatorDataProvider provider, final SchemaDefinitions schemaDefinitions) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postAggregateAndProofs")
            .summary("Publish aggregate and proofs")
            .description(
                "Verifies given aggregate and proofs and publishes it on appropriate gossipsub topic.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(
                    schemaDefinitions.getSignedAggregateAndProofSchema().getJsonTypeDefinition()))
            .response(SC_OK, "Successfully published aggregate.")
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final List<SignedAggregateAndProof> signedAggregateAndProofs = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future =
        provider.sendAggregateAndProofs(signedAggregateAndProofs);

    request.respondAsync(
        future.thenApply(
            errors -> {
              if (errors.isEmpty()) {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
              return AsyncApiResponse.respondWithError(
                  SC_BAD_REQUEST, PARTIAL_PUBLISH_FAILURE_MESSAGE);
            }));
  }
}
