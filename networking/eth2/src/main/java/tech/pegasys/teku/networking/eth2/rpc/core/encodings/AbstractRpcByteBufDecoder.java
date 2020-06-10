package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;

public abstract class AbstractRpcByteBufDecoder<TMessage>
    implements RpcByteBufDecoder<TMessage> {

  private final List<ByteBuf> incompleteFrames = new ArrayList<>();

  public synchronized Optional<TMessage> decodeOneMessage(ByteBuf in) {
    incompleteFrames.add(in.retainedSlice());
    ByteBuf inBuf = Unpooled.wrappedBuffer(incompleteFrames.toArray(new ByteBuf[0]));
    try {
      Optional<TMessage> outBuf;
      while (true) {
        int readerIndex = inBuf.readerIndex();
        outBuf = decodeOneImpl(inBuf);
        if (outBuf.isPresent()
            || readerIndex == inBuf.readerIndex()
            || inBuf.readableBytes() == 0) {
          break;
        }
      }
      if (outBuf.isPresent()) {
        incompleteFrames.forEach(ReferenceCounted::release);
        incompleteFrames.clear();
        in.skipBytes(in.readableBytes() - inBuf.readableBytes());
      } else {
        in.skipBytes(in.readableBytes());
      }
      return outBuf;
    } catch (Throwable t) {
      incompleteFrames.forEach(ReferenceCounted::release);
      throw t;
    }
  }

  @Override
  public void complete() {
    if (incompleteFrames.isEmpty()) {
      throw new PayloadSmallerThanExpectedException(
          "Rpc stream complete, but unprocessed data left: " + incompleteFrames);
    }
  }

  protected abstract Optional<TMessage> decodeOneImpl(ByteBuf in);
}
