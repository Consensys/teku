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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.config.Constants;

class PendingBlocks extends Service {
  private static final UnsignedLong DEFAULT_FUTURE_BLOCK_TOLERANCE = UnsignedLong.valueOf(2);
  private static final UnsignedLong DEFAULT_HISTORICAL_BLOCK_TOLERANCE =
      UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH * 10);
  private static final UnsignedLong GENESIS_SLOT = UnsignedLong.valueOf(Constants.GENESIS_SLOT);

  private final EventBus eventBus;
  private final Map<Bytes32, BeaconBlock> pendingBlocks = new ConcurrentHashMap<>();
  private final Map<Bytes32, Set<Bytes32>> pendingBlocksByParentRoot = new ConcurrentHashMap<>();
  // Define the range of slots we care about
  private final UnsignedLong futureBlockTolerance;
  private final UnsignedLong historicalBlockTolerance;

  private volatile UnsignedLong currentSlot = UnsignedLong.ZERO;

  PendingBlocks(
      final EventBus eventBus,
      final UnsignedLong historicalBlockTolerance,
      final UnsignedLong futureBlockTolerance) {
    this.eventBus = eventBus;
    this.historicalBlockTolerance = historicalBlockTolerance;
    this.futureBlockTolerance = futureBlockTolerance;
  }

  public static PendingBlocks create(final EventBus eventBus) {
    return new PendingBlocks(
        eventBus, DEFAULT_HISTORICAL_BLOCK_TOLERANCE, DEFAULT_FUTURE_BLOCK_TOLERANCE);
  }

  @Override
  protected CompletableFuture<?> doStart() {
    eventBus.register(this);
    return CompletableFuture.completedFuture(null);
  }

  public void add(BeaconBlock beaconBlock) {
    if (shouldIgnoreBlock(beaconBlock)) {
      // Ignore blocks outside of the range we care about
      return;
    }

    final Bytes32 blockRoot = beaconBlock.signing_root("signature");
    final Bytes32 parentRoot = beaconBlock.getParent_root();

    // Index block by parent
    pendingBlocksByParentRoot
        .computeIfAbsent(parentRoot, (key) -> createRootSet(blockRoot))
        .add(blockRoot);

    // Index block by root
    pendingBlocks.putIfAbsent(blockRoot, beaconBlock);
  }

  public void remove(BeaconBlock beaconBlock) {
    final Bytes32 blockRoot = beaconBlock.signing_root("signature");
    pendingBlocks.remove(blockRoot);

    final Bytes32 parentRoot = beaconBlock.getParent_root();
    Set<Bytes32> childSet = pendingBlocksByParentRoot.get(parentRoot);
    if (childSet == null) {
      return;
    }
    childSet.remove(blockRoot);
    pendingBlocksByParentRoot.remove(parentRoot, Collections.emptySet());
  }

  public int size() {
    return pendingBlocks.size();
  }

  public boolean contains(final BeaconBlock block) {
    final Bytes32 blockRoot = block.signing_root("signature");
    return pendingBlocks.containsKey(blockRoot);
  }

  public List<BeaconBlock> childrenOf(final Bytes32 parentRoot) {
    final Set<Bytes32> childHashes = pendingBlocksByParentRoot.get(parentRoot);
    if (childHashes == null) {
      return Collections.emptyList();
    }

    return childHashes.stream()
        .map(pendingBlocks::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Subscribe
  void onSlot(final SlotEvent slotEvent) {
    currentSlot = slotEvent.getSlot();
    if (currentSlot.mod(historicalBlockTolerance).equals(UnsignedLong.ZERO)) {
      // Purge old blocks
      pruneBlocks(isTooOld());
    }
  }

  private boolean shouldIgnoreBlock(final BeaconBlock block) {
    return isTooOld().test(block) || isFromFuture().test(block);
  }

  private Predicate<BeaconBlock> isTooOld() {
    final UnsignedLong slot = calculateBlockAgeLimit();
    return (block) -> block.getSlot().compareTo(slot) <= 0;
  }

  private Predicate<BeaconBlock> isFromFuture() {
    final UnsignedLong slot = (calculateFutureBlockLimit());
    return (block) -> block.getSlot().compareTo(slot) > 0;
  }

  private UnsignedLong calculateBlockAgeLimit() {
    final UnsignedLong ageLimit =
        currentSlot.minus(UnsignedLong.ONE).minus(historicalBlockTolerance);
    if (ageLimit.compareTo(currentSlot) > 0) {
      // If subtraction caused overflow, return genesis slot
      return GENESIS_SLOT;
    }
    return ageLimit;
  }

  private UnsignedLong calculateFutureBlockLimit() {
    return currentSlot.plus(futureBlockTolerance);
  }

  private void pruneBlocks(final Predicate<BeaconBlock> shouldRemove) {
    pendingBlocks.values().stream().filter(shouldRemove).forEach(this::remove);
  }

  private Set<Bytes32> createRootSet(final Bytes32 initialValue) {
    final Set<Bytes32> rootSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    rootSet.add(initialValue);
    return rootSet;
  }

  @Override
  protected CompletableFuture<?> doStop() {
    eventBus.unregister(this);
    return CompletableFuture.completedFuture(null);
  }
}
