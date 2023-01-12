/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.protoarray;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.TestStoreImpl;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;

public class ProtoArrayTestUtil {

  // Gives a deterministic hash for a given integer
  public static Bytes32 getHash(int i) {
    return MathHelpers.uintToBytes32(Integer.toUnsignedLong(i + 1));
  }

  public static ForkChoiceStrategy createProtoArrayForkChoiceStrategy(
      Spec spec,
      Bytes32 finalizedBlockRoot,
      UInt64 finalizedBlockSlot,
      UInt64 finalizedCheckpointEpoch,
      UInt64 justifiedCheckpointEpoch) {

    final ProtoArray protoArray =
        ProtoArray.builder()
            .currentEpoch(ZERO)
            .justifiedCheckpoint(new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO))
            .finalizedCheckpoint(new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO))
            .progressiveBalancesMode(spec.getGenesisSpecConfig().getProgressiveBalancesMode())
            .build();

    ForkChoiceStrategy forkChoice =
        ForkChoiceStrategy.initialize(
            spec, protoArray, spec.getGenesisSpec().getConfig().getMinGenesisTime());

    forkChoice.processBlock(
        finalizedBlockSlot,
        finalizedBlockRoot,
        Bytes32.ZERO,
        Bytes32.ZERO,
        new BlockCheckpoints(
            new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(justifiedCheckpointEpoch, Bytes32.ZERO),
            new Checkpoint(finalizedCheckpointEpoch, Bytes32.ZERO)),
        Bytes32.ZERO);

    return forkChoice;
  }

  public static TestStoreImpl createStoreToManipulateVotes() {
    return new TestStoreFactory().createGenesisStore();
  }

  public static void assertThatBlockInformationMatches(ProtoNode node1, ProtoNode node2) {
    assertThat(node1.getBlockSlot()).isEqualTo(node2.getBlockSlot());
    assertThat(node1.getStateRoot()).isEqualTo(node2.getStateRoot());
    assertThat(node1.getBlockRoot()).isEqualTo(node2.getBlockRoot());
    assertThat(node1.getParentRoot()).isEqualTo(node2.getParentRoot());
    assertThat(node1.getJustifiedCheckpoint()).isEqualTo(node2.getJustifiedCheckpoint());
    assertThat(node1.getFinalizedCheckpoint()).isEqualTo(node2.getFinalizedCheckpoint());
  }

  public static void assertThatProtoArrayMatches(ProtoArray array1, ProtoArray array2) {
    assertThat(array1.getNodes().size()).isEqualTo(array2.getNodes().size());
    assertThat(array1.getJustifiedCheckpoint()).isEqualTo(array2.getJustifiedCheckpoint());
    assertThat(array1.getFinalizedCheckpoint()).isEqualTo(array2.getFinalizedCheckpoint());
    for (int i = 0; i < array1.getNodes().size(); i++) {
      assertThatBlockInformationMatches(array1.getNodes().get(i), array2.getNodes().get(i));
    }
  }
}
