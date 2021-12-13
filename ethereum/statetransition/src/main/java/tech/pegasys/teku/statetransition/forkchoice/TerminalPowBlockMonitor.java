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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
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

  private Optional<Bytes32> maybeBlockHashTracking = Optional.empty();

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
    final Duration pollingPeriod =
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

    if (spec.atSlot(beaconState.getSlot()).miscHelpers().isMergeTransitionComplete(beaconState)) {
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

    SpecConfigMerge specConfigMerge = maybeMergeSpecConfig.get();

    Optional<BeaconState> beaconState = recentChainData.getBestState();
    if (beaconState.isEmpty()) {
      LOG.trace("beaconState not yet available");
      return;
    }

    if (spec.atSlot(beaconState.get().getSlot())
        .miscHelpers()
        .isMergeTransitionComplete(beaconState.get())) {
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
    final SpecVersion specVersion = spec.atSlot(slot);
    final Optional<SpecConfigMerge> maybeSpecConfigMerge = specVersion.getConfig().toVersionMerge();
    if (maybeSpecConfigMerge.isEmpty()) {
      return;
    }
    final SpecConfigMerge specConfigMerge = maybeSpecConfigMerge.get();

    final boolean isActivationEpochReached =
        specVersion
            .miscHelpers()
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
              final Optional<SpecConfigMerge> maybeSpecConfigMerge =
                  recentChainData
                      .getCurrentEpoch()
                      .flatMap(epoch -> spec.atEpoch(epoch).getConfig().toVersionMerge());
              if (maybeSpecConfigMerge.isEmpty()) {
                return SafeFuture.COMPLETE;
              }
              final SpecConfigMerge specConfigMerge = maybeSpecConfigMerge.get();
              UInt256 totalDifficulty = powBlock.getTotalDifficulty();
              if (totalDifficulty.compareTo(specConfigMerge.getTerminalTotalDifficulty()) < 0) {
                LOG.trace("checkTerminalBlockByTTD: Total Terminal Difficulty not reached.");
                return SafeFuture.COMPLETE;
              }

              // TTD is reached
              if (notYetFound(powBlock.getBlockHash())) {
                LOG.trace("checkTerminalBlockByTTD: Terminal Block found!");
                return validateTerminalBlockParentByTTD(specConfigMerge, powBlock)
                    .thenAccept(
                        valid -> {
                          if (valid) {
                            LOG.trace(
                                "Total Difficulty of Terminal Block parent has been validated.");
                            onTerminalPowBlockFound(powBlock.getBlockHash());
                          } else {
                            LOG.error(
                                "Total Difficulty of Terminal Block parent is invalid! Must be lower than TTD!");
                          }
                        });
              }
              return SafeFuture.COMPLETE;
            })
        .finish(error -> LOG.error("Unexpected error while checking TTD", error));
  }

  private SafeFuture<Boolean> validateTerminalBlockParentByTTD(
      final SpecConfigMerge specConfigMerge, final PowBlock terminalBlock) {
    return executionEngine
        .getPowBlock(terminalBlock.getParentHash())
        .thenApply(
            powBlock -> {
              UInt256 totalDifficulty =
                  powBlock
                      .orElseThrow(
                          () -> new IllegalStateException("Terminal Block Parent not found!"))
                      .getTotalDifficulty();
              return totalDifficulty.compareTo(specConfigMerge.getTerminalTotalDifficulty()) < 0;
            });
  }

  private void onTerminalPowBlockFound(Bytes32 blockHash) {
    foundTerminalBlockHash = Optional.of(blockHash);
    forkChoiceNotifier.onTerminalBlockReached(blockHash);
    EVENT_LOG.terminalPowBlockDetected(blockHash);
  }

  private boolean notYetFound(Bytes32 blockHash) {
    return !foundTerminalBlockHash.map(blockHash::equals).orElse(false);
  }
}
