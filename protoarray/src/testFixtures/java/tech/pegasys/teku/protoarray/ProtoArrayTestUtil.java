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

package tech.pegasys.teku.protoarray;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProtoArrayTestUtil {
  private static final TestStoreFactory STORE_FACTORY = new TestStoreFactory();

  // Gives a deterministic hash for a given integer
  public static Bytes32 getHash(int i) {
    return BeaconStateUtil.uint_to_bytes32(Integer.toUnsignedLong(i + 1));
  }

  public static ProtoArrayForkChoiceStrategy createProtoArrayForkChoiceStrategy(
      Bytes32 finalizedBlockRoot,
      UInt64 finalizedBlockSlot,
      UInt64 finalizedCheckpointEpoch,
      UInt64 justifiedCheckpointEpoch) {
    MutableStore store = STORE_FACTORY.createEmptyStore();
    store.setJustifiedCheckpoint(new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO));
    store.setFinalizedCheckpoint(new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO));

    ProtoArrayForkChoiceStrategy forkChoice =
        ProtoArrayForkChoiceStrategy.initializeAndMigrateStorage(
                store, ProtoArrayStorageChannel.NO_OP)
            .join();

    forkChoice.processBlock(
        finalizedBlockSlot,
        finalizedBlockRoot,
        Bytes32.ZERO,
        Bytes32.ZERO,
        justifiedCheckpointEpoch,
        finalizedCheckpointEpoch);

    return forkChoice;
  }

  public static MutableStore createStoreToManipulateVotes() {
    return STORE_FACTORY.createGenesisStore();
  }

  public static void assertThatBlockInformationMatches(ProtoNode node1, ProtoNode node2) {
    assertThat(node1.getBlockSlot()).isEqualTo(node2.getBlockSlot());
    assertThat(node1.getStateRoot()).isEqualTo(node2.getStateRoot());
    assertThat(node1.getBlockRoot()).isEqualTo(node2.getBlockRoot());
    assertThat(node1.getParentRoot()).isEqualTo(node2.getParentRoot());
    assertThat(node1.getJustifiedEpoch()).isEqualTo(node2.getJustifiedEpoch());
    assertThat(node1.getFinalizedEpoch()).isEqualTo(node2.getFinalizedEpoch());
  }

  public static void assertThatProtoArrayMatches(ProtoArray array1, ProtoArray array2) {
    assertThat(array1.getNodes().size()).isEqualTo(array2.getNodes().size());
    assertThat(array1.getJustifiedEpoch()).isEqualTo(array2.getJustifiedEpoch());
    assertThat(array1.getFinalizedEpoch()).isEqualTo(array2.getFinalizedEpoch());
    for (int i = 0; i < array1.getNodes().size(); i++) {
      assertThatBlockInformationMatches(array1.getNodes().get(i), array2.getNodes().get(i));
    }
  }
}
