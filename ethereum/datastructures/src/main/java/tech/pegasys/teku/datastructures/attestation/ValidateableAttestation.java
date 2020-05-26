package tech.pegasys.teku.datastructures.attestation;

import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;

import java.util.Optional;

public class ValidateableAttestation {
  private final Attestation attestation;
  private Optional<SignedAggregateAndProof> maybeAggregate = Optional.empty();

  public static ValidateableAttestation fromSingle(Attestation attestation) {
    return new ValidateableAttestation(attestation);
  }

  public static ValidateableAttestation fromAggregate(SignedAggregateAndProof attestation) {
    return new ValidateableAttestation(attestation);
  }

  public ValidateableAttestation(Attestation attestation) {
    this.attestation = attestation;
  }

  private ValidateableAttestation(SignedAggregateAndProof attestation) {
    maybeAggregate = Optional.of(attestation);
    this.attestation = attestation.getMessage().getAggregate();
  }

  public boolean isAggregate() {
    return maybeAggregate.isPresent();
  }

  public Attestation getAttestation() {
    return attestation;
  }

  public SignedAggregateAndProof getSignedAggregateAndProof() {
    if (!isAggregate()) {
      throw new UnsupportedOperationException("ValidateableAttestation is not an aggregate.");
    }
    return maybeAggregate.get();
  }
}
