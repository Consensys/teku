package tech.pegasys.teku.networking.eth2.gossip.topics;

import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;

public interface UpstreamAttestationPipe {
  void forward(ValidateableAttestation attestation);
}
