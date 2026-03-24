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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class PostExecutionPayloadEnvelope extends RestApiEndpoint {

    public static final String ROUTE = "/eth/v1/beacon/execution_payload_envelope";
    private final NodeDataProvider nodeDataProvider;

    public PostExecutionPayloadEnvelope(
      final NodeDataProvider provider, final SchemaDefinitionCache schemaCache) {
        super(createEndpointMetadata(schemaCache));
        this.nodeDataProvider = provider;
    }

    private static EndpointMetadata createEndpointMetadata(final SchemaDefinitionCache schemaCache) {
        return EndpointMetadata.post(ROUTE)
        .operationId("publishExecutionPayloadEnvelope")
        .summary("Publish signed execution payload envelope")
        .description("Instructs the beacon node to broadcast a signed execution payload envelope to the network,\n" + 
                        "    to be gossiped for payload validation.")
        .tags(TAG_BEACON)
        .tags(TAG_VALIDATOR_REQUIRED)
        .requestBodyType(DeserializableTypeDefinition.listOf(schemaCache.getSchemaDefinition(SpecMilestone.GLOAS).toVersionGloas().orElseThrow().getSignedExecutionPayloadEnvelopeSchema().getJsonTypeDefinition()))
        .response(SC_OK, "Success")
        .response(SC_BAD_REQUEST, "Invalid request")
        .build();
    }

    @Override
    public void handleRequest(RestApiRequest request) throws JsonProcessingException {
        final List<SignedExecutionPayloadEnvelope> signedExecutionPayloadEnvelopes = request.getRequestBody();
    }
    
}
