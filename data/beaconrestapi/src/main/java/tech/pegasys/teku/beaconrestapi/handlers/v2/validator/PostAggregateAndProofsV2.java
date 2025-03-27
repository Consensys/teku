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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.headerBasedSelector;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfArrayJsonRequestContentTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostAggregateAndProofsV2 extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/validator/aggregate_and_proofs";
  private final ValidatorDataProvider provider;

  public PostAggregateAndProofsV2(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostAggregateAndProofsV2(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(schemaDefinitionCache));
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
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

  private static EndpointMetadata createMetadata(
      final SchemaDefinitionCache schemaDefinitionCache) {

    final BiPredicate<SignedAggregateAndProof, SpecMilestone>
        signedAggregateAndProofSchemaPredicate =
            (signedAggregateAndProof, milestone) ->
                schemaDefinitionCache
                    .milestoneAtSlot(
                        signedAggregateAndProof.getMessage().getAggregate().getData().getSlot())
                    .equals(milestone);

    final SerializableOneOfTypeDefinition<SignedAggregateAndProof>
        signedAggregateAndProofSchemaDefinition =
            getSchemaDefinitionForAllSupportedMilestones(
                schemaDefinitionCache,
                "SignedAggregateAndProof",
                SchemaDefinitions::getSignedAggregateAndProofSchema,
                signedAggregateAndProofSchemaPredicate);

    final OneOfArrayJsonRequestContentTypeDefinition.BodyTypeSelector<SignedAggregateAndProof>
        aggregateAndProofBodySelector =
            context ->
                headerBasedSelector(
                    context.getHeaders(),
                    schemaDefinitionCache,
                    SchemaDefinitions::getSignedAggregateAndProofSchema);

    return EndpointMetadata.post(ROUTE)
        .operationId("publishAggregateAndProofsV2")
        .summary("Publish multiple aggregate and proofs")
        .description(
            "Verifies given aggregate and proofs and publishes it on appropriate gossipsub topic.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .requestBodyType(
            SerializableTypeDefinition.listOf(signedAggregateAndProofSchemaDefinition),
            aggregateAndProofBodySelector)
        .headerRequired(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "Version of the aggregate and proofs being submitted."))
        .response(SC_OK, "Attestations are stored in pool and broadcast on appropriate subnet")
        .withBadRequestResponse(Optional.of("Invalid request syntax."))
        .withChainDataResponses()
        .build();
  }
}
