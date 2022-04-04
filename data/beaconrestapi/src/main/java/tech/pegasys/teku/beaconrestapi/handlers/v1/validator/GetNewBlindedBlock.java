/*
 * Copyright 2021 ConsenSys AG.
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

import static tech.pegasys.teku.beaconrestapi.EthereumTypes.SIGNATURE_TYPE;
import static tech.pegasys.teku.beaconrestapi.handlers.AbstractHandler.routeWithBracedParameters;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.GRAFFITI;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.HttpMethod;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetNewBlindedBlockResponse;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.beaconrestapi.SchemaDefinitionCache;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

@SuppressWarnings("unused")
public class GetNewBlindedBlock extends MigratingEndpointAdapter {
  private static final String OAPI_ROUTE = "/eth/v1/validator/blinded_blocks/:slot";
  public static final String ROUTE = routeWithBracedParameters(OAPI_ROUTE);
  private final ValidatorDataProvider provider;

  private static final ParameterMetadata<UInt64> PARAM_SLOT =
      new ParameterMetadata<>(
          SLOT,
          CoreTypes.UINT64_TYPE.withDescription(
              "The slot for which the block should be proposed."));

  private static final ParameterMetadata<BLSSignature> PARAM_RANDAO =
      new ParameterMetadata<>(
          RestApiConstants.RANDAO_REVEAL,
          SIGNATURE_TYPE.withDescription(
              "`BLSSignature Hex` BLS12-381 signature for the current epoch."));

  private static final ParameterMetadata<Bytes32> PARAM_GRAFFITI =
      new ParameterMetadata<>(
          GRAFFITI, CoreTypes.BYTES32_TYPE.withDescription("`Bytes32 Hex` Graffiti."));
  private static final DeserializableTypeDefinition<SpecMilestone> SPEC_VERSION =
      DeserializableTypeDefinition.enumOf(SpecMilestone.class);

  public GetNewBlindedBlock(
      final DataProvider dataProvider, final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), schemaDefinitionCache);
  }

  public GetNewBlindedBlock(
      final ValidatorDataProvider provider, final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(schemaDefinitionCache));
    this.provider = provider;
  }

  @OpenApi(
      path = OAPI_ROUTE,
      method = HttpMethod.GET,
      summary = "Produce unsigned blinded block",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED, TAG_EXPERIMENTAL},
      description =
          "Requests a beacon node to produce a valid blinded block, which can then be signed by a validator. "
              + "A blinded block is a block with only a transactions root, rather than a full transactions list.\n\n"
              + "Metadata in the response indicates the type of block produced, and the supported types of block "
              + "will be added to as forks progress.\n\n"
              + "Pre-Bellatrix, this endpoint will return a `BeaconBlock`.",
      pathParams = {
        @OpenApiParam(
            name = SLOT,
            description = "The slot for which the block should be proposed."),
      },
      queryParams = {
        @OpenApiParam(
            name = RANDAO_REVEAL,
            description = "`BLSSignature Hex` BLS12-381 signature for the current epoch.",
            required = true),
        @OpenApiParam(name = GRAFFITI, description = "`Bytes32 Hex` Graffiti.")
      },
      responses = {
        @OpenApiResponse(
            status = RES_OK,
            content = @OpenApiContent(from = GetNewBlindedBlockResponse.class)),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getPathParameter(PARAM_SLOT);
    final BLSSignature signature = request.getQueryParameter(PARAM_RANDAO);
    final Optional<Bytes32> grafitti = request.getOptionalQueryParameter(PARAM_GRAFFITI);
    throw new IllegalArgumentException("Not implemented");
  }

  private static EndpointMetadata getEndpointMetaData(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getNewBlindedBlock")
        .summary("Produce unsigned blinded block")
        .description(
            "Requests a beacon node to produce a valid blinded block, which can then be signed by a validator. "
                + "A blinded block is a block with only a transactions root, rather than a full transactions list.\n\n"
                + "Metadata in the response indicates the type of block produced, and the supported types of block "
                + "will be added to as forks progress.\n\n"
                + "Pre-Bellatrix, this endpoint will return a `BeaconBlock`.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED, TAG_EXPERIMENTAL)
        .pathParam(PARAM_SLOT)
        .queryParamRequired(PARAM_RANDAO)
        .queryParam(PARAM_GRAFFITI)
        .response(
            SC_OK,
            "Request successful",
            SerializableTypeDefinition.<BeaconBlock>object()
                .name("GetNewBlindedBlockResponse")
                .withField(
                    "data",
                    getBlindedBlockSchemaDefinition(schemaDefinitionCache),
                    Function.identity())
                .withField(
                    "version",
                    SPEC_VERSION,
                    block -> schemaDefinitionCache.milestoneAtSlot(block.getSlot()))
                .build())
        .build();
  }

  private static SerializableOneOfTypeDefinition<BeaconBlock> getBlindedBlockSchemaDefinition(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableOneOfTypeDefinitionBuilder<BeaconBlock> builder =
        new SerializableOneOfTypeDefinitionBuilder<BeaconBlock>().title("BlindedBlock");
    for (SpecMilestone milestone : SpecMilestone.values()) {
      builder.withType(
          block -> schemaDefinitionCache.milestoneAtSlot(block.getSlot()).equals(milestone),
          schemaDefinitionCache
              .getSchemaDefinition(milestone)
              .getBlindedBeaconBlockSchema()
              .getJsonTypeDefinition());
    }
    return builder.build();
  }
}
