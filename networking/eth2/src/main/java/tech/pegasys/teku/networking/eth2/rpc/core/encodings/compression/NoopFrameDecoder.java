package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.AbstractRpcByteBufDecoder;

public class NoopFrameDecoder extends AbstractRpcByteBufDecoder<ByteBuf> {
  private final int expectedBytes;

  public NoopFrameDecoder(int expectedBytes) {
    this.expectedBytes = expectedBytes;
  }

  @Override
  protected Optional<ByteBuf> decodeOneImpl(ByteBuf in) {
    if (in.readableBytes() < expectedBytes) {
      return Optional.empty();
    }
    return Optional.of(in.readRetainedSlice(expectedBytes));
  }

  public int getExpectedBytes() {
    return expectedBytes;
  }
}
