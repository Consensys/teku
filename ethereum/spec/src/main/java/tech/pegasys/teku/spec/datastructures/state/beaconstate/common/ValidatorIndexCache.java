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
  private final Cache<BLSPublicKey, Integer> validatorIndices;
  private final AtomicInteger lastCachedIndex;

  private static final int INDEX_NONE = -1;
  private final AtomicInteger latestFinalizedIndex;
  static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE, INDEX_NONE);

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
    this.validatorIndices = LRUCache.create(Integer.MAX_VALUE - 1);
    this.lastCachedIndex = new AtomicInteger(INDEX_NONE);
    latestFinalizedIndex = new AtomicInteger(INDEX_NONE);
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    final Optional<Integer> validatorIndex = validatorIndices.getCached(publicKey);
    if (validatorIndex.isPresent()) {
      return validatorIndex.filter(index -> index < state.getValidators().size());
    }

    return findIndexFromState(state.getValidators(), publicKey);
  }

  private Optional<Integer> findIndexFromState(
      final SszList<Validator> validatorList, final BLSPublicKey publicKey) {
    for (int i = Math.max(lastCachedIndex.get() + 1, 0); i < validatorList.size(); i++) {
      BLSPublicKey pubKey = validatorList.get(i).getPublicKey();
      // from electra, only finalized validator indices are stable,
      // so we look to store only finalized indices not already stored.
      validatorIndices.invalidateWithNewValue(pubKey, i);
      updateLastIndex(i);
      if (pubKey.equals(publicKey)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  private void updateLastIndex(final int i) {
    lastCachedIndex.updateAndGet(curr -> Math.max(curr, i));
  }

  public void invalidateWithNewValue(final BLSPublicKey pubKey, final int updatedIndex) {
    if (latestFinalizedIndex.get() >= updatedIndex) {
      validatorIndices.invalidateWithNewValue(pubKey, updatedIndex);
    } else {
      LOG.trace("Ignoring invalidateWithNewValue {}:{}", pubKey, updatedIndex);
    }
  }

  @VisibleForTesting
  int getLastCachedIndex() {
    return lastCachedIndex.get();
  }

  @VisibleForTesting
  Cache<BLSPublicKey, Integer> getValidatorIndices() {
    return validatorIndices;
  }

  public void updateLatestFinalizedIndex(final BeaconState finalizedState) {
    LOG.trace("Update finalized index: {}", finalizedState.getSlot());
    latestFinalizedIndex.updateAndGet(
        curr -> Math.max(curr, finalizedState.getValidators().size() - 1));
  }

  public int getLatestFinalizedIndex() {
    return latestFinalizedIndex.get();
  }
}
