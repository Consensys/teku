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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.NoOpCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatorIndexCache {
  private static final Logger LOG = LogManager.getLogger();
  private final IndexCache finalizedCache;
  private final IndexCache hotCache;

  static final int INDEX_NONE = -1;
  static final ValidatorIndexCache NO_OP_INSTANCE =
      new ValidatorIndexCache(NoOpCache.getNoOpCache(), INDEX_NONE, INDEX_NONE, UInt64.ZERO);

  public ValidatorIndexCache() {
    this.finalizedCache = new FinalizedValidatorIndexCache();
    this.hotCache = new HotValidatorIndexCache(this.finalizedCache);
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    final SszList<Validator> stateValidators = state.getValidators();
    final Optional<Integer> validatorIndex = finalizedCache.find(stateValidators.size(), publicKey);
    if (validatorIndex.isPresent()) {
      return validatorIndex;
    }
    final boolean isFinalizedState =
        state.getSlot().isLessThanOrEqualTo(finalizedCache.getFinalizedSlot());
    if (!isFinalizedState) {
      final Optional<Integer> nonFinalIndex = hotCache.find(stateValidators.size(), publicKey);
      // can't assume the public key found previously matches, need to re-check based on this state.
      if (nonFinalIndex.isPresent()
          && stateValidators.get(nonFinalIndex.get()).getPublicKey().equals(publicKey)) {
        return nonFinalIndex;
      }
    }
    return findIndexFromValidatorList(stateValidators, publicKey, isFinalizedState);
  }

  public void invalidateWithNewValue(
      final BLSPublicKey pubKey, final int updatedIndex, final UInt64 slot) {
    invalidateWithNewValue(
        pubKey, updatedIndex, finalizedCache.getFinalizedSlot().isGreaterThanOrEqualTo(slot));
  }

  public void invalidateWithNewValue(
      final BLSPublicKey pubKey, final int updatedIndex, final boolean isFinalizedState) {
    LOG.trace(
        "invalidateWithNewValue pubkey {}, index {}, isFinalized {}",
        pubKey,
        updatedIndex,
        isFinalizedState);
    if (!finalizedCache.invalidateWithNewValue(pubKey, updatedIndex, isFinalizedState)) {
      hotCache.invalidateWithNewValue(pubKey, updatedIndex, isFinalizedState);
    }
  }

  public int getLatestFinalizedIndex() {
    return finalizedCache.getLatestFinalizedIndex();
  }

  public void updateLatestFinalizedIndex(final BeaconState finalizedState) {
    finalizedCache.updateLatestFinalizedIndex(finalizedState);
    hotCache.updateLatestFinalizedIndex(finalizedState);
  }

  @VisibleForTesting
  ValidatorIndexCache(
      final Cache<BLSPublicKey, Integer> finalizedValidatorIndices,
      final int latestFinalizedIndex,
      final int lastCachedIndex,
      final UInt64 lastFinalizedSlot) {
    this.finalizedCache =
        new FinalizedValidatorIndexCache(
            finalizedValidatorIndices, latestFinalizedIndex, lastCachedIndex, lastFinalizedSlot);
    this.hotCache = new HotValidatorIndexCache(this.finalizedCache);
  }

  @VisibleForTesting
  int getFinalizedCacheSize() {
    return finalizedCache.getSize();
  }

  @VisibleForTesting
  int getHotValidatorIndexSize() {
    return hotCache.getSize();
  }

  @VisibleForTesting
  int getLastCachedIndex() {
    return finalizedCache.getLastCachedIndex();
  }

  private Optional<Integer> findIndexFromValidatorList(
      final SszList<Validator> validatorList,
      final BLSPublicKey publicKey,
      final boolean isFinalizedState) {
    final int startPosition = finalizedCache.getLastCachedIndex() + 1;

    for (int i = startPosition; i < validatorList.size(); i++) {
      final BLSPublicKey pubKey = validatorList.get(i).getPublicKey();
      invalidateWithNewValue(pubKey, i, isFinalizedState);
      if (pubKey.equals(publicKey)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }
}
