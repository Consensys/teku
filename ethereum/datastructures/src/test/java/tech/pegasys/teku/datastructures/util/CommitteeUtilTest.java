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

package tech.pegasys.teku.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class CommitteeUtilTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void testListShuffleAndShuffledIndexCompatibility() {
    Bytes32 seed = Bytes32.ZERO;
    int index_count = 3333;
    int[] indexes = IntStream.range(0, index_count).toArray();

    CommitteeUtil.shuffle_list(indexes, seed);
    assertThat(indexes)
        .isEqualTo(
            IntStream.range(0, index_count)
                .map(i -> CommitteeUtil.compute_shuffled_index(i, indexes.length, seed))
                .toArray());
  }

  @Test
  public void getBeaconCommittee_stateIsTooOld() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = compute_start_slot_at_epoch(epoch);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 outOfRangeSlot = compute_start_slot_at_epoch(epoch.plus(2));
    assertThatThrownBy(() -> CommitteeUtil.get_beacon_committee(state, outOfRangeSlot, ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Committee information must be derived from a state no older than the previous epoch");
  }

  @Test
  public void getBeaconCommittee_stateFromEpochThatIsTooOld() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = compute_start_slot_at_epoch(epoch.plus(ONE)).minus(ONE);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 outOfRangeSlot = compute_start_slot_at_epoch(epoch.plus(2));
    assertThatThrownBy(() -> CommitteeUtil.get_beacon_committee(state, outOfRangeSlot, ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Committee information must be derived from a state no older than the previous epoch");
  }

  @Test
  public void getBeaconCommittee_stateIsJustNewEnough() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = compute_start_slot_at_epoch(epoch);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 outOfRangeSlot = compute_start_slot_at_epoch(epoch.plus(2));
    final UInt64 inRangeSlot = outOfRangeSlot.minus(ONE);
    assertDoesNotThrow(() -> CommitteeUtil.get_beacon_committee(state, inRangeSlot, ONE));
  }

  @Test
  public void getBeaconCommittee_stateIsNewerThanSlot() {
    final UInt64 epoch = ONE;
    final UInt64 epochSlot = compute_start_slot_at_epoch(epoch);
    final BeaconState state = dataStructureUtil.randomBeaconState(epochSlot);

    final UInt64 oldSlot = epochSlot.minus(ONE);
    assertDoesNotThrow(() -> CommitteeUtil.get_beacon_committee(state, oldSlot, ONE));
  }

  @Test
  void testIsAggregatorReturnsFalseOnARealCase() {
    Bytes signingRoot =
        compute_signing_root(
            57950,
            Bytes32.fromHexString(
                "0x05000000b5303f2ad2010d699a76c8e62350947421a3e4a979779642cfdb0f66"));
    BLSSignature selectionProof =
        BLSSignature.fromSSZBytes(
            Bytes.fromHexString(
                "0xaa176502f0a5e954e4c6b452d0e11a03513c19b6d189f125f07b6c5c120df011c31da4c4a9c4a52a5a48fcba5b14d7b316b986a146187966d2341388bbf1f86c42e90553ba009ba10edc6b5544a6e945ce6d2419197f66ab2b9df2b0a0c89987"));
    BLSPublicKey pKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0xb0861f72583516b17a3fdc33419d5c04c0a4444cc2478136b4935f3148797699e3ef4a4b2227b14876b3d49ff03b796d"));
    int committeeLen = 146;

    assertThat(BLS.verify(pKey, signingRoot, selectionProof)).isTrue();

    int aggregatorModulo = CommitteeUtil.getAggregatorModulo(committeeLen);
    assertThat(CommitteeUtil.isAggregator(selectionProof, aggregatorModulo)).isFalse();
  }
}
