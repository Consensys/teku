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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.util.config.Constants;

public class OperationPool<T> {

  private static final Map<Class<?>, Integer> MAX_NUMBER_OF_ELEMENTS_IN_BLOCK =
      Map.of(
          SignedVoluntaryExit.class, Constants.MAX_VOLUNTARY_EXITS,
          ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS,
          AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS);

  private final Set<T> operations = LimitedSet.create(Constants.OPERATION_POOL_SIZE);
  private final Class<T> clazz;
  private final OperationValidator<T> operationValidator;
  private final Subscribers<OperationAddedSubscriber<T>> subscribers = Subscribers.create(true);

  public OperationPool(Class<T> clazz, OperationValidator<T> operationValidator) {
    this.clazz = clazz;
    this.operationValidator = operationValidator;
  }

  public void subscribeOperationAdded(OperationAddedSubscriber<T> subscriber) {
    this.subscribers.subscribe(subscriber);
  }

  public SSZList<T> getItemsForBlock(BeaconState stateAtBlockSlot) {
    SSZMutableList<T> itemsToPutInBlock =
        SSZList.createMutable(clazz, MAX_NUMBER_OF_ELEMENTS_IN_BLOCK.get(clazz));
    // Note that iterating through all items does not affect their access time so we are effectively
    // evicting the oldest entries when the size is exceeded as we only ever access via iteration.
    Iterator<T> iter = operations.iterator();
    int count = 0;
    int numberOfElementsToGet = MAX_NUMBER_OF_ELEMENTS_IN_BLOCK.get(clazz);
    while (count < numberOfElementsToGet && iter.hasNext()) {
      T item = iter.next();
      if (operationValidator.validateForStateTransition(stateAtBlockSlot, item)) {
        itemsToPutInBlock.add(item);
        count++;
      }
    }
    return itemsToPutInBlock;
  }

  public SafeFuture<InternalValidationResult> add(T item) {
    InternalValidationResult result = operationValidator.validateFully(item);
    if (result.equals(InternalValidationResult.ACCEPT)
        || result.equals(InternalValidationResult.SAVE_FOR_FUTURE)) {
      operations.add(item);
      subscribers.forEach(s -> s.onOperationAdded(item, result));
    }

    return SafeFuture.completedFuture(result);
  }

  public void addAll(SSZList<T> items) {
    operations.addAll(items.asList());
  }

  public void removeAll(SSZList<T> items) {
    operations.removeAll(items.asList());
  }

  public Set<T> getAll() {
    return Collections.unmodifiableSet(operations);
  }

  public interface OperationAddedSubscriber<T> {
    void onOperationAdded(T operation, InternalValidationResult validationStatus);
  }
}
