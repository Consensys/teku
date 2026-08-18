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
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.ETH_HEADER_EXECUTION_PAYLOAD_VALUE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.blockContainerAndMetaDataSszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_INCLUDE_PAYLOAD;
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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.gloas.BlockContentsGloas;
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

              When self-building (local execution payload), the response includes the full block contents
              (beacon block, execution payload envelope, blobs, and KZG proofs) if `include_payload` is
              set to `true`, otherwise only the `BeaconBlock` is returned.
              When using an external builder bid, only the `BeaconBlock` is returned as the beacon node
              does not have access to the builder's execution payload.

              The `Eth-Execution-Payload-Included` header and `execution_payload_included` response field
              indicate which response type was returned.
              """)
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
        .queryParamRequired(RANDAO_PARAMETER)
        .queryParam(GRAFFITI_PARAMETER)
        .queryParamAllowsEmpty(SKIP_RANDAO_VERIFICATION_PARAMETER)
        .queryParamRequired(INCLUDE_PAYLOAD_PARAMETER)
        .queryParam(BUILDER_BOOST_FACTOR_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            getResponseType(schemaDefinitionCache),
            blockContainerAndMetaDataSszResponseType(),
            getHeaders())
        .withChainDataResponses()
        .withNotAcceptableResponse()
        .build();
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot =
        request.getPathParameter(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION));
    final BLSSignature randao = request.getQueryParameter(RANDAO_PARAMETER);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(GRAFFITI_PARAMETER);
    final boolean includePayload = request.getQueryParameter(INCLUDE_PAYLOAD_PARAMETER);
    final Optional<UInt64> requestedBuilderBoostFactor =
        request.getOptionalQueryParameter(BUILDER_BOOST_FACTOR_PARAMETER);

    if (validatorDataProvider.getMilestoneAtSlot(slot).isLessThan(SpecMilestone.GLOAS)) {
      request.respondError(SC_BAD_REQUEST, "produceBlockV4 is only supported from Gloas onwards");
      return;
    }

    final long requestTimeMs = System.currentTimeMillis();
    LOG.debug(
        "produceBlockV4 requested: slot={}, include_payload={}, builder_boost_factor={}, timestampMs={}",
        slot,
        includePayload,
        requestedBuilderBoostFactor,
        requestTimeMs);

    final SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorDataProvider.produceBlock(slot, randao, graffiti, requestedBuilderBoostFactor);

    request.respondAsync(
        result.thenApply(
            maybeBlock ->
                maybeBlock
                    .map(
                        blockContainerAndMetaData -> {
                          final boolean selfBuilt =
                              blockContainerAndMetaData.blockContainer()
                                  instanceof BlockContentsGloas;
                          // include_payload=true and self-built → include full contents
                          // include_payload=false or builder bid → return beacon block only
                          final boolean executionPayloadIncluded = selfBuilt && includePayload;
                          final BlockContainerAndMetaData responseMetaData =
                              executionPayloadIncluded
                                  ? blockContainerAndMetaData
                                  : blockContainerAndMetaData.withBlockContents(
                                      blockContainerAndMetaData.blockContainer().getBlock());
                          final long responseTimeMs = System.currentTimeMillis();
                          LOG.debug(
                              "produceBlockV4 response: slot={}, execution_payload_included={}, consensus_block_value={}, execution_payload_value={}, timestampMs={}, elapsedMs={}",
                              slot,
                              executionPayloadIncluded,
                              responseMetaData.consensusBlockValue().toDecimalString(),
                              responseMetaData.executionPayloadValue().toDecimalString(),
                              responseTimeMs,
                              responseTimeMs - requestTimeMs);
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              responseMetaData.specMilestone().lowerCaseName());
                          request.header(
                              HEADER_CONSENSUS_BLOCK_VALUE,
                              responseMetaData.consensusBlockValue().toDecimalString());
                          request.header(
                              HEADER_EXECUTION_PAYLOAD_VALUE,
                              responseMetaData.executionPayloadValue().toDecimalString());
                          request.header(
                              HEADER_INCLUDE_PAYLOAD, Boolean.toString(executionPayloadIncluded));
                          return AsyncApiResponse.respondOk(responseMetaData);
                        })
                    .orElseGet(
                        () ->
                            AsyncApiResponse.respondWithError(
                                SC_INTERNAL_SERVER_ERROR, "Unable to produce a block"))));
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
        .withField(
            EXECUTION_PAYLOAD_VALUE, UINT256_TYPE, BlockContainerAndMetaData::executionPayloadValue)
        .withField(
            INCLUDE_EXECUTION_PAYLOAD,
            BOOLEAN_TYPE,
            blockContainerAndMetaData ->
                blockContainerAndMetaData.blockContainer() instanceof BlockContentsGloas)
        .withField("data", blockContainerType, BlockContainerAndMetaData::blockContainer)
        .build();
  }

  private static List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
      generateBlockContainerSchemaGetters(final SchemaDefinitionCache schemaDefinitionCache) {
    final List<MilestoneDependentTypesUtil.ConditionalSchemaGetter<BlockContainer>>
        schemaGetterList = new ArrayList<>();

    // Self-built local block: beacon block + envelope + blobs + KZG proofs
    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)
                    && blockContainer instanceof BlockContentsGloas,
            SpecMilestone.GLOAS,
            SchemaDefinitions::getBlockContainerSchema));

    // External builder bid: beacon block only (builder holds the payload)
    schemaGetterList.add(
        new MilestoneDependentTypesUtil.ConditionalSchemaGetter<>(
            (blockContainer, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()).equals(milestone)
                    && milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)
                    && !(blockContainer instanceof BlockContentsGloas),
            SpecMilestone.GLOAS,
            schemaDefinitions ->
                schemaDefinitions.getBeaconBlockSchema().castTypeToBlockContainer()));
    return schemaGetterList;
  }

  private static List<SerializableTypeDefinition<?>> getHeaders() {
    List<SerializableTypeDefinition<?>> headers = new ArrayList<>();
    headers.add(ETH_CONSENSUS_HEADER_TYPE);
    headers.add(ETH_HEADER_CONSENSUS_BLOCK_VALUE_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_VALUE_TYPE);
    headers.add(ETH_HEADER_EXECUTION_PAYLOAD_INCLUDED_TYPE);
    return headers;
  }
}
