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

package tech.pegasys.teku.dataproviders.generators;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;

class ChainStateGenerator {
  private final BlockProcessor blockProcessor;
  private final List<SignedBeaconBlock> chain;
  private final BeaconState baseState;
  private final ExecutionEngineChannel executionEngineChannel;

  private ChainStateGenerator(
      final Spec spec,
      final List<SignedBeaconBlock> chain,
      final BeaconState baseState,
      final boolean skipValidation,
      ExecutionEngineChannel executionEngineChannel) {
    this.executionEngineChannel = executionEngineChannel;
    if (!skipValidation) {
      for (int i = chain.size() - 1; i > 0; i--) {
        checkArgument(
            chain.get(i).getParentRoot().equals(chain.get(i - 1).getRoot()),
            "Blocks must form an ordered chain");
      }
    }

    this.chain = chain;
    this.baseState = baseState;
    this.blockProcessor = new BlockProcessor(spec);
  }

  /**
   * Create a chain generator that can replay the given blocks on top of the base state.
   *
   * @param chain A sorted chain of blocks in ascending order by slot
   * @param baseState A base state corresponding to the first block in the chain
   * @return
   */
  public static ChainStateGenerator create(
      final Spec spec,
      final List<SignedBeaconBlock> chain,
      final BeaconState baseState,
      ExecutionEngineChannel executionEngineChannel) {
    return create(spec, chain, baseState, false, executionEngineChannel);
  }

  static ChainStateGenerator create(
      final Spec spec,
      final List<SignedBeaconBlock> chain,
      final BeaconState baseState,
      final boolean skipValidation,
      ExecutionEngineChannel executionEngineChannel) {
    return new ChainStateGenerator(spec, chain, baseState, skipValidation, executionEngineChannel);
  }

  public void generateStates(final StateHandler handler) {
    // Process blocks in order
    BeaconState state = baseState;
    for (SignedBeaconBlock currentBlock : chain) {
      if (currentBlock.getStateRoot().equals(baseState.hashTreeRoot())) {
        // Don't process base block
        handler.handle(new SignedBlockAndState(currentBlock, baseState));
        continue;
      }
      state = blockProcessor.process(executionEngineChannel, state, currentBlock);
      handler.handle(new SignedBlockAndState(currentBlock, state));
    }
  }
}
