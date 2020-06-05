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
import com.google.common.primitives.UnsignedLong;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.events.Subscribers;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class PendingPool<T> implements SlotEventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private static final UnsignedLong DEFAULT_FUTURE_SLOT_TOLERANCE = UnsignedLong.valueOf(2);
  private static final UnsignedLong DEFAULT_HISTORICAL_SLOT_TOLERANCE =
      UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH * 10);
  private static final UnsignedLong GENESIS_SLOT = UnsignedLong.valueOf(Constants.GENESIS_SLOT);

  private final Subscribers<RequiredBlockRootSubscriber> requiredBlockRootSubscribers =
      Subscribers.create(true);
  private final Subscribers<RequiredBlockRootDroppedSubscriber>
      requiredBlockRootDroppedSubscribers = Subscribers.create(true);

  private final Map<Bytes32, T> pendingItems = new ConcurrentHashMap<>();
  private final Map<Bytes32, Set<Bytes32>> pendingItemsByRequiredBlockRoot =
      new ConcurrentHashMap<>();
  // Define the range of slots we care about
  private final UnsignedLong futureSlotTolerance;
  private final UnsignedLong historicalSlotTolerance;

  private final Function<T, Bytes32> hashTreeRootFunction;
  private final Function<T, Collection<Bytes32>> requiredBlockRootsFunction;
  private final Function<T, UnsignedLong> targetSlotFunction;

  private volatile UnsignedLong currentSlot = UnsignedLong.ZERO;
  private volatile UnsignedLong latestFinalizedSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);

  PendingPool(
      final UnsignedLong historicalSlotTolerance,
      final UnsignedLong futureSlotTolerance,
      final Function<T, Bytes32> hashTreeRootFunction,
      final Function<T, Collection<Bytes32>> requiredBlockRootsFunction,
      final Function<T, UnsignedLong> targetSlotFunction) {
    this.historicalSlotTolerance = historicalSlotTolerance;
    this.futureSlotTolerance = futureSlotTolerance;
    this.hashTreeRootFunction = hashTreeRootFunction;
    this.requiredBlockRootsFunction = requiredBlockRootsFunction;
    this.targetSlotFunction = targetSlotFunction;
  }

  public static PendingPool<SignedBeaconBlock> createForBlocks() {
    return createForBlocks(DEFAULT_HISTORICAL_SLOT_TOLERANCE, DEFAULT_FUTURE_SLOT_TOLERANCE);
  }

  public static PendingPool<SignedBeaconBlock> createForBlocks(
      final UnsignedLong historicalBlockTolerance, final UnsignedLong futureBlockTolerance) {
    return new PendingPool<>(
        historicalBlockTolerance,
        futureBlockTolerance,
        block -> block.getMessage().hash_tree_root(),
        block -> Collections.singleton(block.getParent_root()),
        SignedBeaconBlock::getSlot);
  }

  public static PendingPool<ValidateableAttestation> createForAttestations() {
    return new PendingPool<>(
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        DEFAULT_FUTURE_SLOT_TOLERANCE,
        ValidateableAttestation::hash_tree_root,
        ValidateableAttestation::getDependentBlockRoots,
        ValidateableAttestation::getEarliestSlotForForkChoiceProcessing);
  }

  public void add(T item) {
    if (shouldIgnoreItem(item)) {
      // Ignore items outside of the range we care about
      return;
    }

    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    final Collection<Bytes32> requiredRoots = requiredBlockRootsFunction.apply(item);

    requiredRoots.forEach(
        requiredRoot ->
            // Index item by required roots
            pendingItemsByRequiredBlockRoot
                // Go ahead and add our root when the set is constructed to ensure we don't
                // accidentally
                // drop this set when we prune empty sets
                .computeIfAbsent(
                    requiredRoot,
                    (key) -> {
                      final Set<Bytes32> dependants = createRootSet(itemRoot);
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
  }

  public void remove(T item) {
    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    pendingItems.remove(itemRoot);

    final Collection<Bytes32> requiredRoots = requiredBlockRootsFunction.apply(item);
    requiredRoots.forEach(
        requiredRoot -> {
          Set<Bytes32> childSet = pendingItemsByRequiredBlockRoot.get(requiredRoot);
          if (childSet == null) {
            return;
          }
          childSet.remove(itemRoot);
          if (pendingItemsByRequiredBlockRoot.remove(requiredRoot, Collections.emptySet())) {
            requiredBlockRootDroppedSubscribers.forEach(
                s -> s.onRequiredBlockRootDropped(requiredRoot));
          }
        });
  }

  public int size() {
    return pendingItems.size();
  }

  public boolean contains(final T item) {
    final Bytes32 itemRoot = hashTreeRootFunction.apply(item);
    return contains(itemRoot);
  }

  public boolean contains(final Bytes32 itemRoot) {
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
  private List<T> getItemsDirectlyDependingOn(final Bytes32 blockRoot) {
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
  private List<T> getAllItemsDependingOn(final Bytes32 blockRoot) {
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
  public void onSlot(final UnsignedLong slot) {
    currentSlot = slot;
    if (currentSlot.mod(historicalSlotTolerance).equals(UnsignedLong.ZERO)) {
      // Purge old items
      prune();
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    this.latestFinalizedSlot = checkpoint.getEpochStartSlot();
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

  public interface RequiredBlockRootSubscriber {
    void onRequiredBlockRoot(final Bytes32 blockRoot);
  }

  public interface RequiredBlockRootDroppedSubscriber {
    void onRequiredBlockRootDropped(final Bytes32 blockRoot);
  }
}
