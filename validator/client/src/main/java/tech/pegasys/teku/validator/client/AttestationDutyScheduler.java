package tech.pegasys.teku.validator.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.metrics.TekuMetricCategory;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

public class AttestationDutyScheduler extends AbstractDutyScheduler {
  private static final Logger LOG = LogManager.getLogger();
  private UInt64 lastAttestationCreationSlot;
  private final StableSubnetSubscriber stableSubnetSubscriber;
  public AttestationDutyScheduler(
                                  final MetricsSystem metricsSystem,
                                  final DutyLoader epochDutiesScheduler,
                                  final StableSubnetSubscriber stableSubnetSubscriber) {
    super(epochDutiesScheduler);

    this.stableSubnetSubscriber = stableSubnetSubscriber;
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_attestation_duties_current",
        "Current number of pending attestation duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(DutyQueue::countDuties).sum());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 epochNumber = compute_epoch_at_slot(slot);
    removePriorEpochs(epochNumber);
    dutiesByEpoch.computeIfAbsent(epochNumber, this::requestDutiesForEpoch);
    dutiesByEpoch.computeIfAbsent(epochNumber.plus(ONE), this::requestDutiesForEpoch);
    stableSubnetSubscriber.onSlot(slot);
  }

  @Override
  public void onChainReorg(final UInt64 newSlot) {
    LOG.debug("Chain reorganisation detected. Recalculating validator attestation duties");
    dutiesByEpoch.clear();
    final UInt64 epochNumber = compute_epoch_at_slot(newSlot);
    final UInt64 nextEpochNumber = epochNumber.plus(ONE);
    dutiesByEpoch.put(epochNumber, requestDutiesForEpoch(epochNumber));
    dutiesByEpoch.put(nextEpochNumber, requestDutiesForEpoch(nextEpochNumber));
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    // Check slot being null for the edge case of genesis slot (i.e. slot 0)
    if (lastAttestationCreationSlot != null && slot.compareTo(lastAttestationCreationSlot) <= 0) {
      return;
    }
    lastAttestationCreationSlot = slot;
    notifyDutyQueue(DutyQueue::onAttestationCreationDue, slot);
  }

  @Override
  public void onBlockImportedForSlot(final UInt64 slot) {
    // Create attestations for the current slot as soon as the block is imported.
    onAttestationCreationDue(slot);
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    notifyDutyQueue(DutyQueue::onAttestationAggregationDue, slot);
  }

}
