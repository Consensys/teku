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

package tech.pegasys.teku.core.signatures.record;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Holds the key information required to prevent a validator from being slashed.
 *
 * <p>For blocks, the slot of the last signed block is recorded and signing is only allowed if the
 * slot is greater than the previous.
 *
 * <p>For attestations, the last source epoch and target epoch are recorded. An attestation may only
 * be signed if source >= previousSource AND target > previousTarget
 */
public class ValidatorSigningRecord {

  public static final UInt64 NEVER_SIGNED = UInt64.MAX_VALUE;

  private final UInt64 blockSlot;

  private final UInt64 attestationSourceEpoch;

  private final UInt64 attestationTargetEpoch;

  public ValidatorSigningRecord() {
    this(UInt64.ZERO, NEVER_SIGNED, NEVER_SIGNED);
  }

  public ValidatorSigningRecord(
      final UInt64 blockSlot,
      final UInt64 attestationSourceEpoch,
      final UInt64 attestationTargetEpoch) {
    this.blockSlot = blockSlot;
    this.attestationSourceEpoch = attestationSourceEpoch;
    this.attestationTargetEpoch = attestationTargetEpoch;
  }

  public static ValidatorSigningRecord fromBytes(final Bytes data) {
    return ValidatorSigningRecordSerialization.readRecord(data);
  }

  public Bytes toBytes() {
    return ValidatorSigningRecordSerialization.writeRecord(this);
  }

  /**
   * Determine if it is safe to sign a block at the specified slot.
   *
   * @param slot the slot of the block being signed
   * @return an Optional containing an updated {@link ValidatorSigningRecord} with the state after
   *     the block is signed or empty if it is not safe to sign the block.
   */
  public Optional<ValidatorSigningRecord> maySignBlock(final UInt64 slot) {
    // We never allow signing a block at slot 0 because we shouldn't be signing the genesis block.
    if (blockSlot.compareTo(slot) < 0) {
      return Optional.of(
          new ValidatorSigningRecord(slot, attestationSourceEpoch, attestationTargetEpoch));
    }
    return Optional.empty();
  }

  /**
   * Determine if it is safe to sign an attestation with the specified source and target epochs.
   *
   * @param sourceEpoch the source epoch of the attestation to sign
   * @param targetEpoch the target epoch of the attestation to sign
   * @return an Optional containing an updated {@link ValidatorSigningRecord} with the state after
   *     the attestation is signed or empty if it is not safe to sign the attestation.
   */
  public Optional<ValidatorSigningRecord> maySignAttestation(
      final UInt64 sourceEpoch, final UInt64 targetEpoch) {
    if (isSafeSourceEpoch(sourceEpoch) && isSafeTargetEpoch(targetEpoch)) {
      return Optional.of(new ValidatorSigningRecord(blockSlot, sourceEpoch, targetEpoch));
    }
    return Optional.empty();
  }

  private boolean isSafeSourceEpoch(final UInt64 sourceEpoch) {
    return attestationSourceEpoch.equals(NEVER_SIGNED)
        || attestationSourceEpoch.compareTo(sourceEpoch) <= 0;
  }

  private boolean isSafeTargetEpoch(final UInt64 targetEpoch) {
    return attestationTargetEpoch.equals(NEVER_SIGNED)
        || attestationTargetEpoch.compareTo(targetEpoch) < 0;
  }

  UInt64 getBlockSlot() {
    return blockSlot;
  }

  UInt64 getAttestationSourceEpoch() {
    return attestationSourceEpoch;
  }

  UInt64 getAttestationTargetEpoch() {
    return attestationTargetEpoch;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorSigningRecord that = (ValidatorSigningRecord) o;
    return Objects.equals(blockSlot, that.blockSlot)
        && Objects.equals(attestationSourceEpoch, that.attestationSourceEpoch)
        && Objects.equals(attestationTargetEpoch, that.attestationTargetEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockSlot, attestationSourceEpoch, attestationTargetEpoch);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockSlot", blockSlot)
        .add("attestationSourceEpoch", attestationSourceEpoch)
        .add("attestationTargetEpoch", attestationTargetEpoch)
        .toString();
  }
}
