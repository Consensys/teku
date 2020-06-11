package tech.pegasys.teku.statetransition.operationpools;

import com.google.common.collect.Iterables;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class OperationPools  {

  private Map<Class<?>, OperationPool<?>> operationPools = Map.of(
          SignedVoluntaryExit.class, new OperationPool<>(SignedVoluntaryExit.class),
          ProposerSlashing.class, new OperationPool<>(ProposerSlashing.class),
          AttesterSlashing.class, new OperationPool<>(AttesterSlashing.class)
  );

  private static Map<Class<?>, Integer> maxNumberOfElementsInBlock =
          Map.of(
                  SignedVoluntaryExit.class, Constants.MAX_VOLUNTARY_EXITS,
                  ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS,
                  AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS
          );

  @SuppressWarnings("unchecked")
  public <T> OperationPool<T> getPool(Class<T> clazz) {
    return (OperationPool<T>) operationPools.get(clazz);
  }

  public static class OperationPool<T> {

    private Set<T> operations = new HashSet<>();
    private Class<T> clazz;

    OperationPool(Class<T> clazz) {
      this.clazz = clazz;
    }

    public SSZList<T> getItemsForBlock() {
      SSZMutableList<T> itemsToPutInBlock = SSZList.createMutable(clazz, maxNumberOfElementsInBlock.get(clazz));
      Iterator<T> iter = operations.iterator();
      int count = 0;
      int numberOfElementsToGet = maxNumberOfElementsInBlock.get(iter.getClass());
      while (count < numberOfElementsToGet && iter.hasNext()) {
        T item = iter.next();
        itemsToPutInBlock.add(item);
        iter.remove();
        count++;
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
}
