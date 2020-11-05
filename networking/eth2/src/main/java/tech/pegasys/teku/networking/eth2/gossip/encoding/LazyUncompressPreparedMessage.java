package tech.pegasys.teku.networking.eth2.gossip.encoding;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.teku.datastructures.util.LengthBounds;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.p2p.gossip.PreparedMessage;

class LazyUncompressPreparedMessage implements PreparedMessage {
  // 4-byte domain for gossip message-id isolation of *invalid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_INVALID_SNAPPY = Bytes.fromHexString("0x00000000");
  // 4-byte domain for gossip message-id isolation of *valid* snappy messages
  public static final Bytes MESSAGE_DOMAIN_VALID_SNAPPY = Bytes.fromHexString("0x01000000");

  private final Bytes compressedData;
  private final Class<?> valueType;
  private final SnappyBlockCompressor snappyCompressor;
  private final Supplier<Optional<Bytes>> uncompressed = Suppliers
      .memoize(this::maybeUncompressPayload);
  private DecodingException uncompressException;

  public LazyUncompressPreparedMessage(Bytes compressedData, Class<?> valueType,
      SnappyBlockCompressor snappyCompressor) {
    this.compressedData = compressedData;
    this.valueType = valueType;
    this.snappyCompressor = snappyCompressor;
  }

  public Bytes getUncompressedOrThrow() throws DecodingException {
    Optional<Bytes> maybeUncompressed = getMaybeUncompressed();
    if (maybeUncompressed.isPresent()) {
      return maybeUncompressed.get();
    } else {
      throw new DecodingException("Couldn't uncompress the message", uncompressException);
    }
  }

  public Optional<Bytes> getMaybeUncompressed() {
    return uncompressed.get();
  }

  private Optional<Bytes> maybeUncompressPayload() {
    try {
      return Optional.of(uncompressPayload());
    } catch (DecodingException e) {
      uncompressException = e;
      return Optional.empty();
    }
  }

  private Bytes uncompressPayload() throws DecodingException {
    final LengthBounds lengthBounds =
        SimpleOffsetSerializer.getLengthBounds(valueType)
            .orElseThrow(() -> new DecodingException("Unknown message type: " + valueType));
    return snappyCompressor.uncompress(compressedData, lengthBounds);
  }

  @Override
  public Bytes getMessageId() {
    return Hash.sha2_256(
        getMaybeUncompressed()
            .map(
                validSnappyUncompressed ->
                    Bytes.wrap(MESSAGE_DOMAIN_VALID_SNAPPY, validSnappyUncompressed))
            .orElse(
                Bytes.wrap(MESSAGE_DOMAIN_INVALID_SNAPPY, compressedData))
            .slice(0, 20));
  }
}
