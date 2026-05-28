/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import io.libp2p.core.P2PChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

class LibP2PRpcStreamTest {

  private final NodeId nodeId = mock(NodeId.class);
  private final P2PChannel p2pChannel = mock(P2PChannel.class);
  private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
  private final LibP2PRpcStream stream = new LibP2PRpcStream(nodeId, p2pChannel, ctx);

  @Test
  void shouldReleaseBufferWhenWriteFailsAfterRetainingBuffer() {
    final EmbeddedChannel channel = new EmbeddedChannel();
    final DefaultChannelPromise writePromise = new DefaultChannelPromise(channel);
    final AtomicReference<ByteBuf> writtenBuffer = new AtomicReference<>();

    when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
    when(ctx.writeAndFlush(any(ByteBuf.class)))
        .thenAnswer(
            invocation -> {
              final ByteBuf byteBuf = invocation.getArgument(0);
              writtenBuffer.set(byteBuf);

              byteBuf.retain();
              byteBuf.release();
              writePromise.setFailure(new RuntimeException("write rejected"));
              return writePromise;
            });

    assertThatSafeFuture(stream.writeBytes(Bytes.of(1, 2, 3))).isCompletedExceptionally();
    assertThat(writtenBuffer.get().refCnt()).isZero();
  }

  @Test
  void shouldReleaseBufferWhenWriteThrows() {
    final AtomicReference<ByteBuf> writtenBuffer = new AtomicReference<>();

    when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
    when(ctx.writeAndFlush(any(ByteBuf.class)))
        .thenAnswer(
            invocation -> {
              final ByteBuf byteBuf = invocation.getArgument(0);
              writtenBuffer.set(byteBuf);
              throw new RuntimeException("write rejected");
            });

    assertThatThrownBy(() -> stream.writeBytes(Bytes.of(1, 2, 3)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("write rejected");
    assertThat(writtenBuffer.get().refCnt()).isZero();
  }
}
