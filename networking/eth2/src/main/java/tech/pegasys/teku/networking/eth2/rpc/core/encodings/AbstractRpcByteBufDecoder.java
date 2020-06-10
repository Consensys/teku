package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;

public abstract class AbstractRpcByteBufDecoder<TMessage>
    implements RpcByteBufDecoder<TMessage> {

  private CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer();


  public synchronized Optional<TMessage> decodeOneMessage(ByteBuf in) {
    if (!in.isReadable()) {
      return Optional.empty();
    }
    compositeByteBuf.addComponent(true, in.retainedSlice());
    try {
      Optional<TMessage> outBuf;
      while (true) {
        int readerIndex = compositeByteBuf.readerIndex();
        outBuf = decodeOneImpl(compositeByteBuf);
        if (outBuf.isPresent()
            || readerIndex == compositeByteBuf.readerIndex()
            || compositeByteBuf.readableBytes() == 0) {
          break;
        }
      }
      if (outBuf.isPresent()) {
        in.skipBytes(in.readableBytes() - compositeByteBuf.readableBytes());
        compositeByteBuf.release();
        compositeByteBuf = Unpooled.compositeBuffer();
      } else {
        in.skipBytes(in.readableBytes());
      }
      return outBuf;
    } catch (Throwable t) {
      compositeByteBuf.release();
      compositeByteBuf = Unpooled.compositeBuffer();
      throw t;
    }
  }

  @Override
  public void complete() {
    if (compositeByteBuf.isReadable()) {
      throw new PayloadSmallerThanExpectedException(
          "Rpc stream complete, but unprocessed data left: " + compositeByteBuf.readableBytes());
    }
  }

  protected abstract Optional<TMessage> decodeOneImpl(ByteBuf in);
}
