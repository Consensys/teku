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

package tech.pegasys.teku.statetransition;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.ssz.SszCollection;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class OperationPool<T extends SszData> {
  private static final int OPERATION_POOL_SIZE = 1000;
  private final Set<T> operations = LimitedSet.create(OPERATION_POOL_SIZE);
  private final Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier;
  private final OperationValidator<T> operationValidator;
  private final Subscribers<OperationAddedSubscriber<T>> subscribers = Subscribers.create(true);

  public OperationPool(
      Function<UInt64, SszListSchema<T, ?>> slotToSszListSchemaSupplier,
      OperationValidator<T> operationValidator) {
    this.slotToSszListSchemaSupplier = slotToSszListSchemaSupplier;
    this.operationValidator = operationValidator;
  }

  public void subscribeOperationAdded(OperationAddedSubscriber<T> subscriber) {
    this.subscribers.subscribe(subscriber);
  }

  public SszList<T> getItemsForBlock(BeaconState stateAtBlockSlot) {
    SszListSchema<T, ?> schema = slotToSszListSchemaSupplier.apply(stateAtBlockSlot.getSlot());
    // Note that iterating through all items does not affect their access time so we are effectively
    // evicting the oldest entries when the size is exceeded as we only ever access via iteration.
    return operations.stream()
        .limit(schema.getMaxLength())
        .filter(item -> operationValidator.validateForStateTransition(stateAtBlockSlot, item))
        .collect(schema.collector());
  }

  public SafeFuture<InternalValidationResult> add(T item) {
    InternalValidationResult result = operationValidator.validateFully(item);
    if (result.code().equals(ValidationResultCode.ACCEPT)
        || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
      operations.add(item);
      subscribers.forEach(s -> s.onOperationAdded(item, result));
    }

    return SafeFuture.completedFuture(result);
  }

  public void addAll(SszCollection<T> items) {
    operations.addAll(items.asList());
  }

  public void removeAll(SszCollection<T> items) {
    operations.removeAll(items.asList());
  }

  public Set<T> getAll() {
    return Collections.unmodifiableSet(operations);
  }

  public interface OperationAddedSubscriber<T> {
    void onOperationAdded(T operation, InternalValidationResult validationStatus);
  }
}
