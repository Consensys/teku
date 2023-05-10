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
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class GetNewBlock extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/blocks/{slot}";
  protected final ValidatorDataProvider provider;

  public GetNewBlock(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetNewBlock(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNewBlockV1")
            .summary("Produce unsigned block")
            .description(
                "Requests a beacon node to produce a valid block, which can then be signed by a validator.\n\n"
                    + "__NOTE__: deprecated, switch to using `/eth/v2/validator/blocks/{slot}` for multiple milestone support.")
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .pathParam(SLOT_PARAMETER.withDescription(SLOT_PATH_DESCRIPTION))
            .queryParamRequired(RANDAO_PARAMETER)
            .queryParam(GRAFFITI_PARAMETER)
            .response(SC_OK, "Request successful", getResponseType(schemaDefinitionCache))
            .withChainDataResponses()
            .build());
    this.provider = provider;
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getPathParameter(SLOT_PARAMETER);
    final BLSSignature randao = request.getQueryParameter(RANDAO_PARAMETER);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(GRAFFITI_PARAMETER);

    SafeFuture<? extends Optional<? extends SszData>> future =
        provider.getUnsignedBeaconBlockAtSlot(slot, randao, graffiti);

    request.respondAsync(
        future.thenApply(
            maybeBlock ->
                maybeBlock
                    .map(AsyncApiResponse::respondOk)
                    .orElseThrow(ChainDataUnavailableException::new)));
  }

  private static SerializableTypeDefinition<BeaconBlock> getResponseType(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableTypeDefinition<BeaconBlock> beaconBlockType =
        MilestoneDependentTypesUtil.getSchemaDefinitionForAllMilestones(
            schemaDefinitionCache,
            "BeaconState",
            SchemaDefinitions::getBeaconBlockSchema,
            (beaconBlock, milestone) ->
                schemaDefinitionCache.milestoneAtSlot(beaconBlock.getSlot()).equals(milestone));

    return SerializableTypeDefinition.<BeaconBlock>object()
        .name("ProduceBlockResponse")
        .withField("data", beaconBlockType, Function.identity())
        .build();
  }
}
