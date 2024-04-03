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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatorIndexCache {

  private static final Logger LOG = LogManager.getLogger();

  private static final int INDEX_NONE = -1;

  static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE, INDEX_NONE);

  private final Cache<BLSPublicKey, Integer> validatorIndices;

  private final AtomicInteger latestFinalizedIndex;
  private final AtomicInteger lastCachedIndex;

  @VisibleForTesting
  ValidatorIndexCache(
      final Cache<BLSPublicKey, Integer> validatorIndices,
      final int latestFinalizedIndex,
      final int lastCachedIndex) {
    this.validatorIndices = validatorIndices;
    this.latestFinalizedIndex = new AtomicInteger(latestFinalizedIndex);
    this.lastCachedIndex = new AtomicInteger(lastCachedIndex);
  }

  public ValidatorIndexCache() {
    this(LRUCache.create(Integer.MAX_VALUE - 1), INDEX_NONE, INDEX_NONE);
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    // Store latestFinalizedIndex here in case we need to scan keys from the state.
    // This ensures we're adding from a point that we're confident the cache is at
    // when we scan for more keys through the state later.
    LOG.info("Searching for {} in the cache", publicKey);
    final int latestFinalizedIndexSnapshot = latestFinalizedIndex.get();
    LOG.info("Latest finalized index is: {}", latestFinalizedIndexSnapshot);
    final SszList<Validator> validators = state.getValidators();
    Optional<Integer> cached = validatorIndices.getCached(publicKey);
    cached.ifPresent(i -> LOG.info("Cache hit for {} (index: {})", publicKey, i));
    return cached
        .or(() -> findIndexFromFinalizedState(validators, publicKey, latestFinalizedIndexSnapshot))
        .or(
            () ->
                findIndexFromNonFinalizedState(
                    validators, publicKey, latestFinalizedIndexSnapshot));
  }

  private Optional<Integer> findIndexFromFinalizedState(
      final SszList<Validator> validators,
      final BLSPublicKey publicKey,
      final int latestFinalizedIndex) {
    int to = Math.min(latestFinalizedIndex, validators.size() - 1);
    int from = lastCachedIndex.get() + 1;
    LOG.info("Scanning for {} in finalized state from {} to {}", publicKey, from, to);
    for (int i = from; i <= to; i++) {
      final BLSPublicKey pubKey = validators.get(i).getPublicKey();
      // cache finalized mapping
      validatorIndices.invalidateWithNewValue(pubKey, i);
      updateLastCachedIndex(i);
      if (pubKey.equals(publicKey)) {
        LOG.info("Found {} in finalized state (index: {})", pubKey, i);
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  private void updateLastCachedIndex(final int updatedIndex) {
    lastCachedIndex.updateAndGet(curr -> Math.max(curr, updatedIndex));
  }

  private Optional<Integer> findIndexFromNonFinalizedState(
      final SszList<Validator> validators,
      final BLSPublicKey publicKey,
      final int latestFinalizedIndex) {
    LOG.info(
        "Scanning for {} in non-finalized state from {} to {}",
        publicKey,
        latestFinalizedIndex + 1,
        validators.size() - 1);
    for (int i = latestFinalizedIndex + 1; i < validators.size(); i++) {
      final BLSPublicKey pubKey = validators.get(i).getPublicKey();
      if (pubKey.equals(publicKey)) {
        LOG.info("Found {} in non-finalized state (index: {})", pubKey, i);
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  public void updateLatestFinalizedIndex(final BeaconState finalizedState) {
    latestFinalizedIndex.updateAndGet(
        curr -> Math.max(curr, finalizedState.getValidators().size() - 1));
  }

  @VisibleForTesting
  public int getLatestFinalizedIndex() {
    return latestFinalizedIndex.get();
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    if (updatedIndex > latestFinalizedIndex.get()) {
      // do not cache if index is not finalized
      return;
    }
    validatorIndices.invalidateWithNewValue(pubKey, updatedIndex);
  }

  @VisibleForTesting
  Cache<BLSPublicKey, Integer> getValidatorIndices() {
    return validatorIndices;
  }

  @VisibleForTesting
  int getLastCachedIndex() {
    return lastCachedIndex.get();
  }
}
