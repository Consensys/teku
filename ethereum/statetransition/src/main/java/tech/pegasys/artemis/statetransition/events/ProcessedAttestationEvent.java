package tech.pegasys.artemis.statetransition.events;

import tech.pegasys.artemis.datastructures.operations.Attestation;

public class ProcessedAttestationEvent {

  private final Attestation attestation;

  public ProcessedAttestationEvent(Attestation attestation) {
    this.attestation = attestation;
  }

  public Attestation getAttestation() {
    return attestation;
  }
}
