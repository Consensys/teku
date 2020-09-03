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
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MinimalSigningHistory {
  @JsonProperty("pubkey")
  public final BLSPubKey pubkey;

  @JsonProperty("last_signed_block_slot")
  public final UInt64 lastSignedBlockSlot;

  @JsonProperty("last_signed_attestation_source_epoch")
  public final UInt64 lastSignedAttestationSourceEpoch;

  @JsonProperty("last_signed_attestation_target_epoch")
  public final UInt64 lastSignedAttestationTargetEpoch;

  @JsonCreator
  public MinimalSigningHistory(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("last_signed_block_slot") final UInt64 lastSignedBlockSlot,
      @JsonProperty("last_signed_attestation_source_epoch")
          final UInt64 lastSignedAttestationSourceEpoch,
      @JsonProperty("last_signed_attestation_target_epoch")
          final UInt64 lastSignedAttestationTargetEpoch) {
    this.pubkey = pubkey;
    this.lastSignedBlockSlot = lastSignedBlockSlot;
    this.lastSignedAttestationSourceEpoch = lastSignedAttestationSourceEpoch;
    this.lastSignedAttestationTargetEpoch = lastSignedAttestationTargetEpoch;
  }

  public MinimalSigningHistory(
      final BLSPubKey blsPubKey, final ValidatorSigningRecord validatorSigningRecord) {
    this.pubkey = blsPubKey;
    this.lastSignedBlockSlot = validatorSigningRecord.getBlockSlot();
    this.lastSignedAttestationSourceEpoch = validatorSigningRecord.getAttestationSourceEpoch();
    this.lastSignedAttestationTargetEpoch = validatorSigningRecord.getAttestationTargetEpoch();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final MinimalSigningHistory that = (MinimalSigningHistory) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(lastSignedBlockSlot, that.lastSignedBlockSlot)
        && Objects.equals(lastSignedAttestationSourceEpoch, that.lastSignedAttestationSourceEpoch)
        && Objects.equals(lastSignedAttestationTargetEpoch, that.lastSignedAttestationTargetEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pubkey,
        lastSignedBlockSlot,
        lastSignedAttestationSourceEpoch,
        lastSignedAttestationTargetEpoch);
  }

  public ValidatorSigningRecord toValidatorSigningRecord(
      final Optional<ValidatorSigningRecord> maybeRecord, final Bytes32 genesisValidatorsRoot) {
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
