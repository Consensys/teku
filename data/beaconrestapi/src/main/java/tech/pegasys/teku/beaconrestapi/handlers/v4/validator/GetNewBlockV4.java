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

package tech.pegasys.teku.beaconrestapi.handlers.v4.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BUILDER_BOOST_FACTOR_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.GRAFFITI_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.INCLUDE_PAYLOAD_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.RANDAO_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SKIP_RANDAO_VERIFICATION_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.blockContainerAndMetaDataSszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_INCLUDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_PATH_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT256_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;

public class GetNewBlockV4 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v4/validator/blocks/{slot}";

  protected final ValidatorDataProvider validatorDataProvider;

  public GetNewBlockV4(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetNewBlockV4(
      final ValidatorDataProvider validatorDataProvider,
      @SuppressWarnings("unused") final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData());
    this.validatorDataProvider = validatorDataProvider;
  }

  private static EndpointMetadata getEndpointMetaData() {
    return EndpointMetadata.get(ROUTE)
        .operationId("produceBlockV4")
        .summary("Produce a new block, without signature.")
        .description(
            """
              Requests a beacon node to produce a valid block for post-Gloas (ePBS), which can then be signed by a validator.
              Supports two operational modes controlled by `include_payload`:

              - **Stateless mode** (`include_payload=true`, default): Returns the execution payload envelope and blobs
                inline, suitable for multi-BN operations.
              - **Stateful mode** (`include_payload=false`): The beacon node caches the payload for separate retrieval
                via `/eth/v1/validator/execution_payload_envelope/{slot}/{beacon_block_root}`.
              """)
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
        .queryParamRequired(RANDAO_PARAMETER)
        .queryParam(GRAFFITI_PARAMETER)
        .queryParamAllowsEmpty(SKIP_RANDAO_VERIFICATION_PARAMETER)
        .queryParam(BUILDER_BOOST_FACTOR_PARAMETER)
        .queryParam(INCLUDE_PAYLOAD_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(),
            blockContainerAndMetaDataSszResponseType(),
            getHeaders())
        .withChainDataResponses()
        .withNotImplementedResponse()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondAsync(
        SafeFuture.completedFuture(
            AsyncApiResponse.respondWithError(SC_NOT_IMPLEMENTED, "Not implemented")));
  }

  private static SerializableTypeDefinition<BlockContainerAndMetaData> getResponseType() {
    return SerializableTypeDefinition.<BlockContainerAndMetaData>object()
        .name("ProduceBlockV4Response")
        .withField("version", MILESTONE_TYPE, BlockContainerAndMetaData::specMilestone)
        .withField(EXECUTION_PAYLOAD_INCLUDED, BOOLEAN_TYPE, blockContainerAndMetaData -> false)
        .withField(
            CONSENSUS_BLOCK_VALUE, UINT256_TYPE, BlockContainerAndMetaData::consensusBlockValue)
        .build();
  }

  private static List<SerializableTypeDefinition<?>> getHeaders() {
    List<SerializableTypeDefinition<?>> headers = new ArrayList<>();
    headers.add(ETH_CONSENSUS_HEADER_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE);
    headers.add(ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE);
    return headers;
  }
}
