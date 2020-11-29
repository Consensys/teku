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

package tech.pegasys.teku.datastructures.state;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class ValidatorIndexCache {
  private final Cache<BLSPublicKey, Integer> validatorIndexes;
  private final AtomicInteger lastIndex;

  private static final int INDEX_NONE = -1;
  static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE);

  @VisibleForTesting
  ValidatorIndexCache(final Cache<BLSPublicKey, Integer> validatorIndexes, final int lastIndex) {
    this.validatorIndexes = validatorIndexes;
    this.lastIndex = new AtomicInteger(lastIndex);
  }

  public ValidatorIndexCache() {
    this.validatorIndexes = new LRUCache<>(Integer.MAX_VALUE - 1);
    this.lastIndex = new AtomicInteger(INDEX_NONE);
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    // Store lastIndex here in case we need to scan keys from the state.
    // This ensures we're adding from a point that we're confident the cache is at
    // when we scan for more keys through the state later.
    final int lastIndexSnapshot = lastIndex.get();

    final Optional<Integer> validatorIndex = validatorIndexes.getCached(publicKey);
    if (validatorIndex.isPresent()) {
      return validatorIndex.filter(index -> index < state.getValidators().size());
    }

    return findIndexFromState(state.getValidators(), publicKey, lastIndexSnapshot);
  }

  private Optional<Integer> findIndexFromState(
      final SSZList<Validator> validatorList,
      final BLSPublicKey publicKey,
      final int lastIndexSnapshot) {
    for (int i = Math.max(lastIndexSnapshot, 0); i < validatorList.size(); i++) {
      BLSPublicKey pubKey = BLSPublicKey.fromBytesCompressed(validatorList.get(i).getPubkey());
      validatorIndexes.invalidateWithNewValue(pubKey, i);
      if (pubKey.equals(publicKey)) {
        updateLastIndex(i);
        return Optional.of(i);
      }
    }

    updateLastIndex(validatorList.size());
    return Optional.empty();
  }

  private void updateLastIndex(final int i) {
    lastIndex.updateAndGet(curr -> Math.max(curr, i));
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    validatorIndexes.invalidateWithNewValue(pubKey, updatedIndex);
  }

  @VisibleForTesting
  int getLastIndex() {
    return lastIndex.get();
  }

  @VisibleForTesting
  Cache<BLSPublicKey, Integer> getValidatorIndexes() {
    return validatorIndexes;
  }
}
