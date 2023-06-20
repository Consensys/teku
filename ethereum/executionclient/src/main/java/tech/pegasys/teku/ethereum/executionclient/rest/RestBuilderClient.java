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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static tech.pegasys.teku.ethereum.executionclient.rest.RestClient.NO_HEADERS;
import static tech.pegasys.teku.spec.config.Constants.BUILDER_GET_PAYLOAD_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.BUILDER_PROPOSAL_DELAY_TOLERANCE;
import static tech.pegasys.teku.spec.config.Constants.BUILDER_REGISTER_VALIDATOR_TIMEOUT;
import static tech.pegasys.teku.spec.config.Constants.BUILDER_STATUS_TIMEOUT;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.BuilderApiMethod;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBidSchema;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class RestBuilderClient implements BuilderClient {

  private static final Map<String, String> USER_AGENT_HEADER =
      Map.of(
          "User-Agent",
          VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.IMPLEMENTATION_VERSION);

  private final Map<
          SpecMilestone,
          DeserializableTypeDefinition<? extends BuilderApiResponse<? extends BuilderPayload>>>
      cachedBuilderApiPayloadResponseType = new ConcurrentHashMap<>();

  private final Map<
          SpecMilestone, DeserializableTypeDefinition<BuilderApiResponse<SignedBuilderBid>>>
      cachedBuilderApiSignedBuilderBidResponseType = new ConcurrentHashMap<>();

  private final RestClient restClient;
  private final SchemaDefinitionCache schemaDefinitionCache;
  private final boolean setUserAgentHeader;

  public RestBuilderClient(
      final RestClient restClient, final Spec spec, final boolean setUserAgentHeader) {
    this.restClient = restClient;
    this.schemaDefinitionCache = new SchemaDefinitionCache(spec);
    this.setUserAgentHeader = setUserAgentHeader;
  }

  @Override
  public SafeFuture<Response<Void>> status() {
    return restClient
        .getAsync(BuilderApiMethod.GET_STATUS.getPath())
        .orTimeout(BUILDER_STATUS_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<Void>> registerValidators(
      final UInt64 slot, final SszList<SignedValidatorRegistration> signedValidatorRegistrations) {

    if (signedValidatorRegistrations.isEmpty()) {
      return SafeFuture.completedFuture(Response.withNullPayload());
    }

    final DeserializableTypeDefinition<SszList<SignedValidatorRegistration>> requestType =
        SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition();
    return restClient
        .postAsync(
            BuilderApiMethod.REGISTER_VALIDATOR.getPath(),
            signedValidatorRegistrations,
            requestType)
        .orTimeout(BUILDER_REGISTER_VALIDATOR_TIMEOUT);
  }

  @Override
  public SafeFuture<Response<Optional<SignedBuilderBid>>> getHeader(
      final UInt64 slot, final BLSPublicKey pubKey, final Bytes32 parentHash) {

    final Map<String, String> urlParams = new HashMap<>();
    urlParams.put("slot", slot.toString());
    urlParams.put("parent_hash", parentHash.toHexString());
    urlParams.put("pubkey", pubKey.toBytesCompressed().toHexString());

    final SpecMilestone milestone = schemaDefinitionCache.milestoneAtSlot(slot);

    final DeserializableTypeDefinition<BuilderApiResponse<SignedBuilderBid>>
        responseTypeDefinition =
            cachedBuilderApiSignedBuilderBidResponseType.computeIfAbsent(
                milestone,
                __ -> {
                  final SchemaDefinitionsBellatrix schemaDefinitionsBellatrix =
                      getSchemaDefinitionsBellatrix(milestone);
                  final SignedBuilderBidSchema signedBuilderBidSchema =
                      schemaDefinitionsBellatrix.getSignedBuilderBidSchema();
                  return BuilderApiResponse.createTypeDefinition(
                      signedBuilderBidSchema.getJsonTypeDefinition());
                });

    return restClient
        .getAsync(
            BuilderApiMethod.GET_HEADER.resolvePath(urlParams),
            setUserAgentHeader ? USER_AGENT_HEADER : NO_HEADERS,
            responseTypeDefinition)
        .thenApply(
            response ->
                Response.unwrapVersioned(
                    response,
                    this::extractSignedBuilderBid,
                    milestone,
                    BuilderApiResponse::getVersion,
                    true))
        .thenApply(Response::convertToOptional)
        .orTimeout(BUILDER_PROPOSAL_DELAY_TOLERANCE);
  }

  @Override
  public SafeFuture<Response<BuilderPayload>> getPayload(
      final SignedBlindedBlockContainer signedBlindedBlockContainer) {

    final UInt64 blockSlot = signedBlindedBlockContainer.getSlot();
    final SpecMilestone milestone = schemaDefinitionCache.milestoneAtSlot(blockSlot);

    final SchemaDefinitionsBellatrix schemaDefinitionsBellatrix =
        getSchemaDefinitionsBellatrix(milestone);

    final DeserializableTypeDefinition<SignedBlockContainer> requestTypeDefinition =
        schemaDefinitionsBellatrix.getSignedBlindedBlockContainerSchema().getJsonTypeDefinition();

    final DeserializableTypeDefinition<? extends BuilderApiResponse<? extends BuilderPayload>>
        responseTypeDefinition =
            cachedBuilderApiPayloadResponseType.computeIfAbsent(
                milestone,
                __ -> payloadTypeDefinition(schemaDefinitionsBellatrix.getBuilderPayloadSchema()));

    return restClient
        .postAsync(
            BuilderApiMethod.GET_PAYLOAD.getPath(),
            signedBlindedBlockContainer,
            requestTypeDefinition,
            responseTypeDefinition)
        .thenApply(
            response ->
                Response.unwrapVersioned(
                    response,
                    this::extractBuilderPayload,
                    milestone,
                    BuilderApiResponse::getVersion,
                    false))
        .orTimeout(BUILDER_GET_PAYLOAD_TIMEOUT);
  }

  private <T extends BuilderPayload>
      DeserializableTypeDefinition<BuilderApiResponse<T>> payloadTypeDefinition(
          final BuilderPayloadSchema<T> schema) {
    final DeserializableTypeDefinition<T> typeDefinition = schema.getJsonTypeDefinition();
    return BuilderApiResponse.createTypeDefinition(typeDefinition);
  }

  private <T extends SignedBuilderBid> SignedBuilderBid extractSignedBuilderBid(
      final BuilderApiResponse<T> builderApiResponse) {
    return builderApiResponse.getData();
  }

  private <T extends BuilderPayload> BuilderPayload extractBuilderPayload(
      final BuilderApiResponse<T> builderApiResponse) {
    return builderApiResponse.getData();
  }

  private SchemaDefinitionsBellatrix getSchemaDefinitionsBellatrix(
      final SpecMilestone specMilestone) {
    return schemaDefinitionCache
        .getSchemaDefinition(specMilestone)
        .toVersionBellatrix()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    specMilestone
                        + " is not a supported milestone for the builder rest api. Milestones >= Bellatrix are supported."));
  }
}
