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

package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceStateProvider {
  private final EventThread forkChoiceExecutor;
  private final RecentChainData recentChainData;

  public ForkChoiceStateProvider(EventThread forkChoiceExecutor, RecentChainData recentChainData) {
    this.forkChoiceExecutor = forkChoiceExecutor;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<ForkChoiceState> getForkChoiceStateAsync() {
    return forkChoiceExecutor.execute(this::internalGetForkChoiceState);
  }

  public ForkChoiceState getForkChoiceStateSync() {
    forkChoiceExecutor.checkOnEventThread();
    return internalGetForkChoiceState();
  }

  private ForkChoiceState internalGetForkChoiceState() {
    return recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow()
        .getForkChoiceState(
            recentChainData.getCurrentEpoch().orElseThrow(),
            recentChainData.getJustifiedCheckpoint().orElseThrow(),
            recentChainData.getFinalizedCheckpoint().orElseThrow());
  }
}
