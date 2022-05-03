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

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class TerminalPowBlockMonitor {
  private static final Logger LOG = LogManager.getLogger();
  // number of samples to average out totalDifficulty
  private static final int TD_DIFF_SAMPLES = 5;
  // how many times
  private static final int ETA_EVENT_FREQUENCY_IN_POLLING_PERIODS = 5;

  private final EventLogger eventLogger;
  private final TimeProvider timeProvider;
  private final ExecutionEngineChannel executionEngine;
  private final AsyncRunner asyncRunner;
  private Optional<Cancellable> timer = Optional.empty();
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final ForkChoiceNotifier forkChoiceNotifier;

  private Duration pollingPeriod;
  private int pollingCounter = 0;

  private Optional<Bytes32> maybeBlockHashTracking = Optional.empty();
  private Optional<Bytes32> foundTerminalBlockHash = Optional.empty();
  private SpecConfigBellatrix specConfigBellatrix;
  private boolean isBellatrixActive = false;
  private boolean inSync = true;

  private UInt256 lastTotalDifficulty;
  private final ArrayDeque<UInt256> lastTotalDifficultyDiffs = new ArrayDeque<>();

  public TerminalPowBlockMonitor(
      final ExecutionEngineChannel executionEngine,
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final AsyncRunner asyncRunner,
      final EventLogger eventLogger,
      final TimeProvider timeProvider) {
    this.executionEngine = executionEngine;
    this.asyncRunner = asyncRunner;
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.eventLogger = eventLogger;
    this.timeProvider = timeProvider;
  }

  public synchronized void start() {
    if (timer.isPresent()) {
      return;
    }

    Optional<SpecConfigBellatrix> maybeSpecConfigBellatrix = getSpecConfigBellatrix();
    if (maybeSpecConfigBellatrix.isEmpty()) {
      LOG.error("Bellatrix spec config not found. Monitor will shutdown.");
      stop();
      return;
    }
    specConfigBellatrix = maybeSpecConfigBellatrix.get();

    pollingPeriod = Duration.ofSeconds(spec.getGenesisSpec().getConfig().getSecondsPerEth1Block());
    timer =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::monitor,
                pollingPeriod,
                (error) -> LOG.error("An error occurred while executing the monitor task", error)));
    LOG.info(
        "Monitor has started. Waiting BELLATRIX fork activation. Polling every {}", pollingPeriod);
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

  public synchronized void onNodeSyncStateChanged(final boolean inSync) {
    this.inSync = inSync;
  }

  private synchronized void monitor() {
    if (!isBellatrixActive) {
      initMergeState();
      if (!isBellatrixActive) {
        LOG.trace("Monitor is sill inactive. BELLATRIX fork not yet activated.");
        return;
      }
    }

    pollingCounter++;

    if (isMergeTransitionComplete(recentChainData.getChainHead())) {
      LOG.info("MERGE is completed. Stopping.");
      stop();
      return;
    }

    if (!inSync) {
      LOG.debug("Node is syncing, skipping check.");
      return;
    }

    maybeBlockHashTracking.ifPresentOrElse(
        this::checkTerminalBlockByBlockHash, this::checkTerminalBlockByTTD);
  }

  private Boolean isMergeTransitionComplete(final Optional<ChainHead> chainHead) {
    return chainHead.map(head -> !head.getExecutionBlockHash().isZero()).orElse(false);
  }

  private void initMergeState() {
    Optional<UInt64> maybeEpoch = recentChainData.getCurrentEpoch();
    if (maybeEpoch.isEmpty()) {
      return;
    }

    if (specConfigBellatrix.getBellatrixForkEpoch().isGreaterThan(maybeEpoch.get())) {
      LOG.trace("Bellatrix not yet activated");
      return;
    }

    final Optional<ChainHead> chainHead = recentChainData.getChainHead();
    if (chainHead.isEmpty()) {
      LOG.trace("Beacon state not yet available");
      return;
    }

    if (isMergeTransitionComplete(chainHead)) {
      LOG.info("MERGE is completed. Stopping.");
      stop();
      return;
    }

    if (specConfigBellatrix.getTerminalBlockHash().isZero()) {
      maybeBlockHashTracking = Optional.empty();
      LOG.info(
          "Enabling tracking by Block Total Difficulty {}",
          specConfigBellatrix.getTerminalTotalDifficulty());
    } else {
      maybeBlockHashTracking = Optional.of(specConfigBellatrix.getTerminalBlockHash());
      LOG.info(
          "Enabling tracking by Block Hash {} and Activation Epoch {}",
          specConfigBellatrix.getTerminalBlockHash(),
          specConfigBellatrix.getTerminalBlockHashActivationEpoch());
    }

    isBellatrixActive = true;
    LOG.info("Monitor is now active");
  }

  private void checkTerminalBlockByBlockHash(final Bytes32 blockHashTracking) {
    final UInt64 slot = recentChainData.getCurrentSlot().orElseThrow();
    final SpecVersion specVersion = spec.atSlot(slot);

    final boolean isActivationEpochReached =
        specVersion
            .miscHelpers()
            .computeEpochAtSlot(slot)
            .isGreaterThanOrEqualTo(specConfigBellatrix.getTerminalBlockHashActivationEpoch());

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
                            if (!found) {
                              LOG.trace("checkTerminalBlockByBlockHash: Terminal Block not found.");
                              return;
                            }

                            if (notYetFound(blockHashTracking)) {
                              LOG.trace("checkTerminalBlockByBlockHash: Terminal Block found!");
                              onTerminalPowBlockFound(blockHashTracking);
                            }
                          }))
          .finish(
              error -> LOG.error("Unexpected error while searching Terminal Block by Hash", error));
    }
  }

  private void checkTerminalBlockByTTD() {
    executionEngine
        .getPowChainHead()
        .thenCompose(
            powBlock -> {
              final UInt256 totalDifficulty = powBlock.getTotalDifficulty();
              if (totalDifficulty.compareTo(specConfigBellatrix.getTerminalTotalDifficulty()) < 0) {
                LOG.trace("checkTerminalBlockByTTD: Total Terminal Difficulty not reached.");
                checkTtdEta(totalDifficulty);
                return SafeFuture.COMPLETE;
              }
              if (powBlock.getBlockTimestamp().isGreaterThan(timeProvider.getTimeInSeconds())) {
                LOG.trace("checkTerminalBlockByTTD: Chain head is in the future, ignoring for now");
                return SafeFuture.COMPLETE;
              }

              // TTD is reached
              if (notYetFound(powBlock.getBlockHash())) {
                LOG.trace("checkTerminalBlockByTTD: Terminal Block found!");
                return validateTerminalBlockParentByTTD(powBlock)
                    .thenAccept(
                        valid -> {
                          if (valid) {
                            LOG.trace(
                                "Total Difficulty of Terminal Block parent has been validated.");
                            onTerminalPowBlockFound(powBlock.getBlockHash());
                          } else {
                            LOG.warn(
                                "A candidate Terminal Block has been found but its parent has a Total Difficulty greater than terminal total difficulty. "
                                    + "It is likely the Terminal Block has been already chosen by the network and The Merge will complete shortly.");
                          }
                        });
              }
              return SafeFuture.COMPLETE;
            })
        .finish(error -> LOG.error("Unexpected error while checking TTD", error));
  }

  private SafeFuture<Boolean> validateTerminalBlockParentByTTD(final PowBlock terminalBlock) {
    return executionEngine
        .getPowBlock(terminalBlock.getParentHash())
        .thenApply(
            powBlock -> {
              UInt256 totalDifficulty =
                  powBlock
                      .orElseThrow(
                          () -> new IllegalStateException("Terminal Block Parent not found!"))
                      .getTotalDifficulty();
              return totalDifficulty.compareTo(specConfigBellatrix.getTerminalTotalDifficulty())
                  < 0;
            });
  }

  private void checkTtdEta(final UInt256 totalDifficulty) {
    if (lastTotalDifficulty != null) {
      final UInt256 diff = totalDifficulty.subtract(lastTotalDifficulty);
      lastTotalDifficultyDiffs.addFirst(diff);
      if (lastTotalDifficultyDiffs.size() > TD_DIFF_SAMPLES) {
        lastTotalDifficultyDiffs.removeLast();
      }
      if (pollingCounter % ETA_EVENT_FREQUENCY_IN_POLLING_PERIODS == 0) {
        final UInt256 diffAverage =
            lastTotalDifficultyDiffs.stream()
                .reduce(UInt256::add)
                .orElse(UInt256.ZERO)
                .divide(lastTotalDifficultyDiffs.size());
        final UInt256 averageDifficultyPerSecond = diffAverage.divide(pollingPeriod.getSeconds());
        final UInt256 ttd = specConfigBellatrix.getTerminalTotalDifficulty();

        if (averageDifficultyPerSecond.isZero()) {
          eventLogger.terminalPowBlockTtdEta(ttd, Optional.empty());
        } else {
          final UInt256 secondsToTTD =
              ttd.subtract(totalDifficulty).divide(averageDifficultyPerSecond);

          final Duration eta = Duration.ofSeconds(secondsToTTD.toLong());

          eventLogger.terminalPowBlockTtdEta(ttd, Optional.of(eta));
        }
      }
    }

    lastTotalDifficulty = totalDifficulty;
  }

  private void onTerminalPowBlockFound(Bytes32 blockHash) {
    foundTerminalBlockHash = Optional.of(blockHash);
    forkChoiceNotifier.onTerminalBlockReached(blockHash);
    eventLogger.terminalPowBlockDetected(blockHash);
  }

  private boolean notYetFound(Bytes32 blockHash) {
    return !foundTerminalBlockHash.map(blockHash::equals).orElse(false);
  }

  private Optional<SpecConfigBellatrix> getSpecConfigBellatrix() {
    final SpecVersion bellatrixMilestone = spec.forMilestone(SpecMilestone.BELLATRIX);
    if (bellatrixMilestone == null) {
      return Optional.empty();
    }
    return bellatrixMilestone.getConfig().toVersionBellatrix();
  }
}
