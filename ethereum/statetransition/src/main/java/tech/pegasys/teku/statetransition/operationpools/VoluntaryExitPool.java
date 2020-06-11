package tech.pegasys.teku.statetransition.operationpools;

import tech.pegasys.teku.datastructures.state.BeaconState;

import java.util.List;

public class VoluntaryExitPool implements OperationPool<VoluntaryExitPool> {

  @Override
  public List<VoluntaryExitPool> getItemsForBlock(BeaconState state) {
    return null;
  }

  @Override
  public void add(VoluntaryExitPool item) {

  }

  @Override
  public void remove(VoluntaryExitPool item) {

  }
}
