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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BROADCAST_VALIDATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_EXECUTION_PAYLOAD_ENVELOPE;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class PublishSignedExecutionPayloadRequest extends AbstractTypeDefRequest {

  private final ResponseHandler<Boolean> responseHandler =
      new ResponseHandler<Boolean>()
          .withHandler(SC_OK, (request, response) -> Optional.of(true))
          .withHandler(SC_ACCEPTED, (request, response) -> Optional.of(false));

  private final Spec spec;

  public PublishSignedExecutionPayloadRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public PublishSignedExecutionPayloadResult submit(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    // Blind before sending: the BN unblinds from its getPayload cache; avoids a blob round-trip.
    return submit(signedExecutionPayload.blind(spec), broadcastValidationLevel);
  }

  public PublishSignedExecutionPayloadResult submit(
      final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {

    final Map<String, String> queryParams = getQueryParams(broadcastValidationLevel);

    final DeserializableTypeDefinition<SignedExecutionPayloadEnvelopeContents> typeDefinition =
        spec.atSlot(signedExecutionPayloadEnvelopeContents.getSlot())
            .getSchemaDefinitions()
            .toVersionGloas()
            .orElseThrow()
            .getSignedExecutionPayloadEnvelopeContentsSchema()
            .getJsonTypeDefinition();

    return createResult(
        postJson(
            SEND_SIGNED_EXECUTION_PAYLOAD_ENVELOPE,
            emptyMap(),
            queryParams,
            getHeaders(signedExecutionPayloadEnvelopeContents.getSlot(), false),
            signedExecutionPayloadEnvelopeContents,
            typeDefinition,
            responseHandler),
        signedExecutionPayloadEnvelopeContents.getBeaconBlockRoot());
  }

  public PublishSignedExecutionPayloadResult submit(
      final SignedBlindedExecutionPayloadEnvelope signedBlindedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {

    final Map<String, String> queryParams = getQueryParams(broadcastValidationLevel);

    final DeserializableTypeDefinition<SignedBlindedExecutionPayloadEnvelope> typeDefinition =
        spec.atSlot(signedBlindedExecutionPayload.getSlot())
            .getSchemaDefinitions()
            .toVersionGloas()
            .orElseThrow()
            .getSignedBlindedExecutionPayloadEnvelopeSchema()
            .getJsonTypeDefinition();

    return createResult(
        postJson(
            SEND_SIGNED_EXECUTION_PAYLOAD_ENVELOPE,
            emptyMap(),
            queryParams,
            getHeaders(signedBlindedExecutionPayload.getSlot(), true),
            signedBlindedExecutionPayload,
            typeDefinition,
            responseHandler),
        signedBlindedExecutionPayload.getBeaconBlockRoot());
  }

  private PublishSignedExecutionPayloadResult createResult(
      final Optional<Boolean> maybePublishedAndImported, final Bytes32 beaconBlockRoot) {
    return maybePublishedAndImported
        .map(
            publishedAndImported ->
                publishedAndImported
                    ? PublishSignedExecutionPayloadResult.success(beaconBlockRoot)
                    : PublishSignedExecutionPayloadResult.notImported(beaconBlockRoot, "UNKNOWN"))
        .orElseGet(
            () -> PublishSignedExecutionPayloadResult.notImported(beaconBlockRoot, "UNKNOWN"));
  }

  private Map<String, String> getHeaders(final UInt64 slot, final boolean blinded) {
    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();
    return Map.of(
        HEADER_CONSENSUS_VERSION, milestone.name().toLowerCase(Locale.ROOT),
        HEADER_EXECUTION_PAYLOAD_BLINDED, String.valueOf(blinded));
  }

  private Map<String, String> getQueryParams(
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
    return broadcastValidationLevel
        .map(level -> Map.of(PARAM_BROADCAST_VALIDATION, level.name().toLowerCase(Locale.ROOT)))
        .orElse(emptyMap());
  }
}
