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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ValidatorIndexCacheTest {

  private static final int NUMBER_OF_VALIDATORS = 64;

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconState state = dataStructureUtil.randomBeaconState(NUMBER_OF_VALIDATORS);

  @SuppressWarnings("unchecked")
  final Cache<BLSPublicKey, Integer> cache = mock(Cache.class);

  @Test
  public void shouldReturnEmptyIfValidatorIndexIsNotConsistentWithNumberOfValidatorsInState() {
    final SszList<Validator> validators = state.getValidators();
    final int latestFinalizedIndex = NUMBER_OF_VALIDATORS - 1;
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);

    final BLSPublicKey publicKey = validators.get(latestFinalizedIndex).getPublicKey();
    // cache eagerly the last validator public key
    validatorIndexCache.invalidateWithNewValue(publicKey, latestFinalizedIndex, true);

    // state with one less validator
    final BeaconState state = dataStructureUtil.randomBeaconState(NUMBER_OF_VALIDATORS - 1);

    assertThat(validatorIndexCache.getValidatorIndex(state, publicKey)).isEmpty();
  }

  @Test
  public void shouldScanFinalizedStateAndCache() {
    final SszList<Validator> validators = state.getValidators();
    final int latestFinalizedIndex = NUMBER_OF_VALIDATORS - 1;
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);

    final Optional<Integer> index =
        validatorIndexCache.getValidatorIndex(
            state, validators.get(latestFinalizedIndex).getPublicKey());

    assertThat(index).hasValue(latestFinalizedIndex);

    assertThat(validatorIndexCache.getFinalizedCacheSize()).isEqualTo(NUMBER_OF_VALIDATORS);
    assertThat(validatorIndexCache.getLatestFinalizedIndex()).isEqualTo(latestFinalizedIndex);
  }

  @Test
  public void shouldStartScanningFinalizedStateFromLastCachedIndex() {
    final SszList<Validator> validators = state.getValidators();
    final int latestFinalizedIndex = NUMBER_OF_VALIDATORS - 1;
    final int lastCachedIndex = 31;
    final ValidatorIndexCache validatorIndexCache =
        new ValidatorIndexCache(cache, latestFinalizedIndex, lastCachedIndex, state.getSlot());

    when(cache.getCached(any())).thenReturn(Optional.empty());

    final Optional<Integer> index =
        validatorIndexCache.getValidatorIndex(
            state, validators.get(latestFinalizedIndex).getPublicKey());

    // last cached index is 31, so need to cache 32 more validators (final index - 63)
    verify(cache, times(32)).invalidateWithNewValue(any(), any());
    assertThat(index).hasValue(latestFinalizedIndex);

    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(latestFinalizedIndex);
  }

  @Test
  public void shouldReturnEmptyIfPubkeyNotFoundInState() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);

    final Optional<Integer> index =
        validatorIndexCache.getValidatorIndex(state, dataStructureUtil.randomPublicKey());

    // all keys were cached
    assertThat(validatorIndexCache.getFinalizedCacheSize()).isEqualTo(NUMBER_OF_VALIDATORS);

    assertThat(index).isEmpty();
  }

  @Test
  public void shouldInvalidatePublicKeyIfFinalized() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);

    final BLSPublicKey updatedPublicKey = dataStructureUtil.randomPublicKey();
    validatorIndexCache.invalidateWithNewValue(updatedPublicKey, 30, true);

    assertThat(validatorIndexCache.getFinalizedCacheSize()).isOne();
    assertThat(validatorIndexCache.getValidatorIndex(state, updatedPublicKey)).hasValue(30);
  }

  @Test
  public void nonFinalizedIndicesShouldBeStoredInNonFinalStorage() {
    final Cache<BLSPublicKey, Integer> cache = LRUCache.create(Integer.MAX_VALUE - 1);
    // Cache with the last finalized state containing 24 validators, at slot 64.
    final ValidatorIndexCache validatorIndexCache =
        new ValidatorIndexCache(cache, 23, -1, UInt64.valueOf(64));

    // a state with 30 indices, at slot 80
    final BeaconState state = dataStructureUtil.randomBeaconState(30, 100, UInt64.valueOf(80));
    validatorIndexCache.getValidatorIndex(state, state.getValidators().get(29).getPublicKey());
    // 30 indices, 24 of which are final, means 6 non-final
    assertThat(validatorIndexCache.getHotValidatorIndexSize()).isEqualTo(6);
    final int nonFinalIndex = 28;
    assertThat(
            validatorIndexCache.getValidatorIndex(
                state, state.getValidators().get(nonFinalIndex).getPublicKey()))
        .contains(nonFinalIndex);
  }
}
