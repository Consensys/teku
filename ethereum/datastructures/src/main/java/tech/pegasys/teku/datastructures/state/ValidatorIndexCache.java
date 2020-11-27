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

  static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), Integer.MAX_VALUE - 1);

  @VisibleForTesting
  ValidatorIndexCache(final Cache<BLSPublicKey, Integer> validatorIndexes, final int lastIndex) {
    this.validatorIndexes = validatorIndexes;
    this.lastIndex = new AtomicInteger(lastIndex);
  }

  @VisibleForTesting
  ValidatorIndexCache(final Cache<BLSPublicKey, Integer> validatorIndexes) {
    this(validatorIndexes, -1);
  }

  public ValidatorIndexCache() {
    this.validatorIndexes = new LRUCache<>(Integer.MAX_VALUE - 1);
    this.lastIndex = new AtomicInteger(-1);
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    final Optional<Integer> validatorIndex = validatorIndexes.getCached(publicKey);
    if (validatorIndex.isPresent()) {
      return validatorIndex.filter(index -> index < state.getValidators().size());
    }

    if (lastIndex.get() < state.getValidators().size()) {
      return findIndexFromState(state, publicKey);
    }
    return Optional.empty();
  }

  private Optional<Integer> findIndexFromState(
      final BeaconState state, final BLSPublicKey publicKey) {
    final SSZList<Validator> validatorList = state.getValidators();
    for (int i = Math.max(lastIndex.get(), 0); i < validatorList.size(); i++) {
      final int currentIndex = i;
      lastIndex.updateAndGet(curr -> Math.max(curr, currentIndex));
      BLSPublicKey pubKey = BLSPublicKey.fromBytesCompressed(validatorList.get(i).getPubkey());
      validatorIndexes.invalidateWithNewValue(pubKey, i);
      if (pubKey.equals(publicKey)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    if (lastIndex.get() < updatedIndex) {
      // won't reset last index here, as we haven't scanned from last index up to this index
      validatorIndexes.invalidateWithNewValue(pubKey, updatedIndex);
    }
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
