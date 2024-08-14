/*
 * Copyright Consensys Software Inc., 2024
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
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION_V2;
import static tech.pegasys.teku.validator.remote.typedef.FailureListResponse.getFailureListResponseResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.typedef.FailureListResponse;

public class PostAttestationsRequest extends AbstractTypeDefRequest {

  public PostAttestationsRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public List<SubmitDataError> submit(
      final List<Attestation> attestations, final SpecMilestone specMilestone) {
    if (attestations.isEmpty()) {
      return Collections.emptyList();
    }
    return postJson(
            SEND_SIGNED_ATTESTATION_V2,
            emptyMap(),
            emptyMap(),
            Map.of(HEADER_CONSENSUS_VERSION, specMilestone.name().toLowerCase(Locale.ROOT)),
            attestations,
            listOf(getTypeDefinition(attestations)),
            getFailureListResponseResponseHandler())
        .map(FailureListResponse::failures)
        .orElse(Collections.emptyList());
  }

  @SuppressWarnings("unchecked")
  private DeserializableTypeDefinition<Attestation> getTypeDefinition(
      final List<Attestation> attestations) {
    return (DeserializableTypeDefinition<Attestation>)
        attestations.getFirst().getSchema().getJsonTypeDefinition();
  }
}
