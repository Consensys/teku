package tech.pegasys.artemis.networking.eth2.discovery;

public interface DiscoveryPeerSubscriber {
  void onDiscovery(DiscoveryPeer peer);
}
