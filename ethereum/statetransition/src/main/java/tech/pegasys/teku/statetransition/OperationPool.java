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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import tech.pegasys.teku.core.operationvalidators.OperationStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.collections.LimitStrategy;
import tech.pegasys.teku.util.collections.LimitedSet;
import tech.pegasys.teku.util.config.Constants;

public class OperationPool<T> {

  private static Map<Class<?>, Integer> maxNumberOfElementsInBlock =
      Map.of(
          SignedVoluntaryExit.class, Constants.MAX_VOLUNTARY_EXITS,
          ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS,
          AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS);

  private final Set<T> operations =
      LimitedSet.create(Constants.OPERATION_POOL_SIZE, LimitStrategy.DROP_OLDEST_ELEMENT);
  private final OperationStateTransitionValidator<T> operationValidator;
  private final Class<T> clazz;

  public OperationPool(Class<T> clazz, OperationStateTransitionValidator<T> operationValidator) {
    this.clazz = clazz;
    this.operationValidator = operationValidator;
  }

  public SSZList<T> getItemsForBlock(BeaconState stateAtBlockSlot) {
    SSZMutableList<T> itemsToPutInBlock =
        SSZList.createMutable(clazz, maxNumberOfElementsInBlock.get(clazz));
    Iterator<T> iter = operations.iterator();
    int count = 0;
    int numberOfElementsToGet = maxNumberOfElementsInBlock.get(clazz);
    while (count < numberOfElementsToGet && iter.hasNext()) {
      T item = iter.next();
      if (operationValidator.validate(stateAtBlockSlot, item).isEmpty()) {
        itemsToPutInBlock.add(item);
        count++;
      }
    }
    return itemsToPutInBlock;
  }

  public void add(T item) {
    operations.add(item);
  }

  public void removeAll(SSZList<T> items) {
    operations.removeAll(items.asList());
  }
}
