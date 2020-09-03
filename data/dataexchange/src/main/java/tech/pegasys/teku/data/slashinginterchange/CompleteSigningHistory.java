/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.data.slashinginterchange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.api.schema.BLSPubKey;

public class CompleteSigningHistory {
  @JsonProperty("pubkey")
  public final BLSPubKey pubkey;

  @JsonProperty("signed_blocks")
  public final List<SignedBlock> signedBlocks;

  @JsonProperty("signed_attestations")
  public final List<SignedAttestation> signedAttestations;

  @JsonCreator
  public CompleteSigningHistory(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("signed_blocks") final List<SignedBlock> signedBlocks,
      @JsonProperty("signed_attestations") final List<SignedAttestation> signedAttestations) {
    this.pubkey = pubkey;
    this.signedBlocks = signedBlocks;
    this.signedAttestations = signedAttestations;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final CompleteSigningHistory that = (CompleteSigningHistory) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(signedBlocks, that.signedBlocks)
        && Objects.equals(signedAttestations, that.signedAttestations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, signedBlocks, signedAttestations);
  }
}
