package tech.pegasys.artemis.networking.p2p.peer;

import tech.pegasys.artemis.networking.p2p.peer.Peer;

@FunctionalInterface
public interface PeerConnectedSubscriber<T extends Peer> {

  void onConnected(T peer);

}
