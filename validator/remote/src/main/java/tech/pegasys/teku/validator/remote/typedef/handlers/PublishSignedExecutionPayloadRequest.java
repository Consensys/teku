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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_BROADCAST_VALIDATION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_EXECUTION_PAYLOAD_ENVELOPE;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class PublishSignedExecutionPayloadRequest extends AbstractTypeDefRequest {

  private final Spec spec;

  public PublishSignedExecutionPayloadRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public PublishSignedExecutionPayloadResult submit(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<BroadcastValidationLevel> broadcastValidationLevel) {

    final SpecMilestone milestone = spec.atSlot(signedExecutionPayload.getSlot()).getMilestone();
    final Map<String, String> headers =
        Map.of(HEADER_CONSENSUS_VERSION, milestone.name().toLowerCase(Locale.ROOT));

    final Map<String, String> queryParams =
        broadcastValidationLevel
            .map(level -> Map.of(PARAM_BROADCAST_VALIDATION, level.name().toLowerCase(Locale.ROOT)))
            .orElse(emptyMap());

    final DeserializableTypeDefinition<SignedExecutionPayloadEnvelope> typeDefinition =
        spec.atSlot(signedExecutionPayload.getSlot())
            .getSchemaDefinitions()
            .toVersionGloas()
            .orElseThrow()
            .getSignedExecutionPayloadEnvelopeSchema()
            .getJsonTypeDefinition();

    return postJson(
            SEND_SIGNED_EXECUTION_PAYLOAD_ENVELOPE,
            emptyMap(),
            queryParams,
            headers,
            signedExecutionPayload,
            typeDefinition,
            new ResponseHandler<>())
        .map(
            __ ->
                PublishSignedExecutionPayloadResult.success(
                    signedExecutionPayload.getBeaconBlockRoot()))
        .orElseGet(
            () ->
                PublishSignedExecutionPayloadResult.rejected(
                    signedExecutionPayload.getBeaconBlockRoot(), "UNKNOWN"));
  }
}
