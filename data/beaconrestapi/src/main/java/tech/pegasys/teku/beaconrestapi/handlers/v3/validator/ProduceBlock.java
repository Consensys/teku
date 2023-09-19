/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.handlers.v3.validator;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.GRAFFITI_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.RANDAO_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SKIP_RANDAO_VERIFICATION_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getMultipleSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_PATH_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class ProduceBlock extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v3/validator/blocks/{slot}";

  protected final ValidatorDataProvider provider;

  public ProduceBlock(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public ProduceBlock(
      final ValidatorDataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(spec, schemaDefinitionCache));
    this.provider = provider;
  }

  private static EndpointMetadata getEndpointMetaData(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("produceBlockV3")
        .summary("Produce a new block, without signature")
        .description(
            "Requests a beacon node to produce a valid block, which can then be signed by a validator. The\n"
                + "returned block may be blinded or unblinded, depending on the current state of the network as\n"
                + "decided by the execution and beacon nodes.\n"
                + "The beacon node must return an unblinded block if it obtains the execution payload from its\n"
                + "paired execution node. It must only return a blinded block if it obtains the execution payload\n"
                + "header from an MEV relay.\n"
                + "Metadata in the response indicates the type of block produced, and the supported types of block\n"
                + "will be added to as forks progress.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
        .queryParamRequired(RANDAO_PARAMETER)
        .queryParam(GRAFFITI_PARAMETER)
        .queryParam(SKIP_RANDAO_VERIFICATION_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            SerializableTypeDefinition.<BlockContainer>object()
                .name("ProduceBlockV3Response")
                .withField(
                    "data",
                    getMultipleSchemaDefinitionForAllSupportedMilestones(
                        schemaDefinitionCache,
                        "Block",
                        (blockContainer, milestone) ->
                            schemaDefinitionCache
                                .milestoneAtSlot(blockContainer.getSlot())
                                .equals(milestone),
                        SchemaDefinitions::getBlockContainerSchema,
                        SchemaDefinitions::getBlindedBlockContainerSchema),
                    Function.identity())
                .withField(
                    "version",
                    MILESTONE_TYPE,
                    blockContainer ->
                        schemaDefinitionCache.milestoneAtSlot(blockContainer.getSlot()))
                .build(),
            sszResponseType(
                blockContainer ->
                    spec.getForkSchedule().getSpecMilestoneAtSlot(blockContainer.getSlot())))
        .build();
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot =
        request.getPathParameter(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION));
    final BLSSignature randao = request.getQueryParameter(RANDAO_PARAMETER);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(GRAFFITI_PARAMETER);
    final SafeFuture<Optional<BlockContainerAndMetaData>> result =
        provider.produceBlock(slot, randao, graffiti);
    request.respondAsync(
        result.thenApply(
            maybeBlock ->
                maybeBlock
                    .map(
                        blockContainer -> {
                          request.header(
                              HEADER_CONSENSUS_VERSION,
                              Version.fromMilestone(blockContainer.getMilestone()).name());
                          request.header(
                              HEADER_EXECUTION_PAYLOAD_BLINDED,
                              Boolean.toString(blockContainer.getData().getBlock().isBlinded()));
                          request.header(HEADER_EXECUTION_PAYLOAD_VALUE, "1234");
                          return AsyncApiResponse.respondOk(blockContainer);
                        })
                    .orElseThrow(ChainDataUnavailableException::new)));
  }
}
