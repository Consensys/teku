package tech.pegasys.teku.datastructures.attestation;

public interface ProcessedAggregateConsumer {
  void accept(ValidateableAttestation attestation);
}
