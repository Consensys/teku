package tech.pegasys.teku.networking.eth2.gossip.topics;

import tech.pegasys.teku.datastructures.attestation.ProcessedAttestationListener;

public interface ProcessedAttestationSubscriptionProvider {
  void subscribe(ProcessedAttestationListener processedAttestationListener);
}
