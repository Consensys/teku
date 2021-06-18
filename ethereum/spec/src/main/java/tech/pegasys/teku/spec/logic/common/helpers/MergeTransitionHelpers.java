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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
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

  public boolean isValidTerminalPowBlock(PowBlock powBlock) {
    return false;
  }

  public PowBlock getPowBlock(Bytes32 blockHash) {
    return new PowBlock(blockHash, true, true, UInt256.ZERO, UInt256.ZERO);
  }

  public PowBlock getPowChainHead() {
    return new PowBlock(Bytes32.ZERO, true, true, UInt256.ZERO, UInt256.ZERO);
  }

  public static class PowBlock {
    public final Bytes32 blockHash;
    public final boolean isProcessed;
    public final boolean isValid;
    public final UInt256 totalDifficulty;
    public final UInt256 difficulty;

    public PowBlock(
        Bytes32 blockHash,
        boolean isProcessed,
        boolean isValid,
        UInt256 totalDifficulty,
        UInt256 difficulty) {
      this.blockHash = blockHash;
      this.isProcessed = isProcessed;
      this.isValid = isValid;
      this.totalDifficulty = totalDifficulty;
      this.difficulty = difficulty;
    }
  }
}
