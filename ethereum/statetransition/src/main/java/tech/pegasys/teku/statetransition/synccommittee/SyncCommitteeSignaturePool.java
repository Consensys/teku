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

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SyncCommitteeSignaturePool {

  private final Subscribers<OperationAddedSubscriber<ValidateableSyncCommitteeSignature>>
      subscribers = Subscribers.create(true);

  private final SyncCommitteeSignatureValidator validator;

  public SyncCommitteeSignaturePool(final SyncCommitteeSignatureValidator validator) {
    this.validator = validator;
  }

  public void subscribeOperationAdded(
      OperationAddedSubscriber<ValidateableSyncCommitteeSignature> subscriber) {
    subscribers.subscribe(subscriber);
  }

  public SafeFuture<InternalValidationResult> add(
      final ValidateableSyncCommitteeSignature signature) {
    return validator
        .validate(signature)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                subscribers.forEach(subscriber -> subscriber.onOperationAdded(signature, result));
              }
            });
  }
}
