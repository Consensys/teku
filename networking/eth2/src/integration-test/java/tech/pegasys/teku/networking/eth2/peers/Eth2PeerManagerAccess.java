package tech.pegasys.teku.networking.eth2.peers;

public class Eth2PeerManagerAccess {
  public static void invokeSendPeriodicPing(Eth2PeerManager peerManager, Eth2Peer peer) {
    peerManager.sendPeriodicPing(peer);
  }
}
