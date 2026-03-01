/*
 * Copyright Consensys Software Inc., 2026
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
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BUILDER_INDEX;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetExecutionPayloadBid extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/execution_payload_bid/{slot}/{builder_index}";

  public GetExecutionPayloadBid(final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getExecutionPayloadBid")
            .summary("Get execution payload bid")
            .description("Retrieves execution payload bid for a given slot and builder.")
            .tags(TAG_VALIDATOR)
            .pathParam(
                SLOT_PARAMETER.withDescription(
                    "Slot for which the execution payload bid is requested. Must be current slot or next slot."))
            .pathParam(
                PARAMETER_BUILDER_INDEX.withDescription(
                    "Index of the builder from which the execution payload bid is requested."))
            .response(
                SC_OK,
                "Request successful",
                SerializableTypeDefinition.object(ExecutionPayloadBid.class)
                    .name("GetExecutionPayloadBidResponse")
                    .withField(
                        "data",
                        SchemaDefinitionsGloas.required(
                                schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS))
                            .getExecutionPayloadBidSchema()
                            .getJsonTypeDefinition(),
                        Function.identity())
                    .build())
            .withNotFoundResponse()
            .withNotAcceptedResponse()
            .withChainDataResponses()
            .build());
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondError(SC_NOT_IMPLEMENTED, "Not implemented");
  }
}
