/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

// Serialization formats (detected by presence/absence of epoch field):
//
// Legacy formats (contain epoch field after the two roots):
//   currentRoot(32) | nextRoot(32) | epoch(8)
//   currentRoot(32) | nextRoot(32) | epoch(8) | nextEquiv(1) | curEquiv(1)
//
// Gloas format (current, no epoch field):
//   currentRoot(32) | nextRoot(32) | nextEquiv(1) | curEquiv(1) |
//   nextSlot(8) | nextFullHint(1) | curSlot(8) | curFullHint(1)
//
// The legacy epoch field (uint64, 8 bytes) sits right after the two roots. In the Gloas
// format the next byte after the roots is a boolean (equivocation flag). We use total data
// length to distinguish the Gloas format from legacy formats.
class VoteTrackerSerializer implements KvStoreSerializer<VoteTracker> {

  // Size of the two root fields that are common to all formats
  private static final int ROOTS_SIZE = Bytes32.SIZE + Bytes32.SIZE;
  // Size of the Gloas format written by serialize()
  private static final int GLOAS_SIZE = ROOTS_SIZE + 1 + 1 + 8 + 1 + 8 + 1;

  private final int slotsPerEpoch;

  VoteTrackerSerializer(final int slotsPerEpoch) {
    this.slotsPerEpoch = slotsPerEpoch;
  }

  @Override
  public VoteTracker deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final Bytes32 currentRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 nextRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));

          if (data.length == GLOAS_SIZE) {
            // Gloas format: no epoch field
            final boolean nextEquivocating = reader.readBoolean();
            final boolean currentEquivocating = reader.readBoolean();
            final UInt64 nextSlot = UInt64.fromLongBits(reader.readUInt64());
            final boolean nextFullPayloadHint = reader.readBoolean();
            final UInt64 currentSlot = UInt64.fromLongBits(reader.readUInt64());
            final boolean currentFullPayloadHint = reader.readBoolean();
            return new VoteTracker(
                currentRoot,
                nextRoot,
                nextEquivocating,
                currentEquivocating,
                nextSlot,
                nextFullPayloadHint,
                currentSlot,
                currentFullPayloadHint);
          }

          // Legacy formats: convert epoch to slot
          final long epoch = reader.readUInt64();
          final UInt64 slot = UInt64.fromLongBits(epoch * slotsPerEpoch);

          if (reader.isComplete()) {
            return new VoteTracker(
                currentRoot, nextRoot, false, false, slot, false, UInt64.ZERO, false);
          }

          final boolean nextEquivocating = reader.readBoolean();
          final boolean currentEquivocating = reader.readBoolean();
          return new VoteTracker(
              currentRoot,
              nextRoot,
              nextEquivocating,
              currentEquivocating,
              slot,
              false,
              UInt64.ZERO,
              false);
        });
  }

  @Override
  public byte[] serialize(final VoteTracker value) {
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeFixedBytes(value.getCurrentRoot());
              writer.writeFixedBytes(value.getNextRoot());
              writer.writeBoolean(value.isNextEquivocating());
              writer.writeBoolean(value.isCurrentEquivocating());
              writer.writeUInt64(value.getNextSlot().longValue());
              writer.writeBoolean(value.isNextFullPayloadHint());
              writer.writeUInt64(value.getCurrentSlot().longValue());
              writer.writeBoolean(value.isCurrentFullPayloadHint());
            });
    return bytes.toArrayUnsafe();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return slotsPerEpoch == ((VoteTrackerSerializer) o).slotsPerEpoch;
  }

  @Override
  public int hashCode() {
    return Objects.hash(slotsPerEpoch);
  }
}
