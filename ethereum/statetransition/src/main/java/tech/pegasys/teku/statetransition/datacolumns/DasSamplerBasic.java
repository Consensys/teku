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

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerBasic implements DataAvailabilitySampler, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarCustody custody;
  private final DataColumnSidecarRetriever retriever;

  private final Spec spec;
  private final CurrentSlotProvider currentSlotProvider;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final Map<Bytes32, DataColumnSamplingTracker> recentlySampledColumnsByRoot =
      new ConcurrentHashMap<>();
  private final RecentChainData recentChainData;

  private final AsyncRunner asyncRunner;
  private final RPCFetchDelayProvider rpcFetchDelayProvider;

  public DasSamplerBasic(
      final Spec spec,
      final CurrentSlotProvider currentSlotProvider,
      final RPCFetchDelayProvider rpcFetchDelayProvider,
      final AsyncRunner asyncRunner,
      final DataColumnSidecarCustody custody,
      final DataColumnSidecarRetriever retriever,
      final CustodyGroupCountManager custodyGroupCountManager,
      final RecentChainData recentChainData) {
    this.currentSlotProvider = currentSlotProvider;
    this.rpcFetchDelayProvider = rpcFetchDelayProvider;
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.custody = custody;
    this.retriever = retriever;
    this.custodyGroupCountManager = custodyGroupCountManager;
    this.recentChainData = recentChainData;
  }

  @VisibleForTesting
  Map<Bytes32, DataColumnSamplingTracker> getRecentlySampledColumnsByRoot() {
    return recentlySampledColumnsByRoot;
  }

  @Override
  public void onAlreadyKnownDataColumn(
      final DataColumnSlotAndIdentifier columnId, final RemoteOrigin remoteOrigin) {
    LOG.debug("Sampler received data column {} - origin: {}", columnId, remoteOrigin);

    getOrCreateTracker(columnId.slot(), columnId.blockRoot()).add(columnId, remoteOrigin);
  }

  public void onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final RemoteOrigin remoteOrigin) {
    onAlreadyKnownDataColumn(
        DataColumnSlotAndIdentifier.fromDataColumn(dataColumnSidecar), remoteOrigin);
  }

  @Override
  public SafeFuture<List<UInt64>> checkDataAvailability(
      final UInt64 slot, final Bytes32 blockRoot) {

    final DataColumnSamplingTracker tracker = getOrCreateTracker(slot, blockRoot);

    asyncRunner
        .getDelayedFuture(rpcFetchDelayProvider.calulate(slot))
        .always(
            () -> {
              final List<DataColumnSlotAndIdentifier> missingColumns =
                  tracker.getMissingColumnIdentifiers();
              LOG.info(
                  "checkDataAvailability(): missing columns for slot {} root {}: {}",
                  slot,
                  blockRoot,
                  missingColumns.size());

              SafeFuture.collectAll(
                      missingColumns.stream()
                          .map(id -> retrieveColumnWithSamplingAndCustody(id, tracker)))
                  .thenAccept(
                      retrievedColumns -> {
                        if (retrievedColumns.size() == missingColumns.size()) {
                          LOG.debug(
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
                  .finishError(LOG);
            });

    return tracker.completionFuture();
  }

  private DataColumnSamplingTracker getOrCreateTracker(final UInt64 slot, final Bytes32 blockRoot) {
    return recentlySampledColumnsByRoot.computeIfAbsent(
        blockRoot,
        k -> DataColumnSamplingTracker.create(slot, blockRoot, custodyGroupCountManager));
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
              if (tracker.completionFuture().isDone()) {
                return true;
              }
              if (tracker.slot().isLessThan(firstNonFinalizedSlot)
                  || recentChainData.containsBlock(tracker.blockRoot())) {

                // make sure the future releases any pending waiters
                tracker
                    .completionFuture()
                    .completeExceptionally(new RuntimeException("DAS sampling expired"));
                return true;
              }

              return false;
            });
  }
}
