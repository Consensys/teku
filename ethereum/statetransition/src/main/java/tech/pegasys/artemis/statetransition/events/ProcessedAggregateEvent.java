package tech.pegasys.artemis.statetransition.events;

import tech.pegasys.artemis.datastructures.operations.Attestation;

public class ProcessedAggregateEvent {

  private final Attestation attestation;

  public ProcessedAggregateEvent(Attestation attestation) {
    this.attestation = attestation;
  }

  public Attestation getAttestation() {
    return attestation;
  }
}
