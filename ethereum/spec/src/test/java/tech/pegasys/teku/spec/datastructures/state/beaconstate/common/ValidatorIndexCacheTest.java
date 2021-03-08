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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ValidatorIndexCacheTest {
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  final BeaconState state = dataStructureUtil.randomBeaconState();
  final BLSPublicKey missingPublicKey = dataStructureUtil.randomPublicKey();

  @SuppressWarnings("unchecked")
  final Cache<BLSPublicKey, Integer> cache = mock(Cache.class);

  @Test
  public void shouldNotScanStateIfAlreadyHaveValidators() {
    final ValidatorIndexCache validatorIndexCache =
        new ValidatorIndexCache(cache, state.getValidators().size());

    when(cache.getCached(missingPublicKey)).thenReturn(Optional.empty());
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);

    verify(cache).getCached(missingPublicKey);
    verify(cache, never()).invalidateWithNewValue(any(), any());
    assertThat(index).isEmpty();
  }

  @Test
  public void shouldScanNewValidatorsInSuppliedState() {
    final ValidatorIndexCache validatorIndexCache =
        new ValidatorIndexCache(cache, state.getValidators().size() - 5);

    when(cache.getCached(missingPublicKey)).thenReturn(Optional.empty());
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);
    verify(cache).getCached(missingPublicKey);
    verify(cache, times(5)).invalidateWithNewValue(any(), any());
    assertThat(index).isEmpty();
  }

  @Test
  public void shouldGetAllValidatorKeysCachedIfMissingKeyPassed() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);
    assertThat(index).isEmpty();
    assertThat(validatorIndexCache.getValidatorIndexes().size())
        .isEqualTo(state.getValidators().size());
  }

  @Test
  public void shouldPopulateCacheItemsFromState() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    final BLSPublicKey foundKey =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(10).getPubkey());

    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, foundKey);
    assertThat(index.get()).isEqualTo(10);
    assertThat(validatorIndexCache.getLastIndex()).isEqualTo(10);
    assertThat(validatorIndexCache.getValidatorIndexes().size()).isEqualTo(11);
  }

  @Test
  public void shouldFilterItemsBeyondStateIndex() {
    final ValidatorIndexCache validatorIndexCache = new ValidatorIndexCache();
    validatorIndexCache.invalidateWithNewValue(missingPublicKey, 100);
    final Optional<Integer> index = validatorIndexCache.getValidatorIndex(state, missingPublicKey);

    assertThat(index).isEmpty();
    // state didn't get scanned, because we had the index but it was out of bounds
    assertThat(validatorIndexCache.getLastIndex()).isEqualTo(-1);
    assertThat(validatorIndexCache.getValidatorIndexes().size()).isEqualTo(1);
  }
}
