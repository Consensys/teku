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
import static tech.pegasys.teku.core.signatures.NoOpSigner.NO_OP_SIGNER;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class ValidatorIndexProviderTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  @Test
  void shouldReturnEmptyWhenValidatorIsUnknown() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidatorsWithKeys(key1), validatorApiChannel, asyncRunner);
    assertThat(provider.getValidatorIndex(key1)).isEmpty();
  }

  @Test
  void shouldLoadValidatorKeys() {
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final OwnedValidators validators = ownedValidatorsWithKeys(key1, key2, key3);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(validators.getPublicKeys()))
        .thenReturn(SafeFuture.completedFuture(Map.of(key1, 1, key2, 20, key3, 300)));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndex(key1)).contains(1);
    assertThat(provider.getValidatorIndex(key2)).contains(20);
    assertThat(provider.getValidatorIndex(key3)).contains(300);
  }

  @Test
  void shouldRetryWhenLoadRequestFails() {
    final OwnedValidators validators = ownedValidatorsWithKeys(key1);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(validators.getPublicKeys()))
        .thenReturn(SafeFuture.failedFuture(new IOException("Server not available")))
        .thenReturn(SafeFuture.completedFuture(Map.of(key1, 1)));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndex(key1)).isEmpty();

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();

    assertThat(provider.getValidatorIndex(key1)).contains(1);
  }

  @Test
  void shouldLookupValidatorKeysThatWerePreviouslyUnknown() {
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final OwnedValidators ownedValidators = ownedValidatorsWithKeys(key1, key2, key3);
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidators, validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(ownedValidators.getPublicKeys()))
        .thenReturn(SafeFuture.completedFuture(Map.of(key2, 20)));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndex(key1)).isEmpty();
    assertThat(provider.getValidatorIndex(key2)).contains(20);
    assertThat(provider.getValidatorIndex(key3)).isEmpty();

    when(validatorApiChannel.getValidatorIndices(Set.of(key1, key3)))
        .thenReturn(SafeFuture.completedFuture(Map.of(key1, 1, key3, 300)));
    provider.lookupValidators();

    assertThat(provider.getValidatorIndex(key1)).contains(1);
    assertThat(provider.getValidatorIndex(key2)).contains(20);
    assertThat(provider.getValidatorIndex(key3)).contains(300);
  }

  @Test
  void shouldNotMakeConcurrentRequests() {
    final SafeFuture<Map<BLSPublicKey, Integer>> result = new SafeFuture<>();
    when(validatorApiChannel.getValidatorIndices(Set.of(key1))).thenReturn(result);

    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidatorsWithKeys(key1), validatorApiChannel, asyncRunner);

    provider.lookupValidators();
    verify(validatorApiChannel).getValidatorIndices(Set.of(key1));

    provider.lookupValidators();
    verifyNoMoreInteractions(validatorApiChannel);

    // Can request again once the request completes.
    result.complete(Collections.emptyMap());
    provider.lookupValidators();
    verify(validatorApiChannel, times(2)).getValidatorIndices(Set.of(key1));
  }

  @Test
  void shouldNotMakeRequestWhenAllValidatorsAreKnown() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidatorsWithKeys(key1), validatorApiChannel, asyncRunner);

    when(validatorApiChannel.getValidatorIndices(Set.of(key1)))
        .thenReturn(SafeFuture.completedFuture(Map.of(key1, 1)));
    provider.lookupValidators();

    verify(validatorApiChannel).getValidatorIndices(any());

    provider.lookupValidators();

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldWaitForFirstSuccessfulRequestBeforeLookingUpValidatorIndices() {
    final ValidatorIndexProvider provider =
        new ValidatorIndexProvider(ownedValidatorsWithKeys(key1), validatorApiChannel, asyncRunner);

    final SafeFuture<Map<BLSPublicKey, Integer>> requestResult = new SafeFuture<>();
    when(validatorApiChannel.getValidatorIndices(Set.of(key1))).thenReturn(requestResult);

    final SafeFuture<Collection<Integer>> result = provider.getValidatorIndices(List.of(key1));
    assertThat(result).isNotDone();

    provider.lookupValidators();
    assertThat(result).isNotDone();

    requestResult.complete(Map.of(key1, 1));
    assertThat(result).isCompletedWithValue(List.of(1));
  }

  private OwnedValidators ownedValidatorsWithKeys(final BLSPublicKey... keys) {
    final Map<BLSPublicKey, Validator> validators = new HashMap<>();
    for (BLSPublicKey key : keys) {
      validators.put(key, new Validator(key, NO_OP_SIGNER, Optional::empty));
    }
    return new OwnedValidators(validators);
  }
}
