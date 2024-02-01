/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateTransitionCaches {
  private volatile UInt64 lastBlockRewards = UInt64.ZERO;
  private volatile UInt256 lastBlockExecutionValue = UInt256.ZERO;

  private static final StateTransitionCaches NO_OP_INSTANCE =
      new StateTransitionCaches() {
        @Override
        public StateTransitionCaches copy() {
          return this;
        }
      };

  private StateTransitionCaches(
      final UInt64 lastBlockRewards, final UInt256 lastBlockExecutionValue) {
    this.lastBlockRewards = lastBlockRewards;
    this.lastBlockExecutionValue = lastBlockExecutionValue;
  }

  private StateTransitionCaches() {}

  /** Creates new instance with clean caches */
  public static StateTransitionCaches createNewEmpty() {
    return new StateTransitionCaches();
  }

  /** Returns the instance which doesn't cache anything */
  public static StateTransitionCaches getNoOp() {
    return NO_OP_INSTANCE;
  }

  public UInt64 getLastBlockRewards() {
    return lastBlockRewards;
  }

  public UInt256 getLastBlockExecutionValue() {
    return lastBlockExecutionValue;
  }

  public void increaseLastBlockRewards(final UInt64 delta) {
    // state transition is single threaded, so no need to do an atomic update
    this.lastBlockRewards = this.lastBlockRewards.plus(delta);
  }

  public void setLastBlockExecutionValue(final UInt256 lastBlockExecutionValue) {
    this.lastBlockExecutionValue = lastBlockExecutionValue;
  }

  public StateTransitionCaches copy() {
    return new StateTransitionCaches(lastBlockRewards, lastBlockExecutionValue);
  }

  // Called at the end of every slot transition (processSlot)
  // Note: this is always called before the block is processed (if block is proposed)
  public void onSlotProcessed() {
    this.lastBlockRewards = UInt64.ZERO;
    this.lastBlockExecutionValue = UInt256.ZERO;
  }

  // Called at the end of every slot transition (processBlock)
  // this can be used to free cached data that are only used during the state transition and can be
  // garbage collected
  public void onBlockProcessed() {}
}
