package tech.pegasys.teku.networking.eth2.gossip;

import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.util.LengthBounds;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.networking.eth2.gossip.encoding.DecodingException;
import tech.pegasys.teku.networking.eth2.gossip.encoding.SnappyBlockCompressor;
import tech.pegasys.teku.networking.p2p.gossip.GossipMessage;
import tech.pegasys.teku.networking.p2p.gossip.GossipMessageFactory;

public class Eth2GossipMessageFactory implements GossipMessageFactory {
  private final Function<String, LengthBounds> topicLengthBounds;
  private final SnappyBlockCompressor snappyCompressor;

  public Eth2GossipMessageFactory(
      Function<String, LengthBounds> topicLengthBounds, SnappyBlockCompressor snappyCompressor) {
    this.topicLengthBounds = topicLengthBounds;
    this.snappyCompressor = snappyCompressor;
  }

  @Override
  public GossipMessage createMessage(String topic, Bytes payload) {
    LengthBounds lengthBounds = topicLengthBounds.apply(topic);
    final ExceptionThrowingSupplier<Bytes> decompressedSupplier;
    if (lengthBounds == null) {
      decompressedSupplier =
          () -> {
            throw new DecodingException("Unknown topic: " + topic);
          };
    } else {
      decompressedSupplier = () -> snappyCompressor.uncompress(payload, lengthBounds);
    }
    return new Eth2GossipMessage(topic, payload, decompressedSupplier);
  }
}
