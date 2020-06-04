package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_previous_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.metrics.SettableGauge;
import tech.pegasys.teku.metrics.TekuMetricCategory;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class EpochMetrics implements SlotEventsChannel {

  private final SettableGauge previousLiveValidators;
  private final SettableGauge currentActiveValidators;
  private final SettableGauge previousActiveValidators;
  private final RecentChainData recentChainData;
  private final SettableGauge currentLiveValidators;

  public EpochMetrics(final MetricsSystem metricsSystem, final RecentChainData recentChainData) {
    previousLiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_live_validators",
            "Number of active validators that successfully included attestation on chain for previous epoch");
    currentLiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_live_validators",
            "Number of active validators that successfully included attestation on chain for previous epoch");
    currentActiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_active_validators",
            "Number of active validators");
    previousActiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_active_validators",
            "Number of active validators in the previous epoch");
    this.recentChainData = recentChainData;
  }

  @Override
  public void onSlot(final UnsignedLong slot) {
    recentChainData.getBestState().ifPresent(this::updateMetrics);
  }

  private void updateMetrics(final BeaconState state) {
    currentLiveValidators.set(getLiveValidators(state.getCurrent_epoch_attestations()));
    currentActiveValidators.set(
        get_active_validator_indices(state, get_current_epoch(state)).size());
    previousLiveValidators.set(getLiveValidators(state.getPrevious_epoch_attestations()));
    previousActiveValidators.set(
        get_active_validator_indices(state, get_previous_epoch(state)).size());
  }

  private int getLiveValidators(final SSZList<PendingAttestation> attestations) {
    final Map<UnsignedLong, Map<UnsignedLong, Bitlist>> aggregationBitsBySlotAndCommittee =
        new HashMap<>();
    attestations.forEach(
        attestation ->
            aggregationBitsBySlotAndCommittee
                .computeIfAbsent(attestation.getData().getSlot(), __ -> new HashMap<>())
                .computeIfAbsent(
                    attestation.getData().getIndex(),
                    __ -> attestation.getAggregation_bits().copy())
                .setAllBits(attestation.getAggregation_bits()));

    return aggregationBitsBySlotAndCommittee.values().stream()
        .flatMap(aggregationBitsByCommittee -> aggregationBitsByCommittee.values().stream())
        .mapToInt(Bitlist::getBitCount)
        .sum();
  }
}
