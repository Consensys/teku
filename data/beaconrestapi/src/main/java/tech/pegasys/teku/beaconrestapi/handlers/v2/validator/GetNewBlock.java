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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static tech.pegasys.teku.beaconrestapi.EthereumTypes.SIGNATURE_TYPE;
import static tech.pegasys.teku.beaconrestapi.EthereumTypes.sszResponseType;
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.GRAFFITI;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_INTERNAL_ERROR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RES_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
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
import tech.pegasys.teku.api.response.v2.validator.GetNewBlockResponseV2;
import tech.pegasys.teku.beaconrestapi.MigratingEndpointAdapter;
import tech.pegasys.teku.beaconrestapi.SchemaDefinitionCache;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

public class GetNewBlock extends MigratingEndpointAdapter {
  public static final String ROUTE = "/eth/v2/validator/blocks/{slot}";

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

  protected final ValidatorDataProvider provider;

  public GetNewBlock(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public GetNewBlock(
      final ValidatorDataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(getEndpointMetaData(spec, schemaDefinitionCache));
    this.provider = provider;
  }

  @OpenApi(
      path = ROUTE,
      method = HttpMethod.GET,
      summary = "Produce unsigned block",
      tags = {TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED},
      description =
          "Requests a beacon node to produce a valid block, which can then be signed by a validator.",
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
            content = {
              @OpenApiContent(from = GetNewBlockResponseV2.class),
              @OpenApiContent(type = OCTET_STREAM)
            }),
        @OpenApiResponse(status = RES_BAD_REQUEST, description = "Invalid parameter supplied"),
        @OpenApiResponse(status = RES_INTERNAL_ERROR),
        @OpenApiResponse(status = RES_SERVICE_UNAVAILABLE, description = SERVICE_UNAVAILABLE)
      })
  @Override
  public void handle(final Context ctx) throws Exception {
    adapt(ctx);
  }

  private static EndpointMetadata getEndpointMetaData(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    return EndpointMetadata.get(ROUTE)
        .operationId("getNewBlock")
        .summary("Produce unsigned block")
        .description(
            "Requests a beacon node to produce a valid block, which can then be signed by a validator.\n"
                + "Metadata in the response indicates the type of block produced, and the supported types of block "
                + "will be added to as forks progress.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .pathParam(PARAM_SLOT)
        .queryParamRequired(PARAM_RANDAO)
        .queryParam(PARAM_GRAFFITI)
        .response(
            SC_OK,
            "Request successful",
            SerializableTypeDefinition.<BeaconBlock>object()
                .name("ProduceBlockV2Response")
                .withField(
                    "data", getBlockSchemaDefinition(schemaDefinitionCache), Function.identity())
                .withField(
                    "version",
                    SPEC_VERSION,
                    block -> schemaDefinitionCache.milestoneAtSlot(block.getSlot()))
                .build(),
            sszResponseType(
                block -> spec.getForkSchedule().getSpecMilestoneAtSlot(block.getSlot())))
        .build();
  }

  @Override
  public void handleRequest(RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getPathParameter(PARAM_SLOT);
    final BLSSignature randao = request.getQueryParameter(PARAM_RANDAO);
    final Optional<Bytes32> graffiti = request.getOptionalQueryParameter(PARAM_GRAFFITI);
    final SafeFuture<Optional<BeaconBlock>> result =
        provider.getUnsignedBeaconBlockAtSlot(slot, randao, graffiti, false);
    request.respondAsync(
        result.thenApply(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                throw new ChainDataUnavailableException();
              }
              return AsyncApiResponse.respondOk(maybeBlock.get());
            }));
  }

  private static SerializableOneOfTypeDefinition<BeaconBlock> getBlockSchemaDefinition(
      final SchemaDefinitionCache schemaDefinitionCache) {
    final SerializableOneOfTypeDefinitionBuilder<BeaconBlock> builder =
        new SerializableOneOfTypeDefinitionBuilder<BeaconBlock>().title("Block");

    for (SpecMilestone milestone : SpecMilestone.values()) {
      builder.withType(
          block -> schemaDefinitionCache.milestoneAtSlot(block.getSlot()).equals(milestone),
          schemaDefinitionCache
              .getSchemaDefinition(milestone)
              .getBeaconBlockSchema()
              .getJsonTypeDefinition());
    }
    return builder.build();
  }
}
