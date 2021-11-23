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

package tech.pegasys.teku.services.beaconchain;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.pow.api.TerminalPowBlockMonitorChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MiscHelpersMerge;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;

public class TerminalPowBlockMonitorService {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineChannel executionEngine;
  private final AsyncRunner asyncRunner;
  private final TerminalPowBlockMonitorChannel terminalPowBlockMonitorChannel;
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private final Spec spec;
  private final RecentChainData recentChainData;

  private final AtomicBoolean isMerge = new AtomicBoolean(false);

  private MiscHelpersMerge miscHelpers;
  private SpecConfigMerge specConfigMerge;

  private Optional<Bytes32> maybeBlockHashTracking = Optional.empty();

  public TerminalPowBlockMonitorService(
      ExecutionEngineChannel executionEngine,
      TerminalPowBlockMonitorChannel terminalPowBlockMonitorChannel,
      Spec spec,
      RecentChainData recentChainData,
      AsyncRunner asyncRunner) {
    this.executionEngine = executionEngine;
    this.asyncRunner = asyncRunner;
    this.terminalPowBlockMonitorChannel = terminalPowBlockMonitorChannel;
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  protected void start() {
    monitor();
  }

  protected void stop() {
    stopped.set(true);
  }

  private void monitor() {
    if (stopped.get()) {
      return;
    }

    doMonitor();

    asyncRunner
        .runAfterDelay(this::monitor, Constants.TTD_MONITOR_SERVICE_POLL_INTERVAL)
        .reportExceptions();
  }

  private void doMonitor() {
    if (!isMerge.get()) {
      initMergeState();
      if (!isMerge.get()) {
        return;
      }
    }

    Optional<BeaconState> beaconState = recentChainData.getBestState();
    if (beaconState.isEmpty()) {
      return;
    }

    if (miscHelpers.isMergeComplete(beaconState.get())) {
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

    Optional<SpecConfigMerge> maybeMergeSpecConfig =
        spec.getSpecConfig(spec.computeEpochAtSlot(maybeSlot.get())).toVersionMerge();
    if (maybeMergeSpecConfig.isEmpty()) {
      return;
    }

    specConfigMerge = maybeMergeSpecConfig.get();
    miscHelpers = ((MiscHelpersMerge) spec.atSlot(maybeSlot.get()).miscHelpers());

    Optional<BeaconState> beaconState = recentChainData.getBestState();
    if (beaconState.isEmpty()) {
      return;
    }

    if (specConfigMerge.getTerminalBlockHash().isZero()) {
      maybeBlockHashTracking = Optional.empty();
    } else {
      maybeBlockHashTracking = Optional.of(specConfigMerge.getTerminalBlockHash());
    }

    isMerge.set(true);
  }

  private void checkTerminalBlockByBlockHash(Bytes32 blockHashTracking) {
    Optional<UInt64> slot = recentChainData.getCurrentSlot();
    if (slot.isEmpty()) {
      return;
    }

    final boolean isActivationEpochReached =
        miscHelpers
            .computeEpochAtSlot(slot.get())
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
        .thenAccept(
            powBlock -> {
              UInt256 difficulty = powBlock.getTotalDifficulty();
              if (difficulty.compareTo(specConfigMerge.getTerminalTotalDifficulty()) < 0) {
                onTerminalPowBlockFound(powBlock.getBlockHash());
              }
            })
        .finish(error -> LOG.error("Unexpected error while checking TTD", error));
  }

  private void onTerminalPowBlockFound(Bytes32 blockHash) {
    terminalPowBlockMonitorChannel.onTerminalPowBlockReached(blockHash);
    stop();
  }
}
