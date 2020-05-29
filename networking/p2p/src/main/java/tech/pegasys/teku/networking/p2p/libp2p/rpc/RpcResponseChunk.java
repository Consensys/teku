package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import io.netty.buffer.ByteBuf;

public class RpcResponseChunk {
  private final int respCode;
  private final ByteBuf content;

  public RpcResponseChunk(int respCode, ByteBuf content) {
    this.respCode = respCode;
    this.content = content;
  }

  public int getRespCode() {
    return respCode;
  }

  // WARNING: should be either consumed synchronously or retained for asynchronous use
  public ByteBuf getContent() {
    return content;
  }
}
