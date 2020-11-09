package tech.pegasys.teku.networking.p2p.gossip;

import org.apache.tuweni.bytes.Bytes;

public interface PreparedMessageFactory {

  PreparedMessage create(String topic, Bytes payload);
}
