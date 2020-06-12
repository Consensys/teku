package tech.pegasys.teku.statetransition.operationpools;

import tech.pegasys.teku.core.operationvalidators.OperationStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class OperationPool<T> {

  private static Map<Class<?>, Integer> maxNumberOfElementsInBlock =
          Map.of(
                  SignedVoluntaryExit.class, Constants.MAX_VOLUNTARY_EXITS,
                  ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS,
                  AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS
          );

  private Set<T> operations = new HashSet<>();
  private OperationStateTransitionValidator<T> operationValidator;
  private Class<T> clazz;

  public OperationPool(Class<T> clazz,
                OperationStateTransitionValidator<T> operationValidator) {
    this.clazz = clazz;
    this.operationValidator = operationValidator;
  }

  public SSZList<T> getItemsForBlock(BeaconState stateAtBlockSlot) {
    SSZMutableList<T> itemsToPutInBlock = SSZList.createMutable(clazz, maxNumberOfElementsInBlock.get(clazz));
    Iterator<T> iter = operations.iterator();
    int count = 0;
    int numberOfElementsToGet = maxNumberOfElementsInBlock.get(iter.getClass());
    while (count < numberOfElementsToGet && iter.hasNext()) {
      T item = iter.next();
      if (operationValidator.validate(stateAtBlockSlot, item).isEmpty()) {
        itemsToPutInBlock.add(item);
        count++;
      }
      iter.remove();
    }
    return itemsToPutInBlock;
  }

  public void add(T item) {
    operations.add(item);
  }

  public void remove(T item) {
    operations.remove(item);
  }
}
