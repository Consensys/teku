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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.GRAFFITI_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.RANDAO_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllMilestones;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.MILESTONE_TYPE;
import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_PATH_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

@SuppressWarnings("unused")
public class GetNewBlindedBlock extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/blinded_blocks/{slot}";
  private final ValidatorDataProvider provider;

  public GetNewBlindedBlock(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public GetNewBlindedBlock(
      final ValidatorDataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(spec, schemaDefinitionCache));
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot =
        request.getPathParameter(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION));
    final BLSSignature randao = request.getQueryParameter(RANDAO_PARAMETER);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(GRAFFITI_PARAMETER);
    final SafeFuture<Optional<BeaconBlock>> result =
        provider.getUnsignedBeaconBlockAtSlot(slot, randao, graffiti, true);
    request.respondAsync(
        result.thenApplyChecked(
            maybeBlock ->
                maybeBlock
                    .map(AsyncApiResponse::respondOk)
                    .orElseThrow(ChainDataUnavailableException::new)));
  }

  private static EndpointMetadata getEndpointMetaData(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getNewBlindedBlock")
        .summary("Produce unsigned blinded block")
        .description(
            "Requests a beacon node to produce a valid blinded block, which can then be signed by a validator. "
                + "A blinded block is a block with only a transactions root, rather than a full transactions list.\n\n"
                + "Metadata in the response indicates the type of block produced, and the supported types of block "
                + "will be added to as forks progress.\n\n"
                + "Pre-Bellatrix, this endpoint will return a `BeaconBlock`.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
        .queryParamRequired(RANDAO_PARAMETER)
        .queryParam(GRAFFITI_PARAMETER)
        .response(
            SC_OK,
            "Request successful",
            SerializableTypeDefinition.<BeaconBlock>object()
                .name("GetNewBlindedBlockResponse")
                .withField(
                    "data",
                    getSchemaDefinitionForAllMilestones(
                        schemaDefinitionCache,
                        "BlindedBlock",
                        SchemaDefinitions::getBlindedBeaconBlockSchema,
                        (beaconBlock, milestone) ->
                            schemaDefinitionCache
                                .milestoneAtSlot(beaconBlock.getSlot())
                                .equals(milestone)),
                    Function.identity())
                .withField(
                    "version",
                    MILESTONE_TYPE,
                    block -> schemaDefinitionCache.milestoneAtSlot(block.getSlot()))
                .build(),
            sszResponseType(
                block -> spec.getForkSchedule().getSpecMilestoneAtSlot(block.getSlot())))
        .build();
  }
}
