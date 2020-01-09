package tech.pegasys.artemis.networking.eth2.discovery;

import java.util.stream.Stream;

public interface DiscoveryNetwork {

  void findPeers();
  void subscribePeerDiscovered(DiscoveryPeerSubscriber subscriber);
  Stream<DiscoveryPeer> streamPeers();

}
