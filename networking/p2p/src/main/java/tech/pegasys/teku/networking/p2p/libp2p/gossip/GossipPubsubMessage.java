package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import io.libp2p.etc.types.WBytes;
import io.libp2p.pubsub.PubsubMessage;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.Message;
import tech.pegasys.teku.networking.p2p.gossip.GossipMessage;

public class GossipPubsubMessage implements PubsubMessage {
  // 4-byte domain for gossip message-id isolation of *invalid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_INVALID_SNAPPY = Bytes.fromHexString("0x00000000");
  // 4-byte domain for gossip message-id isolation of *valid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_VALID_SNAPPY = Bytes.fromHexString("0x01000000");

  private final Message originalMessage;
  private final Supplier<Bytes> fastId = Suppliers.memoize(this::calcFastId);
  private final Supplier<WBytes> canonicalId =
      Suppliers.memoize(() -> new WBytes(calcCanonicalId().toArrayUnsafe()));
  private final Supplier<Optional<Bytes>> uncompressedData = Suppliers.memoize(this::uncompress);
  private final GossipMessage gossipMessage;

  public GossipPubsubMessage(Message originalMessage,
      GossipMessage gossipMessage) {
    this.originalMessage = originalMessage;
    this.gossipMessage = gossipMessage;
  }

  public Object getFastMessageId() {
    return fastId.get();
  }

  private Bytes calcFastId() {
    return Bytes.wrap(Hash.sha2_256(getProtobufMessage().getData().toByteArray()));
  }

  private Bytes calcCanonicalId() {
    return Hash.sha2_256(
        uncompressedData
            .get()
            .map(
                validSnappyUncompressed ->
                    Bytes.wrap(MESSAGE_DOMAIN_VALID_SNAPPY, validSnappyUncompressed))
            .orElse(
                Bytes.wrap(
                    MESSAGE_DOMAIN_INVALID_SNAPPY,
                    Bytes.wrap(getProtobufMessage().getData().toByteArray())))
            .slice(0, 20));
  }

  private Optional<Bytes> uncompress() {
    throw new UnsupportedOperationException();
  }

  public Bytes getUncompressedData() {
    return uncompressedData.get().orElseThrow();
  }

  @NotNull
  @Override
  public WBytes getMessageId() {
    return canonicalId.get();
  }

  @NotNull
  @Override
  public Message getProtobufMessage() {
    return originalMessage;
  }

  public GossipMessage getGossipMessage() {
    return gossipMessage;
  }
}
