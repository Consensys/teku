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

package tech.pegasys.teku.core.signatures;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;

class ValidatorSigningRecord {

  static final UnsignedLong NEVER_SIGNED = UnsignedLong.MAX_VALUE;
  private final UnsignedLong blockSlot;
  private final UnsignedLong attestationSourceEpoch;
  private final UnsignedLong attestationTargetEpoch;

  public ValidatorSigningRecord() {
    this(UnsignedLong.ZERO, NEVER_SIGNED, NEVER_SIGNED);
  }

  public ValidatorSigningRecord(
      final UnsignedLong blockSlot,
      final UnsignedLong attestationSourceEpoch,
      final UnsignedLong attestationTargetEpoch) {
    this.blockSlot = blockSlot;
    this.attestationSourceEpoch = attestationSourceEpoch;
    this.attestationTargetEpoch = attestationTargetEpoch;
  }

  public static ValidatorSigningRecord fromBytes(final Bytes data) {
    return SSZ.decode(
        data,
        reader -> {
          final UnsignedLong blockSlot = UnsignedLong.fromLongBits(reader.readUInt64());
          final UnsignedLong sourceEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
          final UnsignedLong targetEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
          return new ValidatorSigningRecord(blockSlot, sourceEpoch, targetEpoch);
        });
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(blockSlot.longValue());
          writer.writeUInt64(attestationSourceEpoch.longValue());
          writer.writeUInt64(attestationTargetEpoch.longValue());
        });
  }

  public Optional<ValidatorSigningRecord> signBlock(final UnsignedLong slot) {
    // We never allow signing a block at slot 0 because we shouldn't be signing the genesis block.
    if (blockSlot.compareTo(slot) < 0) {
      return Optional.of(
          new ValidatorSigningRecord(slot, attestationSourceEpoch, attestationTargetEpoch));
    }
    return Optional.empty();
  }

  public Optional<ValidatorSigningRecord> signAttestation(
      final UnsignedLong sourceEpoch, final UnsignedLong targetEpoch) {
    if (isSafeSourceEpoch(sourceEpoch) && isSafeTargetEpoch(targetEpoch)) {
      return Optional.of(new ValidatorSigningRecord(blockSlot, sourceEpoch, targetEpoch));
    }
    return Optional.empty();
  }

  private boolean isSafeSourceEpoch(final UnsignedLong sourceEpoch) {
    return attestationSourceEpoch.equals(NEVER_SIGNED)
        || attestationSourceEpoch.compareTo(sourceEpoch) <= 0;
  }

  private boolean isSafeTargetEpoch(final UnsignedLong targetEpoch) {
    return attestationTargetEpoch.equals(NEVER_SIGNED)
        || attestationTargetEpoch.compareTo(targetEpoch) < 0;
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
