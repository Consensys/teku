package tech.pegasys.teku.validator.coordinator.performance;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class NoOpPerformanceTracker implements PerformanceTracker {
  @Override
  public void saveSentAttestation(Attestation attestation) {}

  @Override
  public void saveSentBlock(SignedBeaconBlock block) {}

  @Override
  public void onSlot(UInt64 slot) {}
}
