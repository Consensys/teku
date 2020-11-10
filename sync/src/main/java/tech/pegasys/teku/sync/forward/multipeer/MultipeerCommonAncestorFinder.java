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

package tech.pegasys.teku.sync.forward.multipeer;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.sync.forward.singlepeer.CommonAncestor;

public class MultipeerCommonAncestorFinder {
  private static final Logger LOG = LogManager.getLogger();
  private final RecentChainData recentChainData;
  private final CommonAncestor commonAncestorFinder;
  private final EventThread eventThread;

  @VisibleForTesting
  MultipeerCommonAncestorFinder(
      final RecentChainData recentChainData,
      final CommonAncestor commonAncestorFinder,
      final EventThread eventThread) {
    this.recentChainData = recentChainData;
    this.commonAncestorFinder = commonAncestorFinder;
    this.eventThread = eventThread;
  }

  public static MultipeerCommonAncestorFinder create(
      final RecentChainData recentChainData, final EventThread eventThread) {
    return new MultipeerCommonAncestorFinder(
        recentChainData, new CommonAncestor(recentChainData), eventThread);
  }

  public SafeFuture<UInt64> findCommonAncestor(final TargetChain targetChain) {
    eventThread.checkOnEventThread();
    final UInt64 latestFinalizedSlot =
        compute_start_slot_at_epoch(recentChainData.getFinalizedEpoch());
    if (targetChain.getPeerCount() == 0) {
      // No sources to find a common ancestor with, assume its the finalized slot
      return SafeFuture.completedFuture(latestFinalizedSlot);
    }

    return findCommonAncestor(latestFinalizedSlot, targetChain)
        .thenPeek(ancestor -> LOG.trace("Found common ancestor at slot {}", ancestor));
  }

  private SafeFuture<UInt64> findCommonAncestor(
      final UInt64 latestFinalizedSlot, final TargetChain targetChain) {
    eventThread.checkOnEventThread();
    final SyncSource source1 = targetChain.selectRandomPeer().orElseThrow();
    final Optional<SyncSource> source2 = targetChain.selectRandomPeer(source1);
    // Only one peer available, just go with it's common ancestor
    final SafeFuture<UInt64> source1CommonAncestor =
        commonAncestorFinder.getCommonAncestor(
            source1, latestFinalizedSlot, targetChain.getChainHead().getSlot());
    if (source2.isEmpty()) {
      LOG.trace("Finding common ancestor from one peer");
      return source1CommonAncestor;
    }
    LOG.trace("Finding common ancestor from two peers");
    // Two peers available, so check they have the same common ancestor
    return source1CommonAncestor
        .thenCombineAsync(
            commonAncestorFinder.getCommonAncestor(
                source2.get(), latestFinalizedSlot, targetChain.getChainHead().getSlot()),
            (commonAncestor1, commonAncestor2) ->
                verifyResultsMatch(
                    latestFinalizedSlot, targetChain, commonAncestor1, commonAncestor2),
            eventThread)
        .exceptionally(
            error -> {
              LOG.debug("Failed to find common ancestor. Starting sync from finalized slot", error);
              return latestFinalizedSlot;
            });
  }

  private UInt64 verifyResultsMatch(
      final UInt64 latestFinalizedSlot,
      final TargetChain targetChain,
      final UInt64 commonAncestor1,
      final UInt64 commonAncestor2) {
    eventThread.checkOnEventThread();
    if (commonAncestor1.equals(commonAncestor2)) {
      LOG.trace("Found consistent common ancestor at slot {}", commonAncestor1);
      return commonAncestor1;
    }
    LOG.warn(
        "Found different common ancestors for target chain {}. Starting sync from finalized checkpoint",
        targetChain);
    return latestFinalizedSlot;
  }
}
