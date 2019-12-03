package tech.pegasys.artemis.networking.eth2.peers;

import tech.pegasys.artemis.networking.p2p.peer.Peer;

@FunctionalInterface
public interface PeerConnectedSubscriber {

  void onConnected(Eth2Peer peer);

}
