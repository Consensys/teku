/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.helpers;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.TransitionStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MiscHelpersMerge;

public class MergeTransitionHelpers {

  private final MiscHelpersMerge miscHelpers;

  public MergeTransitionHelpers(MiscHelpersMerge miscHelpers) {
    this.miscHelpers = miscHelpers;
  }

  public boolean isMergeComplete(BeaconState state) {
    return miscHelpers.isMergeComplete(state);
  }

  public boolean isMergeBlock(BeaconState state, BeaconBlock block) {
    return miscHelpers.isMergeBlock(state, block);
  }

  public boolean isValidTerminalPowBlock(PowBlock powBlock, TransitionStore transitionStore) {
    boolean isTotalDifficultyReached =
        powBlock.totalDifficulty.compareTo(transitionStore.getTransitionTotalDifficulty()) >= 0;
    return powBlock.isValid && isTotalDifficultyReached;
  }

  public PowBlock getPowBlock(ExecutionEngineChannel executionEngineChannel, Bytes32 blockHash) {
    return executionEngineChannel
        .getPowBlock(blockHash)
        .join()
        .map(PowBlock::new)
        .orElse(new PowBlock(blockHash, false, false, UInt256.ZERO, UInt256.ZERO));
  }

  public PowBlock getPowChainHead(ExecutionEngineChannel executionEngineChannel) {
    return new PowBlock(executionEngineChannel.getPowChainHead().join());
  }
}
