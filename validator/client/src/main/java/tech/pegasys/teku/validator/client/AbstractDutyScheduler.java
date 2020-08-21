package tech.pegasys.teku.validator.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

public abstract class AbstractDutyScheduler  implements ValidatorTimingChannel {
  private final DutyLoader epochDutiesScheduler;

  protected final NavigableMap<UInt64, DutyQueue> dutiesByEpoch = new TreeMap<>();

  protected AbstractDutyScheduler(final DutyLoader epochDutiesScheduler) {
    this.epochDutiesScheduler = epochDutiesScheduler;
  }

  protected DutyQueue requestDutiesForEpoch(final UInt64 epochNumber) {
    return new DutyQueue(epochDutiesScheduler.loadDutiesForEpoch(epochNumber));
  }

  protected void notifyDutyQueue(final BiConsumer<DutyQueue, UInt64> action, final UInt64 slot) {
    final DutyQueue dutyQueue = dutiesByEpoch.get(compute_epoch_at_slot(slot));
    if (dutyQueue != null) {
      action.accept(dutyQueue, slot);
    }
  }

  protected void removePriorEpochs(final UInt64 epochNumber) {
    final NavigableMap<UInt64, DutyQueue> toRemove = dutiesByEpoch.headMap(epochNumber, false);
    removeEpochs(toRemove);
  }

  protected void removeEpochs(final NavigableMap<UInt64, DutyQueue> toRemove) {
    toRemove.values().forEach(DutyQueue::cancel);
    toRemove.clear();
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {

  }

  @Override
  public void onBlockImportedForSlot(final UInt64 slot) {

  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {

  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {

  }

}
