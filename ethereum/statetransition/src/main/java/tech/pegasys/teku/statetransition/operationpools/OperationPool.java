package tech.pegasys.teku.statetransition.operationpools;

import com.google.common.collect.Iterables;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.util.config.Constants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OperationPool<T> {

  private Set<T> operations = new HashSet<>();

  public List<T> getItemsForBlock() {
    List<T> itemsToPutInBlock = new ArrayList<>();
    Iterator<T> iter = operations.iterator();
    int count = 0;
    int numberOfElementsToGet = maxNumberOfElementsInBlock.get(iter.getClass());
    while (count < numberOfElementsToGet && iter.hasNext()) {
      T item = iter.next();
      itemsToPutInBlock.add(item);
      count++;
    }
    return itemsToPutInBlock;
  }

  public void add(T item) {
    operations.add(item);
  }

  void remove(T item) {
    operations.remove(item);
  }

  private static Map<Class<?>, Integer> maxNumberOfElementsInBlock =
          Map.of(
                  SignedVoluntaryExit.class, Constants.MAX_VOLUNTARY_EXITS,
                  ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS,
                  AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS
          );
}
