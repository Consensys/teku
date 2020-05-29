package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import io.libp2p.etc.types.ByteBufExtKt;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import java.util.List;

public class RpcResponseChunkDecoder extends ByteToMessageDecoder {

  private static class HeaderDecoder extends ByteToMessageDecoder {
    private final int chunkNumber;

    public HeaderDecoder(int chunkNumber) {
      this.chunkNumber = chunkNumber;
    }

    public String getName() {
      return "RpcResponseHeaderDecoder-" + chunkNumber;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (!(msg instanceof ByteBuf)) {
        ctx.fireChannelRead(msg);
      } else {
        super.channelRead(ctx, msg);
      }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
        throws Exception {

      if (in.readableBytes() < 2) {
        // wait for more byte to read resp code and length fields
        return;
      }
      int rollbackIndex = in.readerIndex();

      byte respCode = in.readByte();
      long length = ByteBufExtKt.readUvarint(in);
      if (length < 0) {
        // wait for more byte to read length field
        in.readerIndex(rollbackIndex);
        return;
      }

      if (respCode == 0) {
        SnappyFrameDecoder snappyFrameDecoder = new SnappyFrameDecoder(true);
        RpcResponseChunkDecoder nextChunkHandler =
            new RpcResponseChunkDecoder(
                chunkNumber + 1, respCode, (int) length, snappyFrameDecoder);

        String snappyDecoderName = "SnappyFrameDecoder-" + (chunkNumber + 1);
        ctx.pipeline().addAfter(this.getName(), snappyDecoderName, snappyFrameDecoder);
        ctx.pipeline().addAfter(snappyDecoderName, nextChunkHandler.getName(), nextChunkHandler);
      } else {
        RpcResponseChunkDecoder nextChunkHandler =
            new RpcResponseChunkDecoder(chunkNumber + 1, respCode, (int) length, null);
        ctx.pipeline().addAfter(this.getName(), nextChunkHandler.getName(), nextChunkHandler);
      }
      ctx.pipeline().remove(this);
    }
  }

  public static ChannelInboundHandler create() {
    return new ChannelInboundHandlerAdapter() {
      @Override
      public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        HeaderDecoder headerDecoder = new HeaderDecoder(0);
        ctx.pipeline().replace(this, headerDecoder.getName(), headerDecoder);
      }
    };
  }

  private final int respCode;
  private final int chunkNumber;
  private final int rawLength;
  private final SnappyFrameDecoder snappyFrameDecoder;

  private RpcResponseChunkDecoder() {
    respCode = -1; // not used
    rawLength = -1; // not used
    snappyFrameDecoder = null;
    chunkNumber = 0;
  }

  private RpcResponseChunkDecoder(
      int chunkNumber, int respCode, int rawLength, SnappyFrameDecoder snappyFrameDecoder) {
    this.chunkNumber = chunkNumber;
    this.respCode = respCode;
    this.rawLength = rawLength;
    this.snappyFrameDecoder = snappyFrameDecoder;
  }

  public String getName() {
    return "RpcResponseChunkDecoder-" + chunkNumber;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    if (in.readableBytes() < rawLength) {
      // wait for complete chunk
      return;
    }
    RpcResponseChunk decodedChunk = new RpcResponseChunk(respCode, in.retainedSlice());
    out.add(decodedChunk);
    HeaderDecoder headerDecoder = new HeaderDecoder(chunkNumber + 1);
    ctx.pipeline().addAfter(getName(), headerDecoder.getName(), headerDecoder);
    ctx.pipeline().remove(this);
    if (snappyFrameDecoder != null) {
      ctx.pipeline().remove(snappyFrameDecoder);
    }
  }
}
