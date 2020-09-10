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

package tech.pegasys.teku.sync.multipeer;

import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.sync.multipeer.chains.FinalizedTargetChainSelector;
import tech.pegasys.teku.sync.multipeer.chains.TargetChains;

public class SyncController {
  private final FinalizedSync finalizedSync;
  private final EventThread eventThread;
  private final FinalizedTargetChainSelector finalizedTargetChainSelector;

  public SyncController(
      final EventThread eventThread,
      final FinalizedTargetChainSelector finalizedTargetChainSelector,
      final FinalizedSync finalizedSync) {
    this.eventThread = eventThread;
    this.finalizedTargetChainSelector = finalizedTargetChainSelector;
    this.finalizedSync = finalizedSync;
  }

  /**
   * Notify Must be called on the sync event thread.
   *
   * @param finalizedChains the currently known finalized chains to consider
   * @param nonfinalizedChains the currently known non-finalized chains to consider
   */
  public void onTargetChainsUpdated(
      final TargetChains finalizedChains, final TargetChains nonfinalizedChains) {
    eventThread.checkOnEventThread();
    finalizedTargetChainSelector
        .selectTargetChain(finalizedChains)
        .ifPresentOrElse(
            finalizedSync::syncToChain, () -> maybeSyncToNonFinalizedChain(nonfinalizedChains));
  }

  private void maybeSyncToNonFinalizedChain(
      @SuppressWarnings("unused") final TargetChains nonfinalizedChains) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
