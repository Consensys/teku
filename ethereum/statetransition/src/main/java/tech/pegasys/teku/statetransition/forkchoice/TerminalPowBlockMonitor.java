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

package tech.pegasys.teku.statetransition.forkchoice;

import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.storage.client.RecentChainData;

public class TerminalPowBlockMonitor {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineChannel executionEngine;
  private final AsyncRunner asyncRunner;
  private Optional<Cancellable> timer = Optional.empty();
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final ForkChoiceNotifier forkChoiceNotifier;

  private final AtomicBoolean isMerge = new AtomicBoolean(false);

  private MiscHelpers miscHelpers;
  private SpecConfigMerge specConfigMerge;

  private Optional<Bytes32> maybeBlockHashTracking = Optional.empty();
  private Duration pollingPeriod;

  private Optional<Bytes32> foundTerminalBlockHash = Optional.empty();

  public TerminalPowBlockMonitor(
      ExecutionEngineChannel executionEngine,
      Spec spec,
      RecentChainData recentChainData,
      ForkChoiceNotifier forkChoiceNotifier,
      AsyncRunner asyncRunner) {
    this.executionEngine = executionEngine;
    this.asyncRunner = asyncRunner;
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.forkChoiceNotifier = forkChoiceNotifier;
  }

  public synchronized void start() {
    if (timer.isPresent()) {
      return;
    }
    pollingPeriod =
        Duration.ofSeconds(spec.getGenesisSpec().getConfig().getSecondsPerEth1Block().longValue());
    timer =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::monitor,
                pollingPeriod,
                (error) -> LOG.error("An error occurred while executing the monitor task", error)));
    LOG.info("Monitor has started. Waiting MERGE fork activation. Polling every {}", pollingPeriod);
  }

  public synchronized void stop() {
    if (timer.isEmpty()) {
      return;
    }
    timer.get().cancel();
    timer = Optional.empty();
    LOG.info("Monitor has stopped");
  }

  public synchronized boolean isRunning() {
    return timer.isPresent();
  }

  private synchronized void monitor() {
    if (!isMerge.get()) {
      initMergeState();
      if (!isMerge.get()) {
        LOG.trace("Monitor is sill inactive. MERGE fork not yet activated.");
        return;
      }
    }

    // beaconState must be available at this stage
    BeaconState beaconState = recentChainData.getBestState().orElseThrow();

    if (miscHelpers.isMergeComplete(beaconState)) {
      LOG.info("MERGE is completed. Stopping.");
      stop();
      return;
    }

    maybeBlockHashTracking.ifPresentOrElse(
        this::checkTerminalBlockByBlockHash, this::checkTerminalBlockByTTD);
  }

  private void initMergeState() {
    Optional<UInt64> maybeSlot = recentChainData.getCurrentSlot();
    if (maybeSlot.isEmpty()) {
      return;
    }

    UInt64 epoch = spec.computeEpochAtSlot(maybeSlot.get());
    Optional<SpecConfigMerge> maybeMergeSpecConfig = spec.getSpecConfig(epoch).toVersionMerge();
    if (maybeMergeSpecConfig.isEmpty()
        || maybeMergeSpecConfig.get().getMergeForkEpoch().isGreaterThan(epoch)) {
      return;
    }

    specConfigMerge = maybeMergeSpecConfig.get();
    miscHelpers = spec.atSlot(maybeSlot.get()).miscHelpers();

    Optional<BeaconState> beaconState = recentChainData.getBestState();
    if (beaconState.isEmpty()) {
      LOG.trace("beaconState not yet available");
      return;
    }

    if (miscHelpers.isMergeComplete(beaconState.get())) {
      LOG.info("MERGE is completed. Stopping.");
      stop();
      return;
    }

    if (specConfigMerge.getTerminalBlockHash().isZero()) {
      maybeBlockHashTracking = Optional.empty();
      LOG.info(
          "Enabling tracking by Block Total Difficulty {}",
          specConfigMerge.getTerminalTotalDifficulty());
    } else {
      maybeBlockHashTracking = Optional.of(specConfigMerge.getTerminalBlockHash());
      LOG.info(
          "Enabling tracking by Block Hash {} and Activation Epoch {}",
          specConfigMerge.getTerminalBlockHash(),
          specConfigMerge.getTerminalBlockHashActivationEpoch());
    }

    isMerge.set(true);
    LOG.info("Monitor is now active");
  }

  private void checkTerminalBlockByBlockHash(Bytes32 blockHashTracking) {
    UInt64 slot = recentChainData.getCurrentSlot().orElseThrow();

    final boolean isActivationEpochReached =
        miscHelpers
            .computeEpochAtSlot(slot)
            .isGreaterThanOrEqualTo(specConfigMerge.getTerminalBlockHashActivationEpoch());

    if (isActivationEpochReached) {
      executionEngine
          .getPowBlock(blockHashTracking)
          .thenAccept(
              maybePowBlock ->
                  maybePowBlock
                      .map(PowBlock::getBlockHash)
                      .map(blockHashTracking::equals)
                      .ifPresent(
                          found -> {
                            if (found) {
                              LOG.trace("checkTerminalBlockByBlockHash: Terminal Block found!");
                              onTerminalPowBlockFound(blockHashTracking);
                            } else {
                              LOG.trace("checkTerminalBlockByBlockHash: Terminal Block not found.");
                            }
                          }))
          .finish(
              error -> LOG.error("Unexpected error while searching Terminal Block by Hash", error));
    }
  }

  private void checkTerminalBlockByTTD() {
    executionEngine
        .getPowChainHead()
        .thenAccept(
            powBlock -> {
              UInt256 difficulty = powBlock.getTotalDifficulty();
              if (difficulty.compareTo(specConfigMerge.getTerminalTotalDifficulty()) >= 0) {
                LOG.trace("checkTerminalBlockByTTD: Terminal Block found!");
                onTerminalPowBlockFound(powBlock.getBlockHash());
              } else {
                LOG.trace("checkTerminalBlockByTTD: Terminal Block not found.");
              }
            })
        .finish(error -> LOG.error("Unexpected error while checking TTD", error));
  }

  private void onTerminalPowBlockFound(Bytes32 blockHash) {
    if (foundTerminalBlockHash.map(blockHash::equals).orElse(true)) {
      foundTerminalBlockHash = Optional.of(blockHash);
      forkChoiceNotifier.onTerminalBlockReached(blockHash);
      EVENT_LOG.terminalPowBlockDetected(blockHash);
    }
  }
}
