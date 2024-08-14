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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigEip7594;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7594.BeaconBlockBodyEip7594;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DasSamplerImpl
    implements DataAvailabilitySampler, FinalizedCheckpointChannel, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final DataColumnSidecarRetriever retriever;
  private final int maxPendingColumnRequests;
  private final int minPendingColumnRequests;

  private final Map<ColumnSlotAndIdentifier, PendingRequest> pendingRequests = new HashMap<>();
  private boolean started = false;
  private final AtomicLong syncedColumnCount = new AtomicLong();
  private static final int MAX_SCAN_SLOTS = 200;

  private final Spec spec;
  private final DataColumnSidecarDB db;
  private final RecentChainData recentChainData;
  private final int myDataColumnSampleCount;

  private final UInt64 eip7594StartEpoch;
  private final Random rnd;
  private final TreeMap<SlotAndBlockRoot, List<UInt64>> assignedSampleColumns = new TreeMap<>();
  private final TreeMap<SlotAndBlockRoot, Set<DataColumnIdentifier>> collectedSamples =
      new TreeMap<>();
  private final TreeMap<SlotAndBlockRoot, SafeFuture<Void>> sampleTasks = new TreeMap<>();

  private UInt64 currentSlot = null;

  public DasSamplerImpl(
      final Spec spec,
      final DataColumnSidecarDB db,
      final RecentChainData recentChainData,
      final int myDataColumnSampleCount,
      DataColumnSidecarRetriever retriever,
      int maxPendingColumnRequests,
      int minPendingColumnRequests) {
    checkNotNull(spec);
    checkNotNull(db);

    this.spec = spec;
    this.db = db;
    this.recentChainData = recentChainData;
    this.myDataColumnSampleCount = myDataColumnSampleCount;
    this.eip7594StartEpoch = spec.getForkSchedule().getFork(SpecMilestone.EIP7594).getEpoch();
    this.rnd = new Random();

    this.retriever = retriever;
    this.maxPendingColumnRequests = maxPendingColumnRequests;
    this.minPendingColumnRequests = minPendingColumnRequests;
  }

  public DasSamplerImpl(
      final Spec spec,
      final DataColumnSidecarDB db,
      final RecentChainData recentChainData,
      final int myDataColumnSampleCount,
      DataColumnSidecarRetriever retriever) {
    this(spec, db, recentChainData, myDataColumnSampleCount, retriever, 10 * 1024, 2 * 1024);
  }

  private synchronized void onRequestComplete(DataColumnSidecar response) {
    onNewValidatedDataColumnSidecar(response);
    fillUpIfNeeded();
  }

  private boolean wasCancelledImplicitly(Throwable exception) {
    return exception instanceof CancellationException
        || (exception instanceof CompletionException
            && exception.getCause() instanceof CancellationException);
  }

  private synchronized void onRequestException(PendingRequest request, Throwable exception) {
    if (wasCancelledImplicitly(exception)) {
      // request was cancelled explicitly here
    } else {
      LOG.warn("[nyota] Unexpected exception for request " + request, exception);
    }
  }

  @Override
  public SafeFuture<Void> checkDataAvailability(
      UInt64 slot, Bytes32 blockRoot, Bytes32 parentRoot) {
    return addSlotTask(slot, blockRoot, parentRoot).thenPeek(__ -> fillUpIfNeeded());
  }

  private void fillUpIfNeeded() {
    if (started && pendingRequests.size() <= minPendingColumnRequests) {
      fillUp();
    }
  }

  private synchronized void fillUp() {
    int newRequestCount = maxPendingColumnRequests - pendingRequests.size();
    final Set<ColumnSlotAndIdentifier> missingColumnsToRequest =
        retrieveMissingColumns().stream()
            .filter(columnSlotId -> !pendingRequests.containsKey(columnSlotId))
            .limit(newRequestCount)
            .collect(Collectors.toSet());

    // TODO cancel those which are not missing anymore for whatever reason

    for (ColumnSlotAndIdentifier missingColumn : missingColumnsToRequest) {
      addPendingRequest(missingColumn);
    }

    {
      Set<UInt64> missingSlots =
          missingColumnsToRequest.stream()
              .map(ColumnSlotAndIdentifier::slot)
              .collect(Collectors.toSet());
      LOG.info(
          "[nyota] DataSamplerSync.fillUp: synced={} pending={}, missingColumns={}({})",
          syncedColumnCount,
          pendingRequests.size(),
          missingColumnsToRequest.size(),
          missingSlots);
    }
  }

  private synchronized void addPendingRequest(final ColumnSlotAndIdentifier missingColumn) {
    if (pendingRequests.containsKey(missingColumn)) {
      return;
    }
    final SafeFuture<DataColumnSidecar> promise = retriever.retrieve(missingColumn);
    final PendingRequest request = new PendingRequest(missingColumn, promise);
    pendingRequests.put(missingColumn, request);
    promise.finish(this::onRequestComplete, err -> onRequestException(request, err));
  }

  @Override
  public void onSlot(UInt64 slot) {
    this.currentSlot = slot;
  }

  private UInt64 getEarliestSampleSlot(UInt64 currentSlot) {
    UInt64 epoch = getEarliestSamplesEpoch(spec.computeEpochAtSlot(currentSlot));
    return spec.computeStartSlotAtEpoch(epoch);
  }

  private UInt64 getEarliestSamplesEpoch(UInt64 currentEpoch) {
    int custodyPeriod =
        spec.getSpecConfig(currentEpoch)
            .toVersionEip7594()
            .orElseThrow()
            .getMinEpochsForDataColumnSidecarsRequests();
    return currentEpoch.minusMinZero(custodyPeriod).max(eip7594StartEpoch);
  }

  public void onNewValidatedDataColumnSidecar(DataColumnSidecar dataColumnSidecar) {
    getFirstIncompleteSlot()
        .thenAccept(
            firstIncompleteSlot ->
                onNewValidatedDataColumnSidecar(dataColumnSidecar, firstIncompleteSlot))
        .ifExceptionGetsHereRaiseABug();
  }

  private synchronized void onNewValidatedDataColumnSidecar(
      final DataColumnSidecar dataColumnSidecar, final UInt64 firstIncompleteSlot) {
    if (dataColumnSidecar.getSlot().isGreaterThanOrEqualTo(firstIncompleteSlot)) {
      final Set<DataColumnIdentifier> newIdentifiers =
          onMaybeNewValidatedIdentifier(
              dataColumnSidecar.getSlot(),
              DataColumnIdentifier.createFromSidecar(dataColumnSidecar));
      newIdentifiers.forEach(
          newIdentifier -> {
            final Optional<PendingRequest> maybeRequest =
                Optional.ofNullable(
                    pendingRequests.remove(
                        new ColumnSlotAndIdentifier(dataColumnSidecar.getSlot(), newIdentifier)));
            maybeRequest.ifPresent(
                pendingRequest -> {
                  if (!pendingRequest.columnPromise().isDone()) {
                    pendingRequest.columnPromise().cancel(true);
                  }
                  syncedColumnCount.incrementAndGet();
                });
          });

      // IF we've collected 50%, everything from this slot is available
      final Set<DataColumnIdentifier> thisSlotDataColumnIdentifiers =
          collectedSamples.get(dataColumnSidecar.getSlotAndBlockRoot());
      final int columnsCount =
          SpecConfigEip7594.required(spec.atSlot(dataColumnSidecar.getSlot()).getConfig())
              .getNumberOfColumns();
      if (thisSlotDataColumnIdentifiers.size() * 2 >= columnsCount
          && thisSlotDataColumnIdentifiers.size() != columnsCount) {
        IntStream.range(0, columnsCount)
            .mapToObj(
                index ->
                    new DataColumnIdentifier(
                        dataColumnSidecar.getBlockRoot(), UInt64.valueOf(index)))
            .forEach(
                identifier -> {
                  final boolean wasAdded = thisSlotDataColumnIdentifiers.add(identifier);
                  if (wasAdded) {
                    newIdentifiers.add(identifier);
                  }
                });
      }

      // Check if the slot is completed
      if (areSlotAssignedColumnsCompleted(dataColumnSidecar.getSlotAndBlockRoot())
          // Were it just completed, on this DataColumnSidecar?
          && assignedSampleColumns.get(dataColumnSidecar.getSlotAndBlockRoot()).stream()
              .map(
                  assignedIndex ->
                      newIdentifiers.contains(
                          new DataColumnIdentifier(
                              dataColumnSidecar.getBlockRoot(), assignedIndex)))
              .filter(couldBeTrue -> couldBeTrue)
              .findFirst()
              .orElse(false)) {
        final Optional<SafeFuture<Void>> voidSafeFuture =
            Optional.ofNullable(sampleTasks.get(dataColumnSidecar.getSlotAndBlockRoot()));
        voidSafeFuture.ifPresent(future -> future.complete(null));
        LOG.info("Slot {} sampling is completed successfully", dataColumnSidecar.getSlot());
      }
    }
  }

  private Set<DataColumnIdentifier> onMaybeNewValidatedIdentifier(
      final UInt64 slot, final DataColumnIdentifier dataColumnIdentifier) {
    final Set<DataColumnIdentifier> newIdentifiers = new HashSet<>();
    collectedSamples.compute(
        new SlotAndBlockRoot(slot, dataColumnIdentifier.getBlockRoot()),
        (__, dataColumnIdentifiers) -> {
          if (dataColumnIdentifiers != null) {
            final boolean wasAdded = dataColumnIdentifiers.add(dataColumnIdentifier);
            if (wasAdded) {
              newIdentifiers.add(dataColumnIdentifier);
            }
            return dataColumnIdentifiers;
          } else {
            newIdentifiers.add(dataColumnIdentifier);
            Set<DataColumnIdentifier> collectedIdentifiers = new HashSet<>();
            collectedIdentifiers.add(dataColumnIdentifier);
            return collectedIdentifiers;
          }
        });

    return newIdentifiers;
  }

  private boolean areSlotAssignedColumnsCompleted(final SlotAndBlockRoot slotAndBlockRoot) {
    return assignedSampleColumns.containsKey(slotAndBlockRoot)
        // All assigned are collected
        && assignedSampleColumns.get(slotAndBlockRoot).stream()
            .map(
                assignedIndex ->
                    collectedSamples.get(slotAndBlockRoot).stream()
                        .map(DataColumnIdentifier::getIndex)
                        .filter(collectedIndex -> collectedIndex.equals(assignedIndex))
                        .map(__ -> true)
                        .findFirst()
                        .orElse(false))
            .filter(columnResult -> columnResult.equals(false))
            .findFirst()
            .orElse(true);
  }

  private SafeFuture<Void> assignSampleColumns(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    SafeFuture<Void> slotSampled = SafeFuture.COMPLETE;
    if (!started) {
      started = true;
      slotSampled =
          slotSampled
              .thenCompose(__ -> getFirstIncompleteSlot())
              .thenCompose(
                  firstSlot ->
                      SafeFuture.allOf(
                          getAncestorsInclusive(firstSlot, parentRoot)
                              .map(
                                  slotAndBlockRoot ->
                                      scheduleSlotTaskIfNonZeroCommitments(
                                          slotAndBlockRoot.getSlot(),
                                          slotAndBlockRoot.getBlockRoot()))));
    } else {
      final Optional<SlotAndBlockRoot> maybeFirstAssigned =
          assignedSampleColumns.navigableKeySet().stream().findFirst();
      if (maybeFirstAssigned.isPresent()) {
        slotSampled =
            slotSampled.thenCompose(
                __ ->
                    SafeFuture.allOf(
                        getAncestorsInclusive(maybeFirstAssigned.get().getSlot(), parentRoot)
                            .map(
                                slotAndBlockRoot ->
                                    sampleTasks.getOrDefault(
                                        slotAndBlockRoot, SafeFuture.COMPLETE))));
      }
    }

    final SafeFuture<Void> thisSlotTask = scheduleSlotTask(slot, blockRoot);
    return slotSampled.thenCompose(__ -> thisSlotTask);
  }

  private Stream<SlotAndBlockRoot> getAncestorsInclusive(
      final UInt64 fromSlot, final Bytes32 toSlotBlockRoot) {
    final NavigableMap<UInt64, Bytes32> ancestors =
        recentChainData.getAncestorsOnFork(fromSlot.minusMinZero(1), toSlotBlockRoot);
    return ancestors.navigableKeySet().stream()
        .map(aSlot -> new SlotAndBlockRoot(aSlot, ancestors.get(aSlot)));
  }

  private synchronized SafeFuture<Void> scheduleSlotTaskIfNonZeroCommitments(
      final UInt64 slot, final Bytes32 blockRoot) {
    return recentChainData
        .retrieveBlockByRoot(blockRoot)
        .thenCompose(
            maybeBlock -> {
              if (!BeaconBlockBodyEip7594.required(maybeBlock.orElseThrow().getBody())
                  .getBlobKzgCommitments()
                  .isEmpty()) {
                return scheduleSlotTask(slot, blockRoot);
              } else {
                return SafeFuture.COMPLETE;
              }
            });
  }

  private synchronized SafeFuture<Void> scheduleSlotTask(
      final UInt64 slot, final Bytes32 blockRoot) {
    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(slot, blockRoot);
    final boolean alreadyAssigned = assignedSampleColumns.containsKey(slotAndBlockRoot);
    final List<UInt64> assignedSampleColumns =
        this.assignedSampleColumns.computeIfAbsent(slotAndBlockRoot, this::computeSampleColumns);
    if (!alreadyAssigned) {
      LOG.info("Slot {}, root {} assigned columns: {}", slot, blockRoot, assignedSampleColumns);
    }

    assignedSampleColumns.forEach(
        column ->
            db.getSidecar(new DataColumnIdentifier(blockRoot, column))
                .thenPeek(
                    maybeSidecar -> maybeSidecar.ifPresent(this::onNewValidatedDataColumnSidecar))
                .ifExceptionGetsHereRaiseABug());

    return sampleTasks.computeIfAbsent(
        new SlotAndBlockRoot(slot, blockRoot), __ -> new SafeFuture<>());
  }

  private List<UInt64> computeSampleColumns(final SlotAndBlockRoot slotAndBlockRoot) {
    final int columnsCount =
        SpecConfigEip7594.required(spec.atSlot(slotAndBlockRoot.getSlot()).getConfig())
            .getNumberOfColumns();
    final List<UInt64> assignedSamples = new ArrayList<>();
    while (assignedSamples.size() < myDataColumnSampleCount) {
      final UInt64 candidate = UInt64.valueOf(rnd.nextInt(columnsCount));
      if (assignedSamples.contains(candidate)) {
        continue;
      }
      assignedSamples.add(candidate);
    }

    return assignedSamples;
  }

  @Override
  public void onNewFinalizedCheckpoint(Checkpoint checkpoint, boolean fromOptimisticBlock) {
    advanceFirstIncompleteSlot(checkpoint.getEpoch());
  }

  private void advanceFirstIncompleteSlot(UInt64 finalizedEpoch) {
    final UInt64 firstNonFinalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch.increment());

    final List<SlotColumnsTask> slotTasks =
        retrievePotentiallyIncompleteSlotSamples(firstNonFinalizedSlot, MAX_SCAN_SLOTS);
    slotTasks.stream()
        .filter(SlotColumnsTask::isIncomplete)
        .findFirst()
        .ifPresentOrElse(
            slotColumnsTask ->
                db.setFirstSamplerIncompleteSlot(slotColumnsTask.slot())
                    .thenPeek(__ -> prune(slotColumnsTask.slot()))
                    .ifExceptionGetsHereRaiseABug(),
            () -> {
              if (slotTasks.isEmpty()) {
                return;
              }
              db.setFirstSamplerIncompleteSlot(slotTasks.getLast().slot())
                  .thenPeek(__ -> prune(slotTasks.getLast().slot()))
                  .ifExceptionGetsHereRaiseABug();
            });
  }

  private synchronized void prune(final UInt64 slotExclusive) {
    LOG.info(
        "Pruning till slot {}, collectedSamples: {}, assignedSamples: {}",
        slotExclusive,
        collectedSamples
            .subMap(
                SlotAndBlockRoot.createLow(UInt64.ZERO), SlotAndBlockRoot.createLow(slotExclusive))
            .keySet()
            .stream()
            .sorted()
            .toList(),
        assignedSampleColumns
            .subMap(
                SlotAndBlockRoot.createLow(UInt64.ZERO), SlotAndBlockRoot.createLow(slotExclusive))
            .keySet()
            .stream()
            .sorted()
            .toList());
    final Set<SlotAndBlockRoot> collectedSampleSlotsToPrune =
        collectedSamples
            .subMap(
                SlotAndBlockRoot.createLow(UInt64.ZERO), SlotAndBlockRoot.createLow(slotExclusive))
            .keySet();
    collectedSampleSlotsToPrune.forEach(collectedSamples::remove);
    final Set<SlotAndBlockRoot> assignedSamplesSlotsToPrune =
        assignedSampleColumns
            .subMap(
                SlotAndBlockRoot.createLow(UInt64.ZERO), SlotAndBlockRoot.createLow(slotExclusive))
            .keySet();
    assignedSamplesSlotsToPrune.forEach(
        slotAndBlockRoot -> {
          assignedSampleColumns.remove(slotAndBlockRoot);
          SafeFuture<Void> future = sampleTasks.remove(slotAndBlockRoot);
          if (future != null) {
            future.cancel(true);
          }
        });
  }

  private synchronized List<SlotColumnsTask> retrievePotentiallyIncompleteSlotSamples(
      final UInt64 toSlotIncluded, final int limit) {
    if (assignedSampleColumns.isEmpty()) {
      return Collections.emptyList();
    }
    SlotAndBlockRoot from = assignedSampleColumns.navigableKeySet().first();
    final UInt64 toSlot;
    if (from.getSlot().plus(limit).isLessThan(toSlotIncluded)) {
      toSlot = from.getSlot().plus(limit);
    } else {
      toSlot = toSlotIncluded;
    }
    SlotAndBlockRoot to = SlotAndBlockRoot.createHigh(toSlot);
    return assignedSampleColumns.navigableKeySet().headSet(to).stream()
        .map(
            slotAndBlockRoot ->
                new SlotColumnsTask(
                    slotAndBlockRoot.getSlot(),
                    slotAndBlockRoot.getBlockRoot(),
                    assignedSampleColumns.get(slotAndBlockRoot),
                    collectedSamples.getOrDefault(slotAndBlockRoot, Collections.emptySet())))
        .toList();
  }

  private SafeFuture<Void> addSlotTask(
      final UInt64 slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    return assignSampleColumns(slot, blockRoot, parentRoot);
  }

  private SafeFuture<UInt64> getFirstIncompleteSlot() {
    return db.getFirstSamplerIncompleteSlot()
        .thenApply(maybeSlot -> maybeSlot.orElseGet(() -> getEarliestSampleSlot(currentSlot)));
  }

  private List<ColumnSlotAndIdentifier> retrieveMissingColumns() {
    // waiting a column for [gossipWaitSlots] to be delivered by gossip
    // and not considering it missing yet
    return retrievePotentiallyIncompleteSlotSamples(currentSlot, MAX_SCAN_SLOTS).stream()
        .flatMap(
            slotTask ->
                slotTask.getIncompleteColumns().stream()
                    .map(colId -> new ColumnSlotAndIdentifier(slotTask.slot(), colId)))
        .toList();
  }

  private record PendingRequest(
      ColumnSlotAndIdentifier columnId, SafeFuture<DataColumnSidecar> columnPromise) {}

  private record SlotColumnsTask(
      UInt64 slot,
      Bytes32 blockRoot,
      Collection<UInt64> requiredColumnIndices,
      Collection<DataColumnIdentifier> collectedColumnIndices) {
    public Collection<DataColumnIdentifier> getIncompleteColumns() {
      final Set<UInt64> collectedIndices =
          collectedColumnIndices.stream()
              .filter(identifier -> identifier.getBlockRoot().equals(blockRoot))
              .map(DataColumnIdentifier::getIndex)
              .collect(Collectors.toSet());
      return requiredColumnIndices.stream()
          .filter(requiredColIdx -> !collectedIndices.contains(requiredColIdx))
          .map(missedColIdx -> new DataColumnIdentifier(blockRoot, missedColIdx))
          .toList();
    }

    @SuppressWarnings("UnusedMethod")
    public boolean isComplete() {
      return !isIncomplete();
    }

    public boolean isIncomplete() {
      return !getIncompleteColumns().isEmpty();
    }
  }
}
