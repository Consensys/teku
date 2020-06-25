/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.state;

import static com.google.common.primitives.UnsignedLong.ONE;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;
import tech.pegasys.teku.core.StreamingStateRegenerator;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.server.Database;

public class FinalizedStateCache {
  /**
   * Note this is a best effort basis to track what states are cached. Slots are added here slightly
   * before the stateCache is actually updated and removed slightly after they are evicted from the
   * cache.
   */
  private final NavigableSet<UnsignedLong> availableSlots = new ConcurrentSkipListSet<>();

  private final LoadingCache<UnsignedLong, BeaconState> stateCache;
  private final Database database;

  public FinalizedStateCache(
      final Database database, final int maximumCacheSize, final boolean useSoftReferences) {
    this.database = database;
    final CacheBuilder<UnsignedLong, BeaconState> cacheBuilder =
        CacheBuilder.newBuilder()
            .maximumSize(maximumCacheSize)
            .removalListener(this::onRemovedFromCache);
    if (useSoftReferences) {
      cacheBuilder.softValues();
    }
    this.stateCache = cacheBuilder.build(new StateCacheLoader());
  }

  private void onRemovedFromCache(
      final RemovalNotification<UnsignedLong, BeaconState> removalNotification) {
    if (removalNotification.getCause() != RemovalCause.REPLACED) {
      availableSlots.remove(removalNotification.getKey());
    }
  }

  public Optional<BeaconState> getFinalizedState(final UnsignedLong slot) {
    try {
      return Optional.of(stateCache.getUnchecked(slot));
    } catch (final UncheckedExecutionException e) {
      if (Throwables.getRootCause(e) instanceof StateUnavailableException) {
        return Optional.empty();
      }
      throw new RuntimeException("Error while regenerating state", e);
    }
  }

  private Optional<BeaconState> getLatestStateFromCache(final UnsignedLong slot) {
    return Optional.ofNullable(availableSlots.floor(slot)).map(stateCache::getIfPresent);
  }

  private class StateCacheLoader extends CacheLoader<UnsignedLong, BeaconState> {

    @Override
    public BeaconState load(final UnsignedLong key) {
      return regenerateState(key).orElseThrow(StateUnavailableException::new);
    }

    private Optional<BeaconState> regenerateState(final UnsignedLong slot) {
      return database
          .getLatestAvailableFinalizedState(slot)
          .map(state -> regenerateState(slot, state));
    }

    private BeaconState regenerateState(final UnsignedLong slot, final BeaconState stateFromDisk) {
      final Optional<BeaconState> latestStateFromCache = getLatestStateFromCache(slot);
      final BeaconState preState =
          latestStateFromCache
              .filter(
                  stateFromCache ->
                      stateFromCache.getSlot().compareTo(stateFromDisk.getSlot()) >= 0)
              .orElse(stateFromDisk);
      if (preState.getSlot().equals(slot)) {
        return preState;
      }
      try (final Stream<SignedBeaconBlock> blocks =
          database.streamFinalizedBlocks(preState.getSlot().plus(ONE), slot)) {
        final BeaconState state = StreamingStateRegenerator.regenerate(preState, blocks);
        availableSlots.add(state.getSlot());
        return state;
      }
    }
  }

  /**
   * Cache doesn't allow returning null but we may not be able to regenerate a state so throw this
   * exception and catch it in {@link #getFinalizedState(UnsignedLong)}
   */
  private static class StateUnavailableException extends RuntimeException {}
}
