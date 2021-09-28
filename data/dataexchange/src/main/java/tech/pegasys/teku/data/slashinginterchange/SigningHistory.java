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

import static tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord.isNeverSigned;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SigningHistory {
  @JsonProperty("pubkey")
  public final BLSPubKey pubkey;

  @JsonProperty("signed_blocks")
  public final List<SignedBlock> signedBlocks;

  @JsonProperty("signed_attestations")
  public final List<SignedAttestation> signedAttestations;

  @JsonCreator
  public SigningHistory(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("signed_blocks") final List<SignedBlock> signedBlocks,
      @JsonProperty("signed_attestations") final List<SignedAttestation> signedAttestations) {
    this.pubkey = pubkey;
    this.signedBlocks = signedBlocks;
    this.signedAttestations = signedAttestations;
  }

  public SigningHistory(final BLSPubKey pubkey, final ValidatorSigningRecord record) {
    this.pubkey = pubkey;
    this.signedBlocks = new ArrayList<>();
    signedBlocks.add(new SignedBlock(record.getBlockSlot(), null));
    this.signedAttestations = new ArrayList<>();
    if (!isNeverSigned(record.getAttestationSourceEpoch())
        || !isNeverSigned(record.getAttestationTargetEpoch())) {
      signedAttestations.add(
          new SignedAttestation(
              record.getAttestationSourceEpoch(), record.getAttestationTargetEpoch(), null));
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final SigningHistory that = (SigningHistory) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(signedBlocks, that.signedBlocks)
        && Objects.equals(signedAttestations, that.signedAttestations);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubkey", pubkey)
        .add("signedBlocks", signedBlocks)
        .add("signedAttestations", signedAttestations)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, signedBlocks, signedAttestations);
  }

  public ValidatorSigningRecord toValidatorSigningRecord(
      final Optional<ValidatorSigningRecord> maybeRecord, final Bytes32 genesisValidatorsRoot) {

    final UInt64 lastSignedBlockSlot =
        signedBlocks.stream()
            .map(SignedBlock::getSlot)
            .filter(Objects::nonNull)
            .max(UInt64::compareTo)
            .orElse(UInt64.ZERO);
    final UInt64 lastSignedAttestationSourceEpoch =
        signedAttestations.stream()
            .map(SignedAttestation::getSourceEpoch)
            .filter(Objects::nonNull)
            .max(UInt64::compareTo)
            .orElse(null);
    final UInt64 lastSignedAttestationTargetEpoch =
        signedAttestations.stream()
            .map(SignedAttestation::getTargetEpoch)
            .filter(Objects::nonNull)
            .max(UInt64::compareTo)
            .orElse(null);

    if (maybeRecord.isPresent()) {
      final ValidatorSigningRecord record = maybeRecord.get();
      return new ValidatorSigningRecord(
          genesisValidatorsRoot,
          record.getBlockSlot().max(lastSignedBlockSlot),
          nullSafeMax(record.getAttestationSourceEpoch(), lastSignedAttestationSourceEpoch),
          nullSafeMax(record.getAttestationTargetEpoch(), lastSignedAttestationTargetEpoch));
    }
    return new ValidatorSigningRecord(
        genesisValidatorsRoot,
        lastSignedBlockSlot,
        lastSignedAttestationSourceEpoch,
        lastSignedAttestationTargetEpoch);
  }

  private UInt64 nullSafeMax(final UInt64 a, final UInt64 b) {
    if (a == null) {
      return b;
    } else if (b == null) {
      return a;
    }
    return a.max(b);
  }
}
