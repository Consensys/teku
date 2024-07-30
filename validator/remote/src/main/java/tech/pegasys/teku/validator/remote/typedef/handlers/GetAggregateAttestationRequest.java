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

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_AGGREGATE_V2;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetAggregateAttestationRequest extends AbstractTypeDefRequest {

  final AttestationSchema<Attestation> attestationSchema;
  final UInt64 slot;

  private final ResponseHandler<GetAggregateAttestationResponse> responseHandler;

  private final DeserializableTypeDefinition<GetAggregateAttestationResponse>
      getAggregateAttestationTypeDef;

  public GetAggregateAttestationRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final Spec spec,
      final UInt64 slot) {
    super(baseEndpoint, okHttpClient);
    this.slot = slot;
    this.attestationSchema =
        spec.atSlot(slot)
            .getSchemaDefinitions()
            .getAttestationSchema()
            .castTypeToAttestationSchema();
    this.getAggregateAttestationTypeDef =
        DeserializableTypeDefinition.object(GetAggregateAttestationResponse.class)
            .initializer(GetAggregateAttestationResponse::new)
            .withField(
                "version",
                DeserializableTypeDefinition.enumOf(SpecMilestone.class),
                GetAggregateAttestationResponse::getSpecMilestone,
                GetAggregateAttestationResponse::setSpecMilestone)
            .withField(
                "data",
                attestationSchema.getJsonTypeDefinition(),
                GetAggregateAttestationResponse::getData,
                GetAggregateAttestationResponse::setData)
            .build();

    this.responseHandler = new ResponseHandler<>(getAggregateAttestationTypeDef);
  }

  public Optional<Attestation> createAggregate(
      final Bytes32 attestationHashTreeRoot, final UInt64 committeeIndex) {
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put(SLOT_QUERY_DESCRIPTION, slot.toString());
    queryParams.put(ATTESTATION_DATA_ROOT, attestationHashTreeRoot.toHexString());
    queryParams.put(COMMITTEE_INDEX, committeeIndex.toString());
    return get(GET_AGGREGATE_V2, queryParams, this.responseHandler)
        .map(
            getAggregateAttestationResponse -> {
              final Attestation attestation = getAggregateAttestationResponse.getData();
              final Supplier<SszBitvector> committeeBits =
                  attestation.requiresCommitteeBits()
                      ? attestation::getCommitteeBitsRequired
                      : () -> null;
              return this.attestationSchema.create(
                  attestation.getAggregationBits(),
                  attestation.getData(),
                  attestation.getAggregateSignature(),
                  committeeBits);
            });
  }

  public static class GetAggregateAttestationResponse {

    private Attestation data;
    private SpecMilestone specMilestone;

    public GetAggregateAttestationResponse() {}

    public GetAggregateAttestationResponse(final Attestation data) {
      this.data = data;
    }

    public Attestation getData() {
      return data;
    }

    public void setData(final Attestation data) {
      this.data = data;
    }

    public SpecMilestone getSpecMilestone() {
      return specMilestone;
    }

    public void setSpecMilestone(final SpecMilestone specMilestone) {
      this.specMilestone = specMilestone;
    }
  }
}
