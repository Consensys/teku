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

import java.io.IOException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ReconstructHistoricalStatesService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final CombinedChainDataClient chainDataClient;
  private final Spec spec;
  private final Optional<String> genesisStateResource;
  private final StorageUpdateChannel storageUpdateChannel;

  public ReconstructHistoricalStatesService(
      final StorageUpdateChannel storageUpdateChannel,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final Optional<String> genesisStateResource) {
    this.storageUpdateChannel = storageUpdateChannel;
    this.chainDataClient = chainDataClient;
    this.spec = spec;
    this.genesisStateResource = genesisStateResource;
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

    final SafeFuture<Optional<Checkpoint>> future = chainDataClient.getInitialAnchor();
    future
        .thenAccept(
            checkpoint -> {
              if (checkpoint.isEmpty()) {
                return; // todo confirm skip
              }

              final UInt64 anchorSlot = checkpoint.get().getEpochStartSlot(spec);
              applyBlocks(genesisState, 100, anchorSlot);
            })
        .finish(LOG::error); // todo ignores return value

    return SafeFuture.COMPLETE; // todo check
  }

  public void applyBlocks(
      final BeaconState genesisState, final int dataStorageFrequency, final UInt64 anchorSlot) {
    Context context =
        new Context(genesisState, UInt64.ZERO, UInt64.ONE, dataStorageFrequency, anchorSlot);
    applyNextBlock(context).finish(LOG::error); // todo ignores return value
  }

  private SafeFuture<Void> applyNextBlock(Context context) {
    if (context.slot.isGreaterThanOrEqualTo(context.anchorSlot)) {
      return SafeFuture.COMPLETE;
    }

    return chainDataClient
        .getBlockAtSlotExact(context.slot)
        .thenAccept(
            block -> {
              try {
                if (block.isEmpty()) {
                  return;
                }

                context.currentState =
                    spec.replayValidatedBlock(context.currentState, block.orElseThrow());
                if (context.checkStoringHistoricalStates()) {
                  storageUpdateChannel
                      .onFinalizedState(context.currentState)
                      .finish(LOG::error); // todo ignores return value
                }

                context.incrementSlot();

              } catch (StateTransitionException e) {
                LOG.error(e); // todo check approach - crash
              }
            })
        .thenCompose(__ -> applyNextBlock(context));
  }

  @Override
  protected SafeFuture<?> doStop() {
    return null;
  }

  private static class Context {
    private BeaconState currentState;
    private final UInt64 lastStoredSlot;
    private UInt64 slot;
    private final int dataStorageFrequency;
    private final UInt64 anchorSlot;

    Context(
        BeaconState currentState,
        UInt64 lastStoredSlot,
        UInt64 slot,
        int dataStorageFrequency,
        UInt64 anchorSlot) {
      this.currentState = currentState;
      this.lastStoredSlot = lastStoredSlot;
      this.slot = slot;
      this.dataStorageFrequency = dataStorageFrequency;
      this.anchorSlot = anchorSlot;
    }

    private boolean checkStoringHistoricalStates() {
      return lastStoredSlot.plus(dataStorageFrequency).isLessThan(currentState.getSlot());
    }

    private void incrementSlot() {
      slot = slot.increment();
    }
  }
}
