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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProofSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class PostContributionAndProofs extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/validator/contribution_and_proofs";
  private final ValidatorDataProvider provider;

  public PostContributionAndProofs(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostContributionAndProofs(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postContributionAndProofs")
            .summary("Publish contribution and proofs")
            .description(
                "Verifies given sync committee contribution and proofs and publishes on appropriate gossipsub topics.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(getRequestType(schemaDefinitionCache)))
            .response(SC_OK, "Successful response")
            .build());
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Publish contribution and proofs",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(
              content = {
                @OpenApiContent(
                    from = tech.pegasys.teku.api.schema.altair.SignedContributionAndProof.class,
                    isArray = true)
              }),
      description =
          "Verifies given sync committee contribution and proofs and publishes on appropriate gossipsub topics.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "Successfully published contribution and proofs."),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied."),
        @OpenApiResponse(status = RES_INTERNAL_ERROR, description = "Beacon node internal error.")
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final SafeFuture<Void> future = provider.sendContributionAndProofs(request.getRequestBody());
    request.respondAsync(future.thenApply(v -> AsyncApiResponse.respondWithCode(SC_OK)));
  }

  private static DeserializableTypeDefinition<SignedContributionAndProof> getRequestType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SignedContributionAndProofSchema typeDefinition =
        SchemaDefinitionsAltair.required(
                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ALTAIR))
            .getSignedContributionAndProofSchema();

    return typeDefinition.getJsonTypeDefinition();
  }
}
