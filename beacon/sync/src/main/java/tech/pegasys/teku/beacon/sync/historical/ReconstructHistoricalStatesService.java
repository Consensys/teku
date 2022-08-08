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

package tech.pegasys.teku.beacon.sync.historical;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.IOException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ReconstructHistoricalStatesService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient chainDataClient;
  private final Spec spec;
  private final Optional<String> genesisStateResource;
  private final StorageUpdateChannel storageUpdateChannel;
  private final StatusLogger statusLogger;

  public ReconstructHistoricalStatesService(
      final StorageUpdateChannel storageUpdateChannel,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final Optional<String> genesisStateResource) {
    this(storageUpdateChannel, chainDataClient, spec, genesisStateResource, STATUS_LOG);
  }

  public ReconstructHistoricalStatesService(
      final StorageUpdateChannel storageUpdateChannel,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final Optional<String> genesisStateResource,
      final StatusLogger statusLogger) {
    this.storageUpdateChannel = storageUpdateChannel;
    this.chainDataClient = chainDataClient;
    this.spec = spec;
    this.genesisStateResource = genesisStateResource;
    this.statusLogger = statusLogger;
  }

  @Override
  protected SafeFuture<?> doStart() {
    if (genesisStateResource.isEmpty()) {
      return SafeFuture.failedFuture(
          new IllegalStateException("Genesis state resource not provided"));
    }

    final String resource = genesisStateResource.get();
    final BeaconState genesisState;
    try {
      genesisState = ChainDataLoader.loadState(spec, resource);
    } catch (IOException e) {
      LOG.error("Failed to load initial state", e);
      return SafeFuture.failedFuture(
          new InvalidConfigurationException(
              "Failed to load initial state from " + resource + ": " + e.getMessage()));
    }

    return chainDataClient
        .getInitialAnchor()
        .thenAccept(
            checkpoint -> {
              if (checkpoint.isEmpty()) {
                return;
              }
              applyBlocks(genesisState, checkpoint.get().getEpochStartSlot(spec));
            });
  }

  public void applyBlocks(final BeaconState genesisState, final UInt64 anchorSlot) {
    Context context = new Context(genesisState, SpecConfig.GENESIS_SLOT.plus(1), anchorSlot);
    applyNextBlock(context).finish(statusLogger::reconstructHistoricalStatesServiceFailedProcess);
  }

  private SafeFuture<Void> applyNextBlock(Context context) {
    if (context.checkStopApplyBlock()) {
      statusLogger.reconstructHistoricalStatesServiceComplete();
      return SafeFuture.COMPLETE;
    }

    return chainDataClient
        .getBlockAtSlotExact(context.slot)
        .thenComposeChecked(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                return SafeFuture.COMPLETE;
              }

              final SignedBeaconBlock block = maybeBlock.get();
              context.currentState = spec.replayValidatedBlock(context.currentState, block);
              return storageUpdateChannel.onFinalizedState(context.currentState, block.getRoot());
            })
        .thenRun(context::incrementSlot)
        .thenCompose(__ -> applyNextBlock(context));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  private static class Context {
    private BeaconState currentState;
    private UInt64 slot;
    private final UInt64 anchorSlot;

    Context(BeaconState currentState, UInt64 slot, UInt64 anchorSlot) {
      this.currentState = currentState;
      this.slot = slot;
      this.anchorSlot = anchorSlot;
    }

    private boolean checkStopApplyBlock() {
      return slot.isGreaterThanOrEqualTo(anchorSlot);
    }

    private void incrementSlot() {
      slot = slot.increment();
    }
  }
}
