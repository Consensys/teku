/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.synccommittee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.REJECT;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;

class SyncCommitteeSignaturePoolTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());
  private final SyncCommitteeSignatureValidator validator =
      mock(SyncCommitteeSignatureValidator.class);

  private final ValidateableSyncCommitteeSignature signature =
      ValidateableSyncCommitteeSignature.fromValidator(
          dataStructureUtil.randomSyncCommitteeSignature());

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<ValidateableSyncCommitteeSignature> subscriber =
      mock(OperationAddedSubscriber.class);

  private final SyncCommitteeSignaturePool pool = new SyncCommitteeSignaturePool(validator);

  @Test
  void shouldNotifySubscriberWhenValidSignatureAdded() {
    final ValidateableSyncCommitteeSignature signature = this.signature;
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(signature)).thenReturn(SafeFuture.completedFuture(ACCEPT));

    assertThat(pool.add(signature)).isCompletedWithValue(ACCEPT);
    verify(subscriber).onOperationAdded(signature, ACCEPT);
  }

  @Test
  void shouldNotNotifySubscriberWhenInvalidSignatureAdded() {
    final ValidateableSyncCommitteeSignature signature = this.signature;
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(signature)).thenReturn(SafeFuture.completedFuture(REJECT));

    assertThat(pool.add(signature)).isCompletedWithValue(REJECT);
    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldNotNotifySubscriberWhenIgnoredSignatureAdded() {
    final ValidateableSyncCommitteeSignature signature = this.signature;
    pool.subscribeOperationAdded(subscriber);
    when(validator.validate(signature)).thenReturn(SafeFuture.completedFuture(IGNORE));

    assertThat(pool.add(signature)).isCompletedWithValue(IGNORE);
    verifyNoInteractions(subscriber);
  }
}
