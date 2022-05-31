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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.HTTP_ERROR_RESPONSE_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiRequestBody;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class PostAttesterSlashing extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v1/beacon/pool/attester_slashings";
  private final NodeDataProvider nodeDataProvider;

  public PostAttesterSlashing(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getNodeDataProvider(), spec);
  }

  public PostAttesterSlashing(final NodeDataProvider provider, final Spec spec) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("postAttesterSlashing")
            .summary("Submit attester slashing object")
            .description(
                "Submit signed attestations to the beacon node to be validated and submitted if valid.\n\n"
                    + "This endpoint does not protected against slashing.")
            .tags(TAG_BEACON)
            .requestBodyType(getRequestType(spec.getGenesisSpecConfig()))
            .response(SC_OK, "Success")
            .response(
                SC_BAD_REQUEST,
                "Invalid attester slashing, it will never pass validation so it's rejected",
                HTTP_ERROR_RESPONSE_TYPE)
            .build());
    this.nodeDataProvider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.POST,
      summary = "Submit attester slashing object",
      tags = {TAG_BEACON},
      description =
          "Submits attester slashing object to node's pool and if passes validation node MUST broadcast it to network.",
      requestBody = @OpenApiRequestBody(content = {@OpenApiContent(from = tech.pegasys.teku.api.schema.AttesterSlashing.class)}),
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            description =
                "Attester Slashing has been successfully validated, added to the pool, and broadcast."),
        @OpenApiResponse(
            status = RES_BAD_REQUEST,
            description =
                "Invalid attester slashing, it will never pass validation so it's rejected"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
      })
  @Override
  public void handle(@NotNull final Context ctx) throws Exception {}

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final AttesterSlashing attesterSlashing = request.getRequestBody();
    final InternalValidationResult result =
        nodeDataProvider.postAttesterSlashing(attesterSlashing).join();

    if (result.code().equals(ValidationResultCode.IGNORE)
        || result.code().equals(ValidationResultCode.REJECT)) {
      request.respondError(
          SC_BAD_REQUEST,
          "Invalid attester slashing, it will never pass validation so it's rejected.");
    } else {
      request.respondWithCode(SC_OK);
    }
  }

  private static DeserializableTypeDefinition<AttesterSlashing> getRequestType(
      SpecConfig specConfig) {
    final IndexedAttestation.IndexedAttestationSchema indexedAttestationSchema =
        new IndexedAttestation.IndexedAttestationSchema(specConfig);
    final tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing.AttesterSlashingSchema
        attesterSlashingSchema =
            new tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing
                .AttesterSlashingSchema(indexedAttestationSchema);

    return attesterSlashingSchema.getJsonTypeDefinition();
  }
}
