package tech.pegasys.artemis.statetransition.events;

import tech.pegasys.artemis.datastructures.operations.Attestation;

import java.util.List;

public class ProcessedBlockEvent {
  private final List<Attestation> attestationList;

  public ProcessedBlockEvent(List<Attestation> attestationList) {
    this.attestationList = attestationList;
  }

  public List<Attestation> getAttestationList() {
    return attestationList;
  }
}
