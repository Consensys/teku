package tech.pegasys.teku.networking.p2p.gossip;

import org.apache.tuweni.bytes.Bytes;

public interface GossipMessage {

  String getTopic();

  Bytes getPayload();

  Bytes getMessageId();
}
