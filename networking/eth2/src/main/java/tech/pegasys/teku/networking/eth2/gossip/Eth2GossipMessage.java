package tech.pegasys.teku.networking.eth2.gossip;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier;
import tech.pegasys.teku.networking.p2p.gossip.GossipMessage;

public class Eth2GossipMessage implements GossipMessage {
  private static final Logger LOG = LogManager.getLogger();

  // 4-byte domain for gossip message-id isolation of *invalid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_INVALID_SNAPPY = Bytes.fromHexString("0x00000000");
  // 4-byte domain for gossip message-id isolation of *valid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_VALID_SNAPPY = Bytes.fromHexString("0x01000000");

  private final String topic;
  private final Bytes payload;
  private final ExceptionThrowingSupplier<Bytes> decompressedSupplier;
  private final Supplier<Bytes> messageId = Suppliers.memoize(this::calcMessageId);

  public Eth2GossipMessage(String topic, Bytes payload, ExceptionThrowingSupplier<Bytes> decompressedSupplier) {
    this.topic = topic;
    this.payload = payload;
    this.decompressedSupplier = decompressedSupplier;
  }

  @Override
  public String getTopic() {
    return topic;
  }

  @Override
  public Bytes getPayload() {
    return payload;
  }

  public Optional<Bytes> getDecompressedPayload() {
    try {
      return Optional.of(decompressedSupplier.get());
    } catch (Throwable e) {
      LOG.debug("Failed to decompress inbound gossip message: ", e);
      return Optional.empty();
    }
  }

  private Bytes calcMessageId() {
    return Hash.sha2_256(
        getDecompressedPayload()
            .map(
                validSnappyUncompressed ->
                    Bytes.wrap(MESSAGE_DOMAIN_VALID_SNAPPY, validSnappyUncompressed))
            .orElse(Bytes.wrap(MESSAGE_DOMAIN_INVALID_SNAPPY, getPayload()))
            .slice(0, 20));
  }

  @Override
  public Bytes getMessageId() {
    return messageId.get();
  }
}
