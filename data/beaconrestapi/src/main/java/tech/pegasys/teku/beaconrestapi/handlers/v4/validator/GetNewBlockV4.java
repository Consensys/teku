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
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionFromMilestone;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_CONSENSUS_HEADER_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.blockContainerAndMetaDataSszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.INCLUDE_EXECUTION_PAYLOAD;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_PATH_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT256_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class GetNewBlockV4 extends RestApiEndpoint {
  private static final Logger LOG = LoggerFactory.getLogger(GetNewBlockV4.class);

  public static final String ROUTE = "/eth/v4/validator/blocks/{slot}";

  protected final ValidatorDataProvider validatorDataProvider;

  public GetNewBlockV4(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetNewBlockV4(
      final ValidatorDataProvider validatorDataProvider,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(schemaDefinitionCache));
    this.validatorDataProvider = validatorDataProvider;
  }

  private static EndpointMetadata getEndpointMetaData(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("produceBlockV4")
        .summary("Produce a new block, without signature.")
        .description(
            """
              Requests a beacon node to produce a valid block, which can then be signed by a validator.

              Post-Gloas, proposers submit execution payload bids rather than full execution payloads,
              so there is no longer a concept of blinded or unblinded blocks. Builders release the
              payload later. This endpoint is specific to the post-Gloas forks and is not backwards compatible
              with previous forks.

              When self-building (local execution payload), the response will include the full block contents
              including the beacon block, execution payload envelope, blobs, and KZG proofs.
              When using an external builder bid, only the `BeaconBlock` is returned as the beacon node
              does not have access to the builder's execution payload.

              The `Eth-Execution-Payload-Included` header and `execution_payload_included` response field
              indicate which response type was returned.
              """)
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
        .queryParamRequired(RANDAO_PARAMETER)
        .queryParamAllowsEmpty(GRAFFITI_PARAMETER)
        .queryParamAllowsEmpty(SKIP_RANDAO_VERIFICATION_PARAMETER)
        .queryParamAllowsEmpty(INCLUDE_PAYLOAD_PARAMETER)
        .queryParam(BUILDER_BOOST_FACTOR_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(schemaDefinitionCache),
            blockContainerAndMetaDataSszResponseType(),
            getHeaders())
        .withChainDataResponses()
        .withNotAcceptableResponse()
        .withNotImplementedResponse()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot =
        request.getPathParameter(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION));
    final BLSSignature randao = request.getQueryParameter(RANDAO_PARAMETER);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(GRAFFITI_PARAMETER);
    final Optional<Boolean> includePayload =
        request.getOptionalQueryParameter(INCLUDE_PAYLOAD_PARAMETER);
    final Optional<UInt64> requestedBuilderBoostFactor =
        request.getOptionalQueryParameter(BUILDER_BOOST_FACTOR_PARAMETER);

    LOG.debug(
        "Parsed parameters: slot={}, randao={}, graffiti={}, include_payload={}, builder_boost_factor={}",
        slot,
        randao,
        graffiti,
        includePayload,
        requestedBuilderBoostFactor);
    request.respondError(SC_NOT_IMPLEMENTED, ROUTE + " is not implemented");
  }

  private static SerializableTypeDefinition<BlockContainerAndMetaData> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {

    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>> schemaGetters =
        generateBlockContainerSchemaGetters(schemaDefinitionCache);

    final SerializableTypeDefinition<BlockContainer> blockContainerType =
        getMultipleSchemaDefinitionFromMilestone(schemaDefinitionCache, "Block", schemaGetters);

    return SerializableTypeDefinition.<BlockContainerAndMetaData>object()
        .name("ProduceBlockV4Response")
        .withField("version", MILESTONE_TYPE, BlockContainerAndMetaData::specMilestone)
        .withField(
            CONSENSUS_BLOCK_VALUE, UINT256_TYPE, BlockContainerAndMetaData::consensusBlockValue)
        .withOptionalField(INCLUDE_EXECUTION_PAYLOAD, BOOLEAN_TYPE, (b) -> Optional.of(false))
        .withField("data", blockContainerType, BlockContainerAndMetaData::blockContainer)
        .build();
  }

  private static List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
      generateBlockContainerSchemaGetters(final SchemaDefinitionCache schemaDefinitionCache) {
    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
        schemaGetterList = new ArrayList<>();

    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && !blockContainer.isBlinded(),
            SpecMilestone.PHASE0,
            SchemaDefinitions::getBlockContainerSchema));

    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && milestone.isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)
                    && blockContainer.isBlinded(),
            SpecMilestone.BELLATRIX,
            SchemaDefinitions::getBlindedBlockContainerSchema));
    return schemaGetterList;
  }

  private static List<SerializableTypeDefinition<?>> getHeaders() {
    List<SerializableTypeDefinition<?>> headers = new ArrayList<>();
    headers.add(ETH_CONSENSUS_HEADER_TYPE);
    headers.add(ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE);
    return headers;
  }
}
