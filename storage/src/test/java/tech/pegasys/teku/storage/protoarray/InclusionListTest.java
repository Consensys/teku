/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus.VALID;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class InclusionListTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final int pruneThreshold = 10;
  private final UInt64 currentEpoch = UInt64.valueOf(10);
  private final Checkpoint justifiedCheckpoint = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
  private final Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
  private final UInt64 initialEpoch = UInt64.ZERO;

  private ProtoArray protoArray;

  @BeforeEach
  void setUp() {
    protoArray =
        ProtoArray.builder()
            .pruneThreshold(pruneThreshold)
            .justifiedCheckpoint(justifiedCheckpoint)
            .finalizedCheckpoint(finalizedCheckpoint)
            .currentEpoch(currentEpoch)
            .initialEpoch(initialEpoch)
            .spec(spec)
            .build();
  }

  @Test
  void shouldConsiderBlockOnInclusionListAsViableForHead() {
    final Bytes32 blockRoot = Bytes32.fromHexString("0x1234");
    final Bytes32 parentRoot = Bytes32.ZERO;
    final UInt64 blockSlot = UInt64.ONE;
    final BlockCheckpoints checkpoints =
        new BlockCheckpoints(justifiedCheckpoint, finalizedCheckpoint, justifiedCheckpoint, finalizedCheckpoint);

    // Add a block
    protoArray.onBlock(
        blockSlot,
        blockRoot,
        parentRoot,
        Bytes32.ZERO,
        checkpoints,
        UInt64.ZERO,
        Bytes32.ZERO,
        false);

    // Initially block is not on inclusion list and not viable for head
    assertThat(protoArray.nodeIsViableForHead(protoArray.getProtoNode(blockRoot).orElseThrow()))
        .isFalse();

    // Add block to inclusion list
    protoArray.onInclusionList(blockRoot, true);

    // Block should now be viable for head
    assertThat(protoArray.nodeIsViableForHead(protoArray.getProtoNode(blockRoot).orElseThrow()))
        .isTrue();
  }

  @Test
  void shouldRemoveBlockFromInclusionList() {
    final Bytes32 blockRoot = Bytes32.fromHexString("0x1234");
    final Bytes32 parentRoot = Bytes32.ZERO;
    final UInt64 blockSlot = UInt64.ONE;
    final BlockCheckpoints checkpoints =
        new BlockCheckpoints(justifiedCheckpoint, finalizedCheckpoint, justifiedCheckpoint, finalizedCheckpoint);

    // Add a block and put it on inclusion list
    protoArray.onBlock(
        blockSlot,
        blockRoot,
        parentRoot,
        Bytes32.ZERO,
        checkpoints,
        UInt64.ZERO,
        Bytes32.ZERO,
        false);
    protoArray.onInclusionList(blockRoot, true);

    // Remove block from inclusion list
    protoArray.onInclusionList(blockRoot, false);

    // Block should no longer be viable for head
    assertThat(protoArray.nodeIsViableForHead(protoArray.getProtoNode(blockRoot).orElseThrow()))
        .isFalse();
  }

  @Test
  void shouldUpdateBestDescendantWhenAddingToInclusionList() {
    final Bytes32 blockRoot1 = Bytes32.fromHexString("0x1234");
    final Bytes32 blockRoot2 = Bytes32.fromHexString("0x5678");
    final UInt64 blockSlot1 = UInt64.ONE;
    final UInt64 blockSlot2 = UInt64.valueOf(2);
    final BlockCheckpoints checkpoints =
        new BlockCheckpoints(justifiedCheckpoint, finalizedCheckpoint, justifiedCheckpoint, finalizedCheckpoint);

    // Add two blocks in sequence
    protoArray.onBlock(
        blockSlot1,
        blockRoot1,
        Bytes32.ZERO,
        Bytes32.ZERO,
        checkpoints,
        UInt64.ZERO,
        Bytes32.ZERO,
        false);
    protoArray.onBlock(
        blockSlot2,
        blockRoot2,
        blockRoot1,
        Bytes32.ZERO,
        checkpoints,
        UInt64.ZERO,
        Bytes32.ZERO,
        false);

    // Add second block to inclusion list
    protoArray.onInclusionList(blockRoot2, true);

    // First block should have second block as best descendant
    final ProtoNode node1 = protoArray.getProtoNode(blockRoot1).orElseThrow();
    assertThat(node1.getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(blockRoot2));
  }
} 
