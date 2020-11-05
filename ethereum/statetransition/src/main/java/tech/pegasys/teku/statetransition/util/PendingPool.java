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

package tech.pegasys.teku.statetransition.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class PendingPool<T> implements SlotEventsChannel, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final Comparator<SlotAndRoot> SLOT_AND_ROOT_COMPARATOR =
      Comparator.comparing(SlotAndRoot::getSlot).thenComparing(SlotAndRoot::getRoot);
  private static final UInt64 DEFAULT_HISTORICAL_SLOT_TOLERANCE =
      UInt64.valueOf(Constants.SLOTS_PER_EPOCH * 10);
  private static final int DEFAULT_MAX_ITEMS = 5000;
  private static final UInt64 GENESIS_SLOT = UInt64.valueOf(Constants.GENESIS_SLOT);

  private final Subscribers<RequiredBlockRootSubscriber> requiredBlockRootSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlockRootDroppedSubscriber>
      requiredBlockRootDroppedSubscribers = Subscribers.create(true);

  private final Map<Bytes32, T> pendingItems = new HashMap<>();
  private final NavigableSet<SlotAndRoot> orderedPendingItems =
      new TreeSet<>(SLOT_AND_ROOT_COMPARATOR);
  private final Map<Bytes32, Set<Bytes32>> pendingItemsByRequiredBlockRoot = new HashMap<>();
  // Define the range of slots we care about
  private final UInt64 futureSlotTolerance;
  private final UInt64 historicalSlotTolerance;
  private final int maxItems;

  private final Function<T, Bytes32> hashTreeRootFunction;
  private final Function<T, Collection<Bytes32>> requiredBlockRootsFunction;
  private final Function<T, UInt64> targetSlotFunction;

  private volatile UInt64 currentSlot = UInt64.ZERO;
  private volatile UInt64 latestFinalizedSlot = UInt64.valueOf(Constants.GENESIS_SLOT);

  PendingPool(
      final UInt64 historicalSlotTolerance,
      final UInt64 futureSlotTolerance,
      final int maxItems,
      final Function<T, Bytes32> hashTreeRootFunction,
      final Function<T, Collection<Bytes32>> requiredBlockRootsFunction,
      final Function<T, UInt64> targetSlotFunction) {
    this.historicalSlotTolerance = historicalSlotTolerance;
    this.futureSlotTolerance = futureSlotTolerance;
    this.maxItems = maxItems;
    this.hashTreeRootFunction = hashTreeRootFunction;
    this.requiredBlockRootsFunction = requiredBlockRootsFunction;
    this.targetSlotFunction = targetSlotFunction;
  }

  public static PendingPool<SignedBeaconBlock> createForBlocks() {
    return createForBlocks(
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_ITEMS);
  }

  public static PendingPool<SignedBeaconBlock> createForBlocks(
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxItems) {
    return new PendingPool<>(
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems,
        block -> block.getMessage().hash_tree_root(),
        block -> Collections.singleton(block.getParentRoot()),
        SignedBeaconBlock::getSlot);
  }

  public static PendingPool<ValidateableAttestation> createForAttestations() {
    return new PendingPool<>(
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_ITEMS,
        ValidateableAttestation::hash_tree_root,
        ValidateableAttestation::getDependentBlockRoots,
        ValidateableAttestation::getEarliestSlotForForkChoiceProcessing);
  }

  public synchronized void add(T item) {
    if (shouldIgnoreItem(item)) {
      // Ignore items outside of the range we care about
      return;
    }

    // Make room for the new item
    while (pendingItems.size() > (maxItems - 1)) {
      final SlotAndRoot toRemove = orderedPendingItems.pollFirst();
      if (toRemove == null) {
        break;
      }
      remove(pendingItems.get(toRemove.getRoot()));
    }

    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    final Collection<Bytes32> requiredRoots = requiredBlockRootsFunction.apply(item);

    requiredRoots.forEach(
        requiredRoot ->
            // Index item by required roots
            pendingItemsByRequiredBlockRoot
                .computeIfAbsent(
                    requiredRoot,
                    (key) -> {
                      final Set<Bytes32> dependants = new HashSet<>();
                      requiredBlockRootSubscribers.forEach(
                          c -> c.onRequiredBlockRoot(requiredRoot));
                      return dependants;
                    })
                .add(itemRoot));

    // Index item by root
    if (pendingItems.putIfAbsent(itemRoot, item) == null) {
      LOG.trace(
          "Save unattached item at slot {} for future import: {}",
          targetSlotFunction.apply(item),
          item);
    }

    orderedPendingItems.add(toSlotAndRoot(item));
  }

  public synchronized void remove(T item) {
    final SlotAndRoot itemSlotAndRoot = toSlotAndRoot(item);
    orderedPendingItems.remove(itemSlotAndRoot);
    pendingItems.remove(itemSlotAndRoot.getRoot());

    final Collection<Bytes32> requiredRoots = requiredBlockRootsFunction.apply(item);
    requiredRoots.forEach(
        requiredRoot -> {
          Set<Bytes32> childSet = pendingItemsByRequiredBlockRoot.get(requiredRoot);
          if (childSet == null) {
            return;
          }
          childSet.remove(itemSlotAndRoot.getRoot());
          if (pendingItemsByRequiredBlockRoot.remove(requiredRoot, Collections.emptySet())) {
            requiredBlockRootDroppedSubscribers.forEach(
                s -> s.onRequiredBlockRootDropped(requiredRoot));
          }
        });
  }

  public synchronized int size() {
    return pendingItems.size();
  }

  public boolean contains(final T item) {
    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    return contains(itemRoot);
  }

  public synchronized boolean contains(final Bytes32 itemRoot) {
    return pendingItems.containsKey(itemRoot);
  }

  /**
   * Returns any items that are dependent on the given block root
   *
   * @param blockRoot The block root that some pending items may depend on.
   * @param includeIndirectDependents Whether to include items that depend indirectly on the given
   *     root. For example, if item B depends on item A which in turn depends on {@code blockRoot},
   *     both items A and B are returned if {@code includeIndirectDependents} is {@code true}. If
   *     {@code includeIndirectDependents} is {@code false}, only item A is returned.
   * @return The list of items which depend on the given block root.
   */
  public List<T> getItemsDependingOn(final Bytes32 blockRoot, boolean includeIndirectDependents) {
    if (includeIndirectDependents) {
      return getAllItemsDependingOn(blockRoot);
    } else {
      return getItemsDirectlyDependingOn(blockRoot);
    }
  }

  /**
   * Returns any items that are directly dependent on the given block root
   *
   * @param blockRoot The block root that some pending items may depend on
   * @return A list of items that depend on this block root.
   */
  private synchronized List<T> getItemsDirectlyDependingOn(final Bytes32 blockRoot) {
    final Set<Bytes32> dependentRoots = pendingItemsByRequiredBlockRoot.get(blockRoot);
    if (dependentRoots == null) {
      return Collections.emptyList();
    }

    return dependentRoots.stream()
        .map(pendingItems::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * Returns all items that directly or indirectly depend on the given block root. In other words,
   * if item B depends on item A which in turn depends on {@code blockRoot}, both items A and B are
   * returned.
   *
   * @param blockRoot The block root that some pending items may depend on.
   * @return A list of items that either directly or indirectly depend on the given block root.
   */
  private synchronized List<T> getAllItemsDependingOn(final Bytes32 blockRoot) {
    final Set<Bytes32> dependentRoots = new HashSet<>();

    Set<Bytes32> requiredRoots = Set.of(blockRoot);
    while (!requiredRoots.isEmpty()) {
      final Set<Bytes32> roots =
          requiredRoots.stream()
              .map(pendingItemsByRequiredBlockRoot::get)
              .filter(Objects::nonNull)
              .flatMap(Set::stream)
              .collect(Collectors.toSet());

      dependentRoots.addAll(roots);
      requiredRoots = roots;
    }

    return dependentRoots.stream()
        .map(pendingItems::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public long subscribeRequiredBlockRoot(final RequiredBlockRootSubscriber subscriber) {
    return requiredBlockRootSubscribers.subscribe(subscriber);
  }

  public boolean unsubscribeRequiredBlockRoot(final long subscriberId) {
    return requiredBlockRootSubscribers.unsubscribe(subscriberId);
  }

  public long subscribeRequiredBlockRootDropped(
      final RequiredBlockRootDroppedSubscriber subscriber) {
    return requiredBlockRootDroppedSubscribers.subscribe(subscriber);
  }

  public boolean unsubscribeRequiredBlockRootDropped(final long subscriberId) {
    return requiredBlockRootDroppedSubscribers.unsubscribe(subscriberId);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    currentSlot = slot;
    if (currentSlot.mod(historicalSlotTolerance).equals(UInt64.ZERO)) {
      // Purge old items
      prune();
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    this.latestFinalizedSlot = checkpoint.getEpochStartSlot();
  }

  @VisibleForTesting
  synchronized void prune() {
    final UInt64 slotLimit = latestFinalizedSlot.max(calculateItemAgeLimit());

    final List<T> toRemove = new ArrayList<>();
    for (SlotAndRoot slotAndRoot : orderedPendingItems) {
      if (slotAndRoot.getSlot().isGreaterThan(slotLimit)) {
        break;
      }
      toRemove.add(pendingItems.get(slotAndRoot.getRoot()));
    }

    toRemove.forEach(this::remove);
  }

  private boolean shouldIgnoreItem(final T item) {
    return isTooOld(item) || isFromFarFuture(item);
  }

  private boolean isTooOld(final T item) {
    return isFromAFinalizedSlot(item) || isOutsideOfHistoricalLimit(item);
  }

  private boolean isFromFarFuture(final T item) {
    final UInt64 slot = calculateFutureItemLimit();
    return targetSlotFunction.apply(item).isGreaterThan(slot);
  }

  private boolean isOutsideOfHistoricalLimit(final T item) {
    final UInt64 slot = calculateItemAgeLimit();
    return targetSlotFunction.apply(item).compareTo(slot) <= 0;
  }

  private boolean isFromAFinalizedSlot(final T item) {
    return targetSlotFunction.apply(item).compareTo(latestFinalizedSlot) <= 0;
  }

  private UInt64 calculateItemAgeLimit() {
    return currentSlot.compareTo(historicalSlotTolerance.plus(UInt64.ONE)) > 0
        ? currentSlot.minus(UInt64.ONE).minus(historicalSlotTolerance)
        : GENESIS_SLOT;
  }

  private UInt64 calculateFutureItemLimit() {
    return currentSlot.plus(futureSlotTolerance);
  }

  private SlotAndRoot toSlotAndRoot(final T item) {
    final UInt64 slot = targetSlotFunction.apply(item);
    final Bytes32 root = hashTreeRootFunction.apply(item);
    return new SlotAndRoot(slot, root);
  }

  public interface RequiredBlockRootSubscriber {
    void onRequiredBlockRoot(final Bytes32 blockRoot);
  }

  public interface RequiredBlockRootDroppedSubscriber {
    void onRequiredBlockRootDropped(final Bytes32 blockRoot);
  }

  private static class SlotAndRoot {
    private final UInt64 slot;
    private final Bytes32 root;

    private SlotAndRoot(final UInt64 slot, final Bytes32 root) {
      this.slot = slot;
      this.root = root;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public Bytes32 getRoot() {
      return root;
    }
  }
}
