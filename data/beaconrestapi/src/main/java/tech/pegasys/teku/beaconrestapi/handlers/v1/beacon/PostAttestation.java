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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostAttestation extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/beacon/pool/attestations";
  private final ValidatorDataProvider provider;

  private static final String PARTIAL_PUBLISH_FAILURE_MESSAGE =
      "Some items failed to publish, refer to errors for details";

  private static final SerializableTypeDefinition<List<SubmitDataError>> BAD_REQUEST_RESPONSE =
      SerializableTypeDefinition.<List<SubmitDataError>>object()
          .name("PostDataFailureResponse")
          .withField("code", INTEGER_TYPE, (__) -> SC_BAD_REQUEST)
          .withField("message", STRING_TYPE, (__) -> PARTIAL_PUBLISH_FAILURE_MESSAGE)
          .withField(
              "failures", listOf(SubmitDataError.getJsonTypeDefinition()), Function.identity())
          .build();

  public PostAttestation(
      final DataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public PostAttestation(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postAttestation")
            .summary("Submit signed attestations")
            .description(
                "Submit signed attestations to the beacon node to be validated and submitted if valid.\n\n"
                    + "This endpoint does not protected against slashing.")
            .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
            .requestBodyType(
                DeserializableTypeDefinition.listOf(
                    schemaDefinitionCache
                        .getSchemaDefinition(SpecMilestone.PHASE0)
                        .getAttestationSchema()
                        .getJsonTypeDefinition()))
            .response(SC_OK, "Attestations are stored in pool and broadcast on appropriate subnet")
            .response(SC_BAD_REQUEST, "Errors with one or more attestations", BAD_REQUEST_RESPONSE)
            .build());
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Submit signed attestations",
      tags = {TAG_BEACON, TAG_VALIDATOR_REQUIRED},
      requestBody =
          @OpenApiRequestBody(
              content = {
                @OpenApiContent(
                    from = tech.pegasys.teku.api.schema.Attestation.class,
                    isArray = true)
              }),
      description =
          "Submit signed attestations to the beacon node to be validated and submitted if valid.\n\n"
              + "This endpoint does not protected against slashing.",
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description = "The Attestation was accepted, validated, and submitted"),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description = "Errors with one or more sync committee messages",
            content = @OpenApiContent(from = PostDataFailureResponse.class)),
        @OpenApiResponse(status = RES_INTERNAL_ERROR)
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final List<Attestation> attestations = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future = provider.submitAttestations(attestations);

    request.respondAsync(
        future.thenApply(
            submitDataErrorList -> {
              if (submitDataErrorList.isEmpty()) {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
              return AsyncApiResponse.respondWithObject(SC_BAD_REQUEST, submitDataErrorList);
            }));
  }
}
