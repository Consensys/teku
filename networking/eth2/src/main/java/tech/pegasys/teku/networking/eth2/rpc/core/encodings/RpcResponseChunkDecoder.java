/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static java.lang.Integer.min;

import io.libp2p.etc.types.ByteBufExtKt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.compression.SnappyFrameDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Netty decoder which decodes ssz_snappy or raw ssz Eth2 RPC response chunks from inbound response
 * stream
 */
public class RpcResponseChunkDecoder extends SnappyFrameDecoder {

  private final boolean compressed;
  private int respCode;
  private long remainingRawLength = 0;
  private boolean decodePayload = false;
  private final List<ByteBuf> rawDataFrames = new ArrayList<>();

  /**
   * Create a new decoder
   *
   * @param compressed {@code true} if the stream is Snappy compressed, {@code false} if the stream
   *     is plain ssz
   */
  public RpcResponseChunkDecoder(boolean compressed) {
    super(true);
    this.compressed = compressed;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    if (!decodePayload) {
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
      this.respCode = respCode;
      this.remainingRawLength = length;
      decodePayload = true;
    } else {
      ArrayList<Object> rawOut = new ArrayList<>();
      if (compressed) {
        super.decode(ctx, in, rawOut);
      } else {
        rawOut.add(in.readSlice(min(in.readableBytes(), (int) remainingRawLength)).retain());
      }

      if (remainingRawLength == 0) {
        // special case for chunk with 0 length
        rawOut.add(Unpooled.EMPTY_BUFFER);
      }

      if (!rawOut.isEmpty()) {
        ByteBuf rawBuf = (ByteBuf) rawOut.get(0);
        rawDataFrames.add(rawBuf);
        remainingRawLength -= rawBuf.readableBytes();
        if (remainingRawLength < 0) {
          throw new RuntimeException("Invalid stream");
        }
        if (remainingRawLength == 0) {
          ByteBuf rawChunkPayload = Unpooled.wrappedBuffer(rawDataFrames.toArray(new ByteBuf[0]));
          rawDataFrames.clear();
          out.add(new RpcResponseChunk(respCode, rawChunkPayload));
          decodePayload = false;
        }
      }
    }
  }
}
