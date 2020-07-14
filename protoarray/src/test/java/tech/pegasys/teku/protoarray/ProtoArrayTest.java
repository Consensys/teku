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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArrayTest {
  private static final UnsignedLong GENESIS_EPOCH = UnsignedLong.valueOf(Constants.GENESIS_EPOCH);

  @Test
  public void findHead_tieBreakUsingChainHeight() {
    final ProtoArrayChainBuilder builder = ProtoArrayChainBuilder.createGenesisChain();
    final ProtoArray protoArray = builder.protoArray();
    final Bytes32 genesisRoot = builder.getBlockAtSlot(Constants.GENESIS_SLOT).hash_tree_root();

    // Build small chain
    builder.advanceToSlot(5);

    // Create a few forks
    ProtoArrayChainBuilder forkA = builder;
    ProtoArrayChainBuilder forkB = builder.fork();

    // Advance forks to different heights
    forkA.advanceToSlot(7);
    forkB.advanceToSlot(10);

    // We should select the longest chain
    protoArray.applyScoreChanges(getEmptyScoreDeltas(protoArray), GENESIS_EPOCH, GENESIS_EPOCH);
    final BeaconBlock expectedHead = forkB.getBlockAtSlot(10);
    Bytes32 head = protoArray.findHead(genesisRoot);
    assertThat(head).isEqualTo(expectedHead.hash_tree_root());

    // Grow the other fork - we should now choose the tallest fork
    forkA.advanceToSlot(11);
    protoArray.applyScoreChanges(getEmptyScoreDeltas(protoArray), GENESIS_EPOCH, GENESIS_EPOCH);
    final BeaconBlock expectedHead2 = forkA.getBlockAtSlot(11);
    Bytes32 head2 = protoArray.findHead(genesisRoot);
    assertThat(head2).isEqualTo(expectedHead2.hash_tree_root());
  }

  @Test
  public void findHead_ignoreHeightInFavorOfWeight() {
    final ProtoArrayChainBuilder builder = ProtoArrayChainBuilder.createGenesisChain();
    final ProtoArray protoArray = builder.protoArray();
    final Bytes32 genesisRoot = builder.getBlockAtSlot(Constants.GENESIS_SLOT).hash_tree_root();

    // Build small chain
    builder.advanceToSlot(5);

    // Create a few forks
    ProtoArrayChainBuilder forkA = builder;
    ProtoArrayChainBuilder forkB = builder.fork();

    // Advance forks to different heights
    forkA.advanceToSlot(7);
    forkB.advanceToSlot(10);

    // Update the shorter chain to be weightier
    List<Long> deltas = getEmptyScoreDeltas(protoArray);
    Integer forkABlock6Index =
        protoArray.getIndices().get(forkA.getBlockAtSlot(6).hash_tree_root());
    deltas.set(forkABlock6Index, 10L);

    // We should choose the head from the shorter, weightier chain
    protoArray.applyScoreChanges(deltas, GENESIS_EPOCH, GENESIS_EPOCH);
    final BeaconBlock expectedHead = forkA.getBlockAtSlot(7);
    Bytes32 head = protoArray.findHead(genesisRoot);
    assertThat(head).isEqualTo(expectedHead.hash_tree_root());
  }

  @Test
  public void findHead_ignoreLongerForkNotDescendingFromJustifiedRoot() {
    final ProtoArrayChainBuilder builder = ProtoArrayChainBuilder.createGenesisChain();
    final ProtoArray protoArray = builder.protoArray();

    // Build small chain
    builder.advanceToSlot(5);

    // Create a few forks
    ProtoArrayChainBuilder forkA = builder;
    ProtoArrayChainBuilder forkB = builder.fork();

    // Advance forks to different heights
    forkA.advanceToSlot(7);
    forkB.advanceToSlot(10);

    // Select a justified root that is incompatible with forkB
    final Bytes32 justifiedRoot = forkA.getBlockAtSlot(6).hash_tree_root();

    protoArray.applyScoreChanges(getEmptyScoreDeltas(protoArray), GENESIS_EPOCH, GENESIS_EPOCH);
    final BeaconBlock expectedHead = forkA.getBlockAtSlot(7);
    Bytes32 head = protoArray.findHead(justifiedRoot);
    assertThat(head).isEqualTo(expectedHead.hash_tree_root());
  }

  @Test
  public void findHead_ignoreLongForkWithIncompatibleJustifiedCheckpoint() {
    final ProtoArrayChainBuilder builder = ProtoArrayChainBuilder.createGenesisChain();
    final ProtoArray protoArray = builder.protoArray();
    final Bytes32 genesisRoot = builder.getBlockAtSlot(Constants.GENESIS_SLOT).hash_tree_root();

    // Build small chain
    builder.advanceToSlot(5);

    // Create a few forks
    ProtoArrayChainBuilder forkA = builder;
    ProtoArrayChainBuilder forkB = builder.fork();

    // Build very long fork
    forkB.advanceToSlot(20);
    protoArray.applyScoreChanges(getEmptyScoreDeltas(protoArray), GENESIS_EPOCH, GENESIS_EPOCH);

    // Advance finalized / justified epoch and build other chain from here
    final UnsignedLong newFinalizedEpoch = protoArray.getJustifiedEpoch().plus(UnsignedLong.ONE);
    protoArray.applyScoreChanges(
        getEmptyScoreDeltas(protoArray), newFinalizedEpoch, newFinalizedEpoch);
    forkA.advanceToSlot(10);

    // We should choose the chain that is consistent with the new justified epoch
    protoArray.applyScoreChanges(
        getEmptyScoreDeltas(protoArray), newFinalizedEpoch, newFinalizedEpoch);
    final BeaconBlock expectedHead = forkA.getBlockAtSlot(10);
    Bytes32 head = protoArray.findHead(genesisRoot);
    assertThat(head).isEqualTo(expectedHead.hash_tree_root());
  }

  private List<Long> getEmptyScoreDeltas(final ProtoArray protoArray) {
    return new ArrayList<>(Collections.nCopies(protoArray.getNodes().size(), 0L));
  }
}
