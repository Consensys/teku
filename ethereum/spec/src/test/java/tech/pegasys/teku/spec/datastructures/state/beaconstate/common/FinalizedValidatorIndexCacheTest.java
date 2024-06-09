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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class FinalizedValidatorIndexCacheTest {
  private static final int NUMBER_OF_VALIDATORS = 64;

  @SuppressWarnings("unchecked")
  final Cache<BLSPublicKey, Integer> cache = mock(Cache.class);

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconState state = dataStructureUtil.randomBeaconState(NUMBER_OF_VALIDATORS);

  @Test
  public void finalizedStateContextStoredInCache() {
    final FinalizedValidatorIndexCache validatorIndexCache =
        new FinalizedValidatorIndexCache(cache, 23, -1, UInt64.valueOf(64));
    // verify correct initialization
    assertThat(validatorIndexCache.getLatestFinalizedIndex()).isEqualTo(23);
    assertThat(validatorIndexCache.getFinalizedSlot()).isEqualTo(UInt64.valueOf(64));

    // a state with 30 indices, at slot 80
    final BeaconState state = dataStructureUtil.randomBeaconState(30, 100, UInt64.valueOf(80));
    validatorIndexCache.updateLatestFinalizedIndex(state);

    // verify update from state object
    assertThat(validatorIndexCache.getLatestFinalizedIndex()).isEqualTo(29);
    assertThat(validatorIndexCache.getFinalizedSlot()).isEqualTo(UInt64.valueOf(80));
  }

  @Test
  public void shouldUpdateLatestFinalizedIndex() {
    final FinalizedValidatorIndexCache validatorIndexCache = new FinalizedValidatorIndexCache();

    assertThat(validatorIndexCache.getLatestFinalizedIndex()).isEqualTo(-1);

    validatorIndexCache.updateLatestFinalizedIndex(state);

    assertThat(validatorIndexCache.getLatestFinalizedIndex()).isEqualTo(63);
  }

  @Test
  public void invalidateWithNewValueUpdatesCache() {
    final FinalizedValidatorIndexCache validatorIndexCache = new FinalizedValidatorIndexCache();
    validatorIndexCache.invalidateWithNewValue(
        state.getValidators().get(0).getPublicKey(), 0, true);
    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(0);
    assertThat(validatorIndexCache.getSize()).isEqualTo(1);
  }

  @Test
  public void invalidateWithNewValueIgnoresOutOfRange() {
    final FinalizedValidatorIndexCache validatorIndexCache = new FinalizedValidatorIndexCache();
    validatorIndexCache.invalidateWithNewValue(
        state.getValidators().get(0).getPublicKey(), 0, true);
    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(0);
  }

  @Test
  public void invalidateWithNewValueIgnoresNonFinal() {
    final FinalizedValidatorIndexCache validatorIndexCache = new FinalizedValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);
    assertThat(
            validatorIndexCache.invalidateWithNewValue(
                dataStructureUtil.randomPublicKey(), NUMBER_OF_VALIDATORS + 1, false))
        .isFalse();
    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(-1);
  }

  @Test
  public void invalidateWithNewValueWhereFinalizedIndexIsGreater() {
    final FinalizedValidatorIndexCache validatorIndexCache = new FinalizedValidatorIndexCache();
    final int index = 0;
    validatorIndexCache.updateLatestFinalizedIndex(state);
    assertThat(
            validatorIndexCache.invalidateWithNewValue(
                state.getValidators().get(index).getPublicKey(), index, false))
        .isTrue();
    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(index);
  }

  @Test
  public void findFiltersOnIndex() {
    final FinalizedValidatorIndexCache validatorIndexCache = new FinalizedValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);
    for (int i = 0; i < state.getValidators().size(); i++) {
      validatorIndexCache.invalidateWithNewValue(
          state.getValidators().get(i).getPublicKey(), i, true);
    }
    assertThat(validatorIndexCache.find(30, state.getValidators().get(30).getPublicKey()))
        .isEmpty();
    assertThat(validatorIndexCache.find(30, state.getValidators().get(29).getPublicKey()))
        .contains(29);
  }

  @Test
  public void findWithNoIndex() {
    final FinalizedValidatorIndexCache validatorIndexCache = new FinalizedValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);
    assertThat(validatorIndexCache.find(NUMBER_OF_VALIDATORS, dataStructureUtil.randomPublicKey()))
        .isEmpty();
  }
}
