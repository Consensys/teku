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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

class ValidatorIndexProviderTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  @Test
  void shouldReturnEmptyWhenValidatorIsUnknown() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(List.of(key1), validatorApiChannel);
    assertThat(provider.getValidatorIndex(key1)).isEmpty();
  }

  @Test
  void shouldLoadValidatorKeys() {
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<BLSPublicKey> keys = List.of(key1, key2, key3);
    final ValidatorIndexProvider provider = new ValidatorIndexProvider(keys, validatorApiChannel);

    when(validatorApiChannel.getValidatorIndices(keys))
        .thenReturn(SafeFuture.completedFuture(Map.of(key1, 1, key2, 20, key3, 300)));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndex(key1)).contains(1);
    assertThat(provider.getValidatorIndex(key2)).contains(20);
    assertThat(provider.getValidatorIndex(key3)).contains(300);
  }

  @Test
  void shouldLookupValidatorKeysThatWerePreviouslyUnknown() {
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<BLSPublicKey> keys = List.of(key1, key2, key3);
    final ValidatorIndexProvider provider = new ValidatorIndexProvider(keys, validatorApiChannel);

    when(validatorApiChannel.getValidatorIndices(keys))
        .thenReturn(SafeFuture.completedFuture(Map.of(key2, 20)));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndex(key1)).isEmpty();
    assertThat(provider.getValidatorIndex(key2)).contains(20);
    assertThat(provider.getValidatorIndex(key3)).isEmpty();

    when(validatorApiChannel.getValidatorIndices(List.of(key1, key3)))
        .thenReturn(SafeFuture.completedFuture(Map.of(key1, 1, key3, 300)));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndex(key1)).contains(1);
    assertThat(provider.getValidatorIndex(key2)).contains(20);
    assertThat(provider.getValidatorIndex(key3)).contains(300);
  }

  @Test
  void shouldNotMakeConcurrentRequests() {
    final SafeFuture<Map<BLSPublicKey, Integer>> result = new SafeFuture<>();
    when(validatorApiChannel.getValidatorIndices(List.of(key1))).thenReturn(result);

    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(List.of(key1), validatorApiChannel);

    provider.lookupValidators();
    verify(validatorApiChannel).getValidatorIndices(List.of(key1));

    provider.lookupValidators();
    verifyNoMoreInteractions(validatorApiChannel);

    // Can request again once the request completes.
    result.complete(Collections.emptyMap());
    provider.lookupValidators();
    verify(validatorApiChannel, times(2)).getValidatorIndices(List.of(key1));
  }

  @Test
  void shouldNotMakeRequestWhenAllValidatorsAreKnown() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(List.of(key1), validatorApiChannel);

    when(validatorApiChannel.getValidatorIndices(List.of(key1)))
        .thenReturn(SafeFuture.completedFuture(Map.of(key1, 1)));
    provider.lookupValidators();

    verify(validatorApiChannel).getValidatorIndices(any());

    provider.lookupValidators();

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldWaitForFirstSuccessfulRequestBeforeLookingUpValidatorIndices() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(List.of(key1), validatorApiChannel);

    final SafeFuture<Map<BLSPublicKey, Integer>> requestResult = new SafeFuture<>();
    when(validatorApiChannel.getValidatorIndices(List.of(key1))).thenReturn(requestResult);

    final SafeFuture<Collection<Integer>> result = provider.getValidatorIndices(List.of(key1));
    assertThat(result).isNotDone();

    provider.lookupValidators();
    assertThat(result).isNotDone();

    requestResult.complete(Map.of(key1, 1));
    assertThat(result).isCompletedWithValue(List.of(1));
  }
}
