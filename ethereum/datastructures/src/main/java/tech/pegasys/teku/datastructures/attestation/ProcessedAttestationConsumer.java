package tech.pegasys.teku.datastructures.attestation;

public interface ProcessedAttestationConsumer {
  void accept(ValidateableAttestation attestation);
}
