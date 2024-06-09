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

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class HotValidatorIndexCache implements IndexCache {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<BLSPublicKey, Integer> hotValidatorIndices;
  private final IndexCache finalizedDelegate;

  HotValidatorIndexCache(final IndexCache finalizedDelegate) {
    this.hotValidatorIndices = new HashMap<>(1024);
    this.finalizedDelegate = finalizedDelegate;
  }

  @VisibleForTesting
  HotValidatorIndexCache(
      final Map<BLSPublicKey, Integer> hotValidatorIndices, final IndexCache finalizedDelegate) {
    this.hotValidatorIndices = hotValidatorIndices;
    this.finalizedDelegate = finalizedDelegate;
  }

  @Override
  public boolean invalidateWithNewValue(
      final BLSPublicKey pubKey, final int updatedIndex, final boolean isFinalizedState) {
    hotValidatorIndices.put(pubKey, updatedIndex);
    LOG.trace(
        "Add hot validator index {} -> {}, count {}",
        pubKey,
        updatedIndex,
        hotValidatorIndices.size());
    return true;
  }

  @Override
  public Optional<Integer> find(final int stateValidatorCount, final BLSPublicKey publicKey) {
    try {
      final Optional<Integer> nonFinalIndex =
          Optional.ofNullable(hotValidatorIndices.get(publicKey));
      return nonFinalIndex.filter(index -> index < stateValidatorCount);
    } catch (Exception ex) {
      LOG.trace("hot validator index lookup failed", ex);
    }
    return Optional.empty();
  }

  @Override
  public void updateLatestFinalizedIndex(final BeaconState finalizedState) {
    hotValidatorIndices.clear();
  }

  @Override
  public int getSize() {
    return hotValidatorIndices.size();
  }

  @Override
  public int getLastCachedIndex() {
    return finalizedDelegate.getLastCachedIndex();
  }

  @Override
  public UInt64 getFinalizedSlot() {
    return finalizedDelegate.getFinalizedSlot();
  }

  @Override
  public int getLatestFinalizedIndex() {
    return finalizedDelegate.getLatestFinalizedIndex();
  }
}
