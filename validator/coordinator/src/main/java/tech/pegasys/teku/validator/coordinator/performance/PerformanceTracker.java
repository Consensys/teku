package tech.pegasys.teku.validator.coordinator.performance;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public interface PerformanceTracker extends SlotEventsChannel {

  void saveSentAttestation(Attestation attestation);

  void saveSentBlock(SignedBeaconBlock block);
}
