package tech.pegasys.teku.networking.p2p.gossip;

import org.apache.tuweni.bytes.Bytes;

public interface GossipMessageFactory {

  GossipMessage createMessage(String topic, Bytes payload);
}
