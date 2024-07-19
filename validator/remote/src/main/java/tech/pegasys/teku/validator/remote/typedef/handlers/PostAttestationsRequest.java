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
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION_V2;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class PostAttestationsRequest extends AbstractTypeDefRequest {

  private final Spec spec;

  public PostAttestationsRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public Optional<PostDataFailureResponse> postAttestations(
      final List<Attestation> attestations, final SpecMilestone specMilestone) {
    final DeserializableTypeDefinition<Attestation> attestationTypeDefinition =
        spec.forMilestone(specMilestone)
            .getSchemaDefinitions()
            .getAttestationSchema()
            .castTypeToAttestationSchema()
            .getJsonTypeDefinition();

    final SerializableTypeDefinition<List<Attestation>> attestationsListTypeDefinition =
        SerializableTypeDefinition.listOf(attestationTypeDefinition);

    return postJson(
        SEND_SIGNED_ATTESTATION_V2,
        emptyMap(),
        emptyMap(),
        Map.of(HEADER_CONSENSUS_VERSION, specMilestone.name().toLowerCase(Locale.ROOT)),
        attestations,
        attestationsListTypeDefinition,
        new ResponseHandler<>());
  }
}
