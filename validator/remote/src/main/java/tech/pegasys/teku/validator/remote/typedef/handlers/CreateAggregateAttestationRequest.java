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

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_AGGREGATE;

import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class CreateAggregateAttestationRequest extends AbstractTypeDefRequest {
  private final Spec spec;

  public CreateAggregateAttestationRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient, final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public Optional<? extends Attestation> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    final Map<String, String> queryParams =
        Map.of(
            "slot", slot.toString(), "attestation_data_root", attestationHashTreeRoot.toString());
    final AttestationSchema<? extends Attestation> attestationSchema =
        spec.atSlot(slot).getSchemaDefinitions().getAttestationSchema();

    return get(
        GET_AGGREGATE, queryParams, new ResponseHandler<>(withDataWrapper(attestationSchema)));
  }
}
