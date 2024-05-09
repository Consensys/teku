package tech.pegasys.teku.networking.eth2.peers;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.networking.p2p.peer.Peer;

public interface DiscoveryNodeIdExtractor {
  UInt256 calculateDiscoveryNodeId(Peer peer);
}
