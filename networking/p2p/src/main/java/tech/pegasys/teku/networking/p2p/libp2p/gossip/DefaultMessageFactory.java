package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.networking.p2p.gossip.GossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.GossipMessageFactory;

public class DefaultMessageFactory implements GossipMessageFactory {

  public static class DefaultGossipMessage implements GossipMessage {
    private final String topic;
    private final Bytes payload;

    public DefaultGossipMessage(String topic, Bytes payload) {
      this.topic = topic;
      this.payload = payload;
    }

    @Override
    public String getTopic() {
      return topic;
    }

    @Override
    public Bytes getPayload() {
      return payload;
    }

    @Override
    public Bytes getMessageId() {
      return Hash.sha2_256(getPayload()).slice(0, 20);
    }
  }

  @Override
  public GossipMessage createMessage(String topic, Bytes payload) {
    return new DefaultGossipMessage(topic, payload);
  }
}
