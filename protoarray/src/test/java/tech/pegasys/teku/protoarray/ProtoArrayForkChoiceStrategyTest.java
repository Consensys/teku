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

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.TestStoreFactory;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class ProtoArrayForkChoiceStrategyTest {
  private final MutableStore store = new TestStoreFactory().createGenesisStore();
  private final SignedBlockAndState genesis =
      store.retrieveBlockAndState(store.getFinalizedCheckpoint().getRoot()).join().get();
  private final ProtoArrayStorageChannel storageChannel = new StubProtoArrayStorageChannel();

  @Test
  public void initialize_withLargeChain() {
    final int chainSize = 2_000;
    saveChainToStore(chainSize);
    final SafeFuture<ProtoArrayForkChoiceStrategy> future =
        ProtoArrayForkChoiceStrategy.initialize(store, storageChannel);

    assertThat(future).isCompleted();
    final ProtoArrayForkChoiceStrategy forkChoiceStrategy = future.join();
    assertThat(forkChoiceStrategy.size()).isEqualTo(chainSize + 1);
  }

  private void saveChainToStore(final int blockCount) {
    final List<SignedBlockAndState> chain = generateChain(blockCount);
    chain.forEach(store::putBlockAndState);
  }

  // Creating mocks is much faster than generating random blocks via DataStructureUtil
  private List<SignedBlockAndState> generateChain(final int count) {
    final List<SignedBlockAndState> chain = new ArrayList<>();

    final Checkpoint checkpoint = store.getFinalizedCheckpoint();
    SignedBlockAndState parent = genesis;
    for (int i = 0; i < count; i++) {
      final UnsignedLong slot = parent.getSlot().plus(ONE);
      final Bytes32 blockHash = Bytes32.fromHexStringLenient("0x" + i);
      final Bytes32 parentRoot = parent.getRoot();

      final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
      when(block.getSlot()).thenReturn(slot);
      when(block.getRoot()).thenReturn(blockHash);
      when(block.hash_tree_root()).thenReturn(blockHash);
      when(block.getParent_root()).thenReturn(parentRoot);
      when(block.getStateRoot()).thenReturn(blockHash);

      final BeaconState state = mock(BeaconState.class);
      when(state.getSlot()).thenReturn(slot);
      when(state.hash_tree_root()).thenReturn(blockHash);
      when(state.getCurrent_justified_checkpoint()).thenReturn(checkpoint);
      when(state.getFinalized_checkpoint()).thenReturn(checkpoint);

      final SignedBlockAndState blockAndState = new SignedBlockAndState(block, state);
      chain.add(blockAndState);

      parent = blockAndState;
    }

    return chain;
  }
}
