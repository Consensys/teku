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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

public class VoteTrackerSerializerTest {

  private static final Bytes32 CURRENT_ROOT =
      Bytes32.fromHexString("0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
  private static final Bytes32 NEXT_ROOT =
      Bytes32.fromHexString("0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef");

  private static final int SLOTS_PER_EPOCH = 32;
  private final VoteTrackerSerializer serializer = new VoteTrackerSerializer(SLOTS_PER_EPOCH);

  @Test
  void roundTrip_gloas() {
    final VoteTracker vote =
        new VoteTracker(
            CURRENT_ROOT,
            NEXT_ROOT,
            UInt64.ONE,
            true,
            false,
            UInt64.valueOf(42),
            true,
            UInt64.valueOf(10),
            false);
    final VoteTracker deserialized = serializer.deserialize(serializer.serialize(vote));
    assertThat(deserialized).isEqualTo(vote);
  }

  @Test
  void roundTrip_gloasWithDefaultSlots() {
    final VoteTracker vote = new VoteTracker(CURRENT_ROOT, NEXT_ROOT, true, false);
    final VoteTracker deserialized = serializer.deserialize(serializer.serialize(vote));
    assertThat(deserialized).isEqualTo(vote);
  }

  @Test
  void roundTrip_gloasDefault() {
    final VoteTracker deserialized =
        serializer.deserialize(serializer.serialize(VoteTracker.DEFAULT));
    assertThat(deserialized).isEqualTo(VoteTracker.DEFAULT);
  }

  @Test
  void deserialize_legacyPhase0WithoutEquivocation() {
    // 72-byte format: currentRoot(32) | nextRoot(32) | epoch(8)
    final byte[] legacyData =
        SSZ.encode(
                writer -> {
                  writer.writeFixedBytes(CURRENT_ROOT);
                  writer.writeFixedBytes(NEXT_ROOT);
                  writer.writeUInt64(5L);
                })
            .toArrayUnsafe();

    assertThat(legacyData).hasSize(72);
    final VoteTracker deserialized = serializer.deserialize(legacyData);
    // epoch 5 * 32 slots_per_epoch = slot 160
    assertThat(deserialized)
        .isEqualTo(
            new VoteTracker(
                CURRENT_ROOT,
                NEXT_ROOT,
                UInt64.valueOf(5),
                false,
                false,
                UInt64.valueOf(160),
                false,
                UInt64.ZERO,
                false));
  }

  @Test
  void deserialize_legacyPhase0WithEquivocation() {
    // 74-byte format: currentRoot(32) | nextRoot(32) | epoch(8) | nextEquiv(1) | curEquiv(1)
    final byte[] legacyData =
        SSZ.encode(
                writer -> {
                  writer.writeFixedBytes(CURRENT_ROOT);
                  writer.writeFixedBytes(NEXT_ROOT);
                  writer.writeUInt64(5L);
                  writer.writeBoolean(true);
                  writer.writeBoolean(false);
                })
            .toArrayUnsafe();

    assertThat(legacyData).hasSize(74);
    final VoteTracker deserialized = serializer.deserialize(legacyData);
    // epoch 5 * 32 slots_per_epoch = slot 160
    assertThat(deserialized)
        .isEqualTo(
            new VoteTracker(
                CURRENT_ROOT,
                NEXT_ROOT,
                UInt64.valueOf(5),
                true,
                false,
                UInt64.valueOf(160),
                false,
                UInt64.ZERO,
                false));
  }
}
