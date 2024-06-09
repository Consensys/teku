/*
 * Copyright Consensys Software Inc., 2024
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

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.ValidatorIndexCache.INDEX_NONE;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class FinalizedValidatorIndexCache implements IndexCache {
  private static final Logger LOG = LogManager.getLogger();
  private final Cache<BLSPublicKey, Integer> finalizedValidatorIndices;
  private final AtomicInteger lastCachedIndex;
  private int latestFinalizedIndex;
  private UInt64 finalizedSlot;

  public FinalizedValidatorIndexCache(
      final Cache<BLSPublicKey, Integer> finalizedValidatorIndices,
      final int latestFinalizedIndex,
      final int lastCachedIndex,
      final UInt64 finalizedSlot) {
    this.finalizedValidatorIndices = finalizedValidatorIndices;
    this.lastCachedIndex = new AtomicInteger(lastCachedIndex);
    this.latestFinalizedIndex = latestFinalizedIndex;
    this.finalizedSlot = finalizedSlot;
  }

  public FinalizedValidatorIndexCache() {
    this.finalizedValidatorIndices = LRUCache.create(Integer.MAX_VALUE - 1);
    this.lastCachedIndex = new AtomicInteger(INDEX_NONE);
    this.latestFinalizedIndex = INDEX_NONE;
    this.finalizedSlot = UInt64.ZERO;
  }

  @Override
  public boolean invalidateWithNewValue(
      final BLSPublicKey pubKey, final int updatedIndex, final boolean isFinalizedState) {
    if (isFinalizedState || getLatestFinalizedIndex() >= updatedIndex) {
      finalizedValidatorIndices.invalidateWithNewValue(pubKey, updatedIndex);
      updateLastCachedIndex(updatedIndex);
      LOG.trace(
          "Add final {} -> {}, count {}", pubKey, updatedIndex, finalizedValidatorIndices.size());
      return true;
    }
    return false;
  }

  @Override
  public Optional<Integer> find(final int stateValidatorCount, final BLSPublicKey publicKey) {
    final Optional<Integer> validatorIndex = finalizedValidatorIndices.getCached(publicKey);
    return validatorIndex.filter(index -> index < stateValidatorCount);
  }

  @Override
  public void updateLatestFinalizedIndex(final BeaconState finalizedState) {
    final int lastIndex = finalizedState.getValidators().size() - 1;
    updateFinalizedStateData(lastIndex, finalizedState.getSlot());
  }

  @Override
  public int getSize() {
    return finalizedValidatorIndices.size();
  }

  @Override
  public int getLastCachedIndex() {
    return lastCachedIndex.get();
  }

  @Override
  public int getLatestFinalizedIndex() {
    return latestFinalizedIndex;
  }

  @Override
  public UInt64 getFinalizedSlot() {
    return finalizedSlot;
  }

  public void updateLastCachedIndex(final int newValue) {
    this.lastCachedIndex.updateAndGet(curr -> Math.max(curr, newValue));
  }

  private synchronized void updateFinalizedStateData(final int lastIndex, final UInt64 slot) {
    this.latestFinalizedIndex = lastIndex;
    this.finalizedSlot = slot;
  }
}
