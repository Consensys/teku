/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.collections.cache.StripedCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatorIndexCache {
  private final Cache<BLSPublicKey, Integer> validatorIndices;
  private final AtomicInteger lastCachedIndex;

  private static final int INDEX_NONE = -1;
  public static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE);

  @VisibleForTesting
  ValidatorIndexCache(
      final Cache<BLSPublicKey, Integer> validatorIndices, final int lastCachedIndex) {
    this.validatorIndices = validatorIndices;
    this.lastCachedIndex = new AtomicInteger(lastCachedIndex);
  }

  /** Constructor for benchmarking - allows testing different cache implementations. */
  @VisibleForTesting
  ValidatorIndexCache(final Cache<BLSPublicKey, Integer> validatorIndices) {
    this(validatorIndices, INDEX_NONE);
  }

  public ValidatorIndexCache() {
    this(StripedCache.createUnbounded());
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    final Optional<Integer> validatorIndex = validatorIndices.getCached(publicKey);
    if (validatorIndex.isPresent()) {
      return validatorIndex.filter(index -> index < state.getValidators().size());
    }

    return findIndexFromState(state.getValidators(), publicKey);
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    validatorIndices.invalidateWithNewValue(pubKey, updatedIndex);
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

  @VisibleForTesting
  Cache<BLSPublicKey, Integer> getValidatorIndices() {
    return validatorIndices;
  }

  private Optional<Integer> findIndexFromState(
      final SszList<Validator> validatorList, final BLSPublicKey publicKey) {
    int lastAddedIndex = -1;

    for (int i = Math.max(lastCachedIndex.get() + 1, 0); i < validatorList.size(); i++) {
      final BLSPublicKey pubKey = validatorList.get(i).getPublicKey();
      validatorIndices.invalidateWithNewValue(pubKey, i);
      lastAddedIndex = i;

      if (pubKey.equals(publicKey)) {
        updateLastIndex(lastAddedIndex);
        return Optional.of(i);
      }
    }

    // If we scanned any validators, update lastCachedIndex
    if (lastAddedIndex != -1) {
      updateLastIndex(lastAddedIndex);
    }
    return Optional.empty();
  }
}
