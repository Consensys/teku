/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.sync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.storage.events.FinalizedCheckpointEvent;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;

class PendingPool<T> extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private static final UnsignedLong DEFAULT_FUTURE_SLOT_TOLERANCE = UnsignedLong.valueOf(2);
  private static final UnsignedLong DEFAULT_HISTORICAL_SLOT_TOLERANCE =
      UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH * 10);
  private static final UnsignedLong GENESIS_SLOT = UnsignedLong.valueOf(Constants.GENESIS_SLOT);

  private final EventBus eventBus;
  private final Map<Bytes32, T> pendingItems = new ConcurrentHashMap<>();
  private final Map<Bytes32, Set<Bytes32>> pendingItemsByDependentBlockRoot =
      new ConcurrentHashMap<>();
  // Define the range of slots we care about
  private final UnsignedLong futureSlotTolerance;
  private final UnsignedLong historicalSlotTolerance;

  private final Function<T, Bytes32> hashTreeRootFunction;
  private final Function<T, Collection<Bytes32>> dependentBlockHashFunction;
  private final Function<T, UnsignedLong> targetSlotFunction;

  private volatile UnsignedLong currentSlot = UnsignedLong.ZERO;
  private volatile UnsignedLong latestFinalizedSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);

  PendingPool(
      final EventBus eventBus,
      final UnsignedLong historicalSlotTolerance,
      final UnsignedLong futureSlotTolerance,
      final Function<T, Bytes32> hashTreeRootFunction,
      final Function<T, Collection<Bytes32>> dependentBlockHashFunction,
      final Function<T, UnsignedLong> targetSlotFunction) {
    this.eventBus = eventBus;
    this.historicalSlotTolerance = historicalSlotTolerance;
    this.futureSlotTolerance = futureSlotTolerance;
    this.hashTreeRootFunction = hashTreeRootFunction;
    this.dependentBlockHashFunction = dependentBlockHashFunction;
    this.targetSlotFunction = targetSlotFunction;
  }

  public static PendingPool<SignedBeaconBlock> createForBlocks(final EventBus eventBus) {
    return createForBlocks(
        eventBus, DEFAULT_HISTORICAL_SLOT_TOLERANCE, DEFAULT_FUTURE_SLOT_TOLERANCE);
  }

  static PendingPool<SignedBeaconBlock> createForBlocks(
      final EventBus eventBus,
      final UnsignedLong historicalBlockTolerance,
      final UnsignedLong futureBlockTolerance) {
    return new PendingPool<>(
        eventBus,
        historicalBlockTolerance,
        futureBlockTolerance,
        block -> block.getMessage().hash_tree_root(),
        block -> Collections.singleton(block.getParent_root()),
        SignedBeaconBlock::getSlot);
  }

  public static PendingPool<Attestation> createForAttestations(final EventBus eventBus) {
    return new PendingPool<>(
        eventBus,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        DEFAULT_FUTURE_SLOT_TOLERANCE,
        Attestation::hash_tree_root,
        attestation ->
            Set.of(
                attestation.getData().getTarget().getRoot(),
                attestation.getData().getBeacon_block_root()),
        attestation ->
            max(
                attestation.getData().getSlot().plus(UnsignedLong.ONE),
                attestation.getData().getTarget().getEpochSlot()));
  }

  private static UnsignedLong max(final UnsignedLong a, final UnsignedLong b) {
    return a.compareTo(b) > 0 ? a : b;
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventBus.register(this);
    return SafeFuture.completedFuture(null);
  }

  public void add(T item) {
    if (shouldIgnoreItem(item)) {
      // Ignore items outside of the range we care about
      return;
    }

    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    final Collection<Bytes32> dependentBlockRoots = dependentBlockHashFunction.apply(item);

    dependentBlockRoots.forEach(
        dependentBlockRoot ->
            // Index block by parent
            pendingItemsByDependentBlockRoot
                // Go ahead and add our root when the set is constructed to ensure we don't
                // accidentally
                // drop this set when we prune empty sets
                .computeIfAbsent(dependentBlockRoot, (key) -> createRootSet(itemRoot))
                .add(itemRoot));

    // Index item by root
    if (pendingItems.putIfAbsent(itemRoot, item) == null) {
      LOG.trace(
          "Save unattached item at slot {} for future import: {}",
          targetSlotFunction.apply(item),
          item);
    }
  }

  public void remove(T item) {
    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    pendingItems.remove(itemRoot);

    final Collection<Bytes32> dependentBlockRoots = dependentBlockHashFunction.apply(item);
    dependentBlockRoots.forEach(
        dependentBlockRoot -> {
          Set<Bytes32> childSet = pendingItemsByDependentBlockRoot.get(dependentBlockRoot);
          if (childSet == null) {
            return;
          }
          childSet.remove(itemRoot);
          pendingItemsByDependentBlockRoot.remove(dependentBlockRoot, Collections.emptySet());
        });
  }

  public int size() {
    return pendingItems.size();
  }

  public boolean contains(final T item) {
    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    return pendingItems.containsKey(itemRoot);
  }

  public List<T> childrenOf(final Bytes32 blockRoot) {
    final Set<Bytes32> childRoots = pendingItemsByDependentBlockRoot.get(blockRoot);
    if (childRoots == null) {
      return Collections.emptyList();
    }

    return childRoots.stream()
        .map(pendingItems::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Subscribe
  void onSlot(final SlotEvent slotEvent) {
    currentSlot = slotEvent.getSlot();
    if (currentSlot.mod(historicalSlotTolerance).equals(UnsignedLong.ZERO)) {
      // Purge old items
      prune();
    }
  }

  @Subscribe
  void onFinalizedCheckpoint(final FinalizedCheckpointEvent finalizedCheckpointEvent) {
    this.latestFinalizedSlot = finalizedCheckpointEvent.getFinalizedSlot();
  }

  @VisibleForTesting
  void prune() {
    pruneItems(this::isTooOld);
  }

  private boolean shouldIgnoreItem(final T item) {
    return isTooOld(item) || isFromFarFuture(item);
  }

  private boolean isTooOld(final T item) {
    return isFromAFinalizedSlot(item) || isOutsideOfHistoricalLimit(item);
  }

  private boolean isFromFarFuture(final T item) {
    final UnsignedLong slot = calculateFutureItemLimit();
    return targetSlotFunction.apply(item).compareTo(slot) > 0;
  }

  private boolean isOutsideOfHistoricalLimit(final T item) {
    final UnsignedLong slot = calculateItemAgeLimit();
    return targetSlotFunction.apply(item).compareTo(slot) <= 0;
  }

  private boolean isFromAFinalizedSlot(final T item) {
    return targetSlotFunction.apply(item).compareTo(latestFinalizedSlot) <= 0;
  }

  private UnsignedLong calculateItemAgeLimit() {
    final UnsignedLong ageLimit =
        currentSlot.minus(UnsignedLong.ONE).minus(historicalSlotTolerance);
    if (ageLimit.compareTo(currentSlot) > 0) {
      // If subtraction caused overflow, return genesis slot
      return GENESIS_SLOT;
    }
    return ageLimit;
  }

  private UnsignedLong calculateFutureItemLimit() {
    return currentSlot.plus(futureSlotTolerance);
  }

  private void pruneItems(final Predicate<T> shouldRemove) {
    pendingItems.values().stream().filter(shouldRemove).forEach(this::remove);
  }

  private Set<Bytes32> createRootSet(final Bytes32 initialValue) {
    final Set<Bytes32> rootSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    rootSet.add(initialValue);
    return rootSet;
  }

  @Override
  protected SafeFuture<?> doStop() {
    eventBus.unregister(this);
    return SafeFuture.completedFuture(null);
  }
}
