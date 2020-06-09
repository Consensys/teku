package tech.pegasys.teku.networking.p2p.libp2p;

import tech.pegasys.teku.networking.p2p.peer.Peer;

/**
 * Indicates that two connections to the same PeerID were incorrectly established.
 *
 * <p>LibP2P usually detects attempts to establish multiple connections at the same time, but if
 * we have incoming and outgoing connections simultaneously to the same peer, sometimes it slips
 * through. In that case this exception is thrown so that the new connection is terminated before
 * handshakes complete and we are able to identify the situation and return the existing peer.
 */
public class PeerAlreadyConnectedException extends RuntimeException {
  private final Peer peer;

  public PeerAlreadyConnectedException(final Peer peer) {
    super("Already connected to peer " + peer.getId().toBase58());
    this.peer = peer;
  }

  public Peer getPeer() {
    return peer;
  }
}

