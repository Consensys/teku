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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
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
    validatorIndexCache.invalidateWithNewValue(publicKey, latestFinalizedIndex);

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

    assertThat(validatorIndexCache.getCacheSize()).isEqualTo(NUMBER_OF_VALIDATORS);
    assertThat(validatorIndexCache.getLastCachedIndex()).isEqualTo(latestFinalizedIndex);
  }

  @Test
  public void shouldStartScanningFinalizedStateFromLastCachedIndex() {
    final SszList<Validator> validators = state.getValidators();
    final int latestFinalizedIndex = NUMBER_OF_VALIDATORS - 1;
    final int lastCachedIndex = 31;
    final ValidatorIndexCache validatorIndexCache =
        new ValidatorIndexCache(cache, latestFinalizedIndex, lastCachedIndex);

    when(cache.getCached(any())).thenReturn(Optional.empty());
    // mock cache needs to report the size changed
    when(cache.size()).thenReturn(31).thenReturn(32);

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
    assertThat(validatorIndexCache.getCacheSize()).isEqualTo(NUMBER_OF_VALIDATORS);

    assertThat(index).isEmpty();
  }

  @Test
  public void shouldUpdateLatestFinalizedIndex() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();

    assertThat(validatorIndexCache.getLatestFinalizedIndex()).isEqualTo(-1);

    validatorIndexCache.updateLatestFinalizedIndex(state);

    assertThat(validatorIndexCache.getLatestFinalizedIndex()).isEqualTo(63);
  }

  @Test
  public void shouldInvalidatePublicKeyIfFinalized() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    validatorIndexCache.updateLatestFinalizedIndex(state);

    final BLSPublicKey updatedPublicKey = dataStructureUtil.randomPublicKey();
    validatorIndexCache.invalidateWithNewValue(updatedPublicKey, 30);

    assertThat(validatorIndexCache.getCacheSize()).isOne();
    assertThat(validatorIndexCache.getValidatorIndex(state, updatedPublicKey)).hasValue(30);
  }

  @Test
  @Disabled
  public void shouldNotInvalidatePublicKeyIfIndexIsNotFinalized() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();

    final BLSPublicKey updatedPublicKey = dataStructureUtil.randomPublicKey();
    validatorIndexCache.invalidateWithNewValue(updatedPublicKey, 30);

    // nothing has been cached because the state is not finalized
    assertThat(validatorIndexCache.getCacheSize()).isZero();
    assertThat(validatorIndexCache.getValidatorIndex(state, updatedPublicKey)).isEmpty();
  }

  @Test
  public void noopCacheShouldFindTheSameIndexMoreThanOnce() {
    final int validatorIndex = 2;
    assertThat(ValidatorIndexCache.NO_OP_INSTANCE.getLastCachedIndex()).isEqualTo(-1);
    assertThat(
            ValidatorIndexCache.NO_OP_INSTANCE.getValidatorIndex(
                state, state.getValidators().get(validatorIndex).getPublicKey()))
        .contains(validatorIndex);
    assertThat(ValidatorIndexCache.NO_OP_INSTANCE.getLastCachedIndex()).isEqualTo(-1);
    assertThat(
            ValidatorIndexCache.NO_OP_INSTANCE.getValidatorIndex(
                state, state.getValidators().get(validatorIndex).getPublicKey()))
        .contains(validatorIndex);
  }
}
