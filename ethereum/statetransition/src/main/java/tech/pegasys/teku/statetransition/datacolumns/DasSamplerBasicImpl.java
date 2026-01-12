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

package tech.pegasys.teku.statetransition.datacolumns;

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerBasicImpl implements DasSamplerBasic {

  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;

  private final Spec spec;
  private final CurrentSlotProvider currentSlotProvider;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final int maxRecentlySampledBlocks;
  private final ConcurrentHashMap<Bytes32, DataColumnSamplingTracker> recentlySampledColumnsByRoot =
      new ConcurrentHashMap<>();

  private final LinkedHashMap<Bytes32, SignedBeaconBlock> orderedRecentBlocksTracked =
      new LinkedHashMap<>();

  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;

  private final RPCFetchDelayProvider rpcFetchDelayProvider;

  private final boolean halfColumnsSamplingCompletionEnabled;

  public DasSamplerBasicImpl(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final CurrentSlotProvider currentSlotProvider,
      final RPCFetchDelayProvider rpcFetchDelayProvider,
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final CustodyGroupCountManager custodyGroupCountManager,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final int maxRecentlySampledBlocks,
      final boolean halfColumnsSamplingCompletionEnabled) {
    this.currentSlotProvider = currentSlotProvider;
    this.rpcFetchDelayProvider = rpcFetchDelayProvider;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.custody = custody;
    this.retriever = retriever;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.recentChainData = recentChainData;
    this.maxRecentlySampledBlocks = maxRecentlySampledBlocks;
    this.halfColumnsSamplingCompletionEnabled = halfColumnsSamplingCompletionEnabled;
    metricsSystem.createGauge(
        BEACON,
        "das_recently_sampled_blocks_size",
        "DAS recently sampled blocks size",
        () -> (long) recentlySampledColumnsByRoot.size());
  }

  @VisibleForTesting
  Map<Bytes32, DataColumnSamplingTracker> getRecentlySampledColumnsByRoot() {
    return recentlySampledColumnsByRoot;
  }

  /**
   * When syncing or backfilling always make sure to call this method with known DataColumn *before*
   * calling {@link DataAvailabilitySampler#checkDataAvailability(SignedBeaconBlock)} so that RPC
   * fetch won't be executed on those columns.
   */
  @Override
  public void onNewValidatedDataColumnSidecar(
      final DataColumnSlotAndIdentifier columnId, final RemoteOrigin remoteOrigin) {
    LOG.trace("Sampler received data column {} - origin: {}", columnId, remoteOrigin);

    getOrCreateTracker(columnId.slot(), columnId.blockRoot()).add(columnId, remoteOrigin);
  }

  @Override
  public SafeFuture<List<UInt64>> checkDataAvailability(final SignedBeaconBlock beaconBlock) {
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(beaconBlock.getSlot(), beaconBlock.getMessage().hashTreeRoot());

    makeRoomForNewBlock(slotAndBlockRoot.getBlockRoot());
    final DataColumnSamplingTracker tracker =
        getOrCreateTracker(slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot());

    orderedRecentBlocksTracked.put(slotAndBlockRoot.getBlockRoot(), beaconBlock);

    if (tracker.completionFuture().isDone()) {
      return tracker.completionFuture();
    }
    if (tracker.rpcFetchInProgress().compareAndSet(false, true)) {
      fetchMissingColumnsViaRPC(
          slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), tracker);
    }

    return tracker.completionFuture();
  }

  private void onFirstSeen(
      final UInt64 slot, final Bytes32 blockRoot, final DataColumnSamplingTracker tracker) {
    final Duration delay = rpcFetchDelayProvider.calculate(slot);
    if (delay.isZero()) {
      // in case of immediate RPC fetch, let's postpone the actual fetch when checkDataAvailability
      // is called.
      // this is needed because 0 delay means we are syncing\backfilling this slot, so we want to
      // wait eventual known columns to be added via onAlreadyKnownDataColumn before fetching.
      return;
    }
    tracker.rpcFetchInProgress().set(true);
    asyncRunner
        .getDelayedFuture(delay)
        .always(() -> fetchMissingColumnsViaRPC(slot, blockRoot, tracker));
  }

  private void fetchMissingColumnsViaRPC(
      final UInt64 slot, final Bytes32 blockRoot, final DataColumnSamplingTracker tracker) {
    final List<DataColumnSlotAndIdentifier> missingColumns = tracker.getMissingColumnIdentifiers();
    LOG.trace(
        "checkDataAvailability(): missing columns for slot {} root {}: {}",
        slot,
        blockRoot,
        missingColumns.size());

    SafeFuture.collectAll(
            missingColumns.stream().map(id -> retrieveColumnWithSamplingAndCustody(id, tracker)))
        .thenAccept(
            retrievedColumns -> {
              if (retrievedColumns.size() == missingColumns.size()) {
                LOG.trace(
                    "checkDataAvailability(): retrieved remaining {} (of {}) columns via Req/Resp for block {} ({})",
                    retrievedColumns.size(),
                    tracker.samplingRequirement().size(),
                    slot,
                    blockRoot);
              } else {
                throw new IllegalStateException(
                    String.format(
                        "Retrieved only(%d) out of %d missing columns for slot %s (%s) with %d required columns",
                        retrievedColumns.size(),
                        missingColumns.size(),
                        slot,
                        blockRoot,
                        tracker.samplingRequirement().size()));
              }
            })
        // let's reset the fetched flag so that this tracker can reissue RPC requests on DA check
        // retry
        .alwaysRun(() -> tracker.rpcFetchInProgress().set(false))
        .finish(
            throwable -> {
              if (ExceptionUtil.hasCause(throwable, CancellationException.class)) {
                final String error = throwable.getMessage();
                LOG.debug(
                    "CancellationException in checkDataAvailability: {}",
                    () -> error == null ? "<no message>" : error);

              } else {
                LOG.error("data availability check failed", throwable);
              }
            });
  }

  private DataColumnSamplingTracker getOrCreateTracker(final UInt64 slot, final Bytes32 blockRoot) {

    return recentlySampledColumnsByRoot.computeIfAbsent(
        blockRoot,
        k -> {
          final DataColumnSamplingTracker tracker =
              DataColumnSamplingTracker.create(
                  slot,
                  blockRoot,
                  custodyGroupCountManager,
                  halfColumnsSamplingCompletionEnabled
                      ? Optional.of(
                          SpecConfigFulu.required(spec.atSlot(slot).getConfig())
                                  .getNumberOfColumns()
                              / 2)
                      : Optional.empty());
          onFirstSeen(slot, blockRoot, tracker);
          return tracker;
        });
  }

  private void makeRoomForNewBlock(final Bytes32 blockRoot) {
    if (!orderedRecentBlocksTracked.containsKey(blockRoot)) {
      while (orderedRecentBlocksTracked.size() >= maxRecentlySampledBlocks) {
        LOG.debug(
            "Making room for new block in DAS sampler, current size: {}",
            orderedRecentBlocksTracked.size());

        final Bytes32 toRemove = orderedRecentBlocksTracked.pollFirstEntry().getKey();
        LOG.debug("Removing block {}", toRemove);
      }
    }
  }

  private SafeFuture<DataColumnSidecar> retrieveColumnWithSamplingAndCustody(
      final DataColumnSlotAndIdentifier id, final DataColumnSamplingTracker tracker) {
    return retriever
        .retrieve(id)
        .thenPeek(
            sidecar -> {
              if (tracker.add(id, RemoteOrigin.RPC)) {
                // send to custody only if it was added to the tracker
                // (i.e. not received from other sources in the meantime)
                custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC).finishError(LOG);
              }
            });
  }

  @Override
  public void flush() {
    retriever.flush();
  }

  private boolean hasBlobs(final BeaconBlock block) {
    return !block.getBody().getOptionalBlobKzgCommitments().orElseThrow().isEmpty();
  }

  private boolean isInCustodyPeriod(final BeaconBlock block) {
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.atSlot(block.getSlot()).miscHelpers());
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlotProvider.getCurrentSlot());
    return miscHelpersFulu.isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
        currentEpoch, spec.computeEpochAtSlot(block.getSlot()));
  }

  @Override
  public SamplingEligibilityStatus checkSamplingEligibility(final BeaconBlock block) {
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_BEFORE_FULU;
    } else if (!isInCustodyPeriod(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH;
    } else if (!hasBlobs(block)) {
      return SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS;
    } else {
      return SamplingEligibilityStatus.REQUIRED;
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 firstNonFinalizedSlot =
        spec.computeStartSlotAtEpoch(recentChainData.getFinalizedEpoch()).increment();
    recentlySampledColumnsByRoot
        .values()
        .removeIf(
            tracker -> {
              final SlotAndBlockRoot slotAndBlockRoot =
                  new SlotAndBlockRoot(tracker.slot(), tracker.blockRoot());
              if (tracker.slot().isLessThan(firstNonFinalizedSlot)
                  || recentChainData.containsBlock(tracker.blockRoot())) {
                // Outdated
                if (!tracker.completionFuture().isDone()) {
                  // make sure the future releases any pending waiters
                  tracker
                      .completionFuture()
                      .completeExceptionally(
                          new RuntimeException("DAS sampling expired while slot finalized"));
                  // Slot less than finalized slot, but we didn't complete DA check, means it's
                  // probably orphaned block with data never available - we must prune this
                  // RecentChainData contains block, but we are here - shouldn't happen
                  orderedRecentBlocksTracked.remove(slotAndBlockRoot.getBlockRoot());
                  return true;
                }
                final boolean fullySampled = tracker.fullySampled().get();
                if (fullySampled) {
                  orderedRecentBlocksTracked.remove(slotAndBlockRoot.getBlockRoot());
                }
                // cleanup only if fully sampled
                return fullySampled;
              }
              return false;
            });
  }

  @Override
  public boolean containsBlock(final Bytes32 blockRoot) {

    final boolean present = getBlock(blockRoot).isPresent();
    LOG.debug("Checking if we have block {}, {}", blockRoot, present);
    return present;
  }

  @Override
  public Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
    return Optional.ofNullable(orderedRecentBlocksTracked.get(blockRoot));
  }
}
