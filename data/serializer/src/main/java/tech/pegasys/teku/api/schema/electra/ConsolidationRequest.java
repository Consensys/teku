/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.api.schema.electra;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequestSchema;

public class ConsolidationRequest {

  @JsonProperty("source_address")
  private final Eth1Address sourceAddress;

  @JsonProperty("source_pubkey")
  private final BLSPublicKey sourcePubkey;

  @JsonProperty("target_pubkey")
  private final BLSPublicKey targetPubkey;

  public ConsolidationRequest(
      @JsonProperty("source_address") final Eth1Address sourceAddress,
      @JsonProperty("source_pubkey") final BLSPublicKey sourcePubkey,
      @JsonProperty("target_pubkey") final BLSPublicKey targetPubkey) {
    this.sourceAddress = sourceAddress;
    this.sourcePubkey = sourcePubkey;
    this.targetPubkey = targetPubkey;
  }

  public ConsolidationRequest(
      final tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest
          consolidationRequest) {
    this.sourceAddress =
        Eth1Address.fromBytes(consolidationRequest.getSourceAddress().getWrappedBytes());
    this.sourcePubkey = consolidationRequest.getSourcePubkey();
    this.targetPubkey = consolidationRequest.getTargetPubkey();
  }

  public final tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest
      asInternalConsolidationRequest(final ConsolidationRequestSchema schema) {
    return schema.create(sourceAddress, sourcePubkey, targetPubkey);
  }
}
