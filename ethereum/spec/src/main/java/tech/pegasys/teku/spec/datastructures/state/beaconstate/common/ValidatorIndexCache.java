/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatorIndexCache {
  private final Cache<BLSPublicKey, Integer> validatorIndices;
  private final AtomicInteger lastCachedIndex;

  private static final int INDEX_NONE = -1;
  private int latestFinalizedIndex;
  private UInt64 latestFinalizedSlot;
  public static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE, INDEX_NONE, UInt64.ZERO);

  @VisibleForTesting
  public ValidatorIndexCache(
      final Cache<BLSPublicKey, Integer> validatorIndices,
      final int latestFinalizedIndex,
      final int lastCachedIndex,
      final UInt64 latestFinalizedSlot) {
    this.validatorIndices = validatorIndices;
    this.latestFinalizedIndex = latestFinalizedIndex;
    this.latestFinalizedSlot = latestFinalizedSlot;
    this.lastCachedIndex = new AtomicInteger(lastCachedIndex);
  }

  public ValidatorIndexCache() {
    this.validatorIndices = LRUCache.create(Integer.MAX_VALUE - 1);
    this.lastCachedIndex = new AtomicInteger(INDEX_NONE);
    latestFinalizedIndex = INDEX_NONE;
    this.latestFinalizedSlot = UInt64.ZERO;
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    if (!state.isVersionElectra() || state.getSlot().isLessThan(latestFinalizedSlot)) {
      return getStableValidatorIndex(state, publicKey);
    }
    return getUnstableValidatorIndex(state, publicKey);
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    validatorIndices.invalidateWithNewValue(pubKey, updatedIndex);
  }

  public synchronized void updateLatestFinalizedState(final BeaconState finalizedState) {
    if (finalizedState.isVersionElectra()) {
      // fix cache entries that may be incorrect
      for (int i = latestFinalizedIndex + 1; i < finalizedState.getValidators().size(); i++) {
        final BLSPublicKey publicKey = finalizedState.getValidators().get(i).getPublicKey();
        invalidateWithNewValue(publicKey, i);
      }
      latestFinalizedIndex =
          Math.max(latestFinalizedIndex, finalizedState.getValidators().size() - 1);
      lastCachedIndex.updateAndGet(curr -> Math.max(curr, latestFinalizedIndex));
      latestFinalizedSlot = latestFinalizedSlot.max(finalizedState.getSlot());
    }
  }

  /*
   * Pre Electra logic
   *   - validator indices always stable
   *   - because of stability, no need to track finalized context
   *
   * Post Electra
   *   - non-finalized indices are not guaranteed
   *   - finalization will update any indices that are now 'stable'
   *   - this requires tracking and updating on finalization boundary.
   *   - Even non final indices go into the LRU, its just they're not
   *     guaranteed, and need validating after fetching.
   */

  Optional<Integer> getStableValidatorIndex(final BeaconState state, final BLSPublicKey publicKey) {
    final Optional<Integer> validatorIndex = validatorIndices.getCached(publicKey);
    if (validatorIndex.isPresent()) {
      return validatorIndex.filter(index -> index < state.getValidators().size());
    }
    return findIndexFromState(state.getValidators(), publicKey, lastCachedIndex.get() + 1);
  }

  Optional<Integer> getUnstableValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    final Optional<Integer> validatorIndex = validatorIndices.getCached(publicKey);
    if (validatorIndex.isPresent()
        && state.getValidators().get(validatorIndex.get()).getPublicKey().equals(publicKey)) {
      return validatorIndex.filter(index -> index < state.getValidators().size());
    }
    return findIndexFromState(
        state.getValidators(),
        publicKey,
        Math.min(getLastCachedIndex() + 1, latestFinalizedIndex + 1));
  }

  @VisibleForTesting
  public int getLatestFinalizedIndex() {
    return latestFinalizedIndex;
  }

  @VisibleForTesting
  UInt64 getLatestFinalizedSlot() {
    return latestFinalizedSlot;
  }

  @VisibleForTesting
  int getLastCachedIndex() {
    return lastCachedIndex.get();
  }

  @VisibleForTesting
  int getCacheSize() {
    return validatorIndices.size();
  }

  private void updateLastIndex(final int i) {
    lastCachedIndex.updateAndGet(curr -> Math.max(curr, i));
  }

  private Optional<Integer> findIndexFromState(
      final SszList<Validator> validatorList,
      final BLSPublicKey publicKey,
      final int searchStartIndex) {
    final int initialCacheSize = getCacheSize();
    for (int i = searchStartIndex; i < validatorList.size(); i++) {
      final BLSPublicKey pubKey = validatorList.get(i).getPublicKey();
      validatorIndices.invalidateWithNewValue(pubKey, i);
      if (pubKey.equals(publicKey)) {
        if (initialCacheSize < getCacheSize()) {
          updateLastIndex(i);
        }
        return Optional.of(i);
      }
    }
    if (initialCacheSize < getCacheSize()) {
      updateLastIndex(getCacheSize() - 1);
    }
    return Optional.empty();
  }
}
