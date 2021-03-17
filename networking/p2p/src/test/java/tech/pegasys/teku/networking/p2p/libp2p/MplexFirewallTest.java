/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.libp2p.core.Connection;
import io.libp2p.core.transport.Transport;
import io.libp2p.etc.util.netty.mux.MuxId;
import io.libp2p.mux.MuxFrame;
import io.libp2p.mux.MuxFrame.Flag;
import io.libp2p.transport.implementation.ConnectionOverNetty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MplexFirewallTest {
  private static final ChannelId dummyChannelId = DefaultChannelId.newInstance();

  private final AtomicLong time = new AtomicLong();
  private final MplexFirewall firewall = new MplexFirewall(10, 20, time::get);
  private final ByteBuf data1K = Unpooled.buffer().writeBytes(new byte[1024]);
  private final EmbeddedChannel channel = new EmbeddedChannel();
  private final Connection connectionOverNetty =
      new ConnectionOverNetty(channel, mock(Transport.class), true);
  private final List<String> passedMessages = new ArrayList<>();

  @BeforeEach
  void init() {
    firewall.visit(connectionOverNetty);
    channel
        .pipeline()
        .addLast(
            new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                passedMessages.add(msg.toString());
              }
            });
  }

  private void writeOneInbound(MuxFrame message) {
    try {
      boolean res = channel.writeOneInbound(message).await(1000L);
      assertThat(res).isTrue();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testThatDisconnectsOnRateLimitExceed() {
    for (int i = 0; i < 20; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.CLOSE, Unpooled.EMPTY_BUFFER));
      time.incrementAndGet();
    }
    assertThat(channel.isOpen()).isFalse();
    // hasSizeBetween: giving the Firewall some flexibility to pass 1 extra OPEN frame before
    // closing connection
    assertThat(passedMessages).hasSizeBetween(20, 22).doesNotHaveDuplicates();
  }

  @Test
  void testThatDoesntDisconnectOnAllowedRate() {
    for (int i = 0; i < 500; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.CLOSE, Unpooled.EMPTY_BUFFER));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(1000).doesNotHaveDuplicates();
  }

  @Test
  void testThatDisconnectsOnExceededAfterAllowedRate() {
    for (int i = 0; i < 100; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.CLOSE, Unpooled.EMPTY_BUFFER));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200).doesNotHaveDuplicates();

    for (int i = 100; i < 200; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.CLOSE, Unpooled.EMPTY_BUFFER));
      time.addAndGet(80); // ~12 per sec
    }
    assertThat(channel.isOpen()).isFalse();
    // 220 + 2: giving the Firewall some flexibility to pass 1 extra OPEN frame before closing
    // connection
    assertThat(passedMessages).hasSizeLessThan(220 + 2).doesNotHaveDuplicates();
  }

  @Test
  void testThatDoesntDisconnectOnHighDataRate() {
    for (int i = 0; i < 500; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      for (int j = 0; j < 30; j++) {
        writeOneInbound(
            new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.DATA, data1K.slice()));
      }
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.CLOSE, Unpooled.EMPTY_BUFFER));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(500 * (2 + 30));
  }

  @Test
  void testThatDisconnectsOnExceedingParallelStreams() {
    // opening 30 streams on normal open rate
    for (int i = 0; i < 30; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      time.addAndGet(112); // ~9 per sec
    }
    assertThat(channel.isOpen()).isFalse();
    // 20 + 1: giving the Firewall some flexibility to pass 1 extra OPEN frame before closing
    // connection
    assertThat(passedMessages).hasSizeLessThan(20 + 1).doesNotHaveDuplicates();
  }

  @Test
  void testThatDoesntDisconnectOnAllowedParallelStreams() {
    List<Integer> openedIds = new ArrayList<>();
    for (int i = 0; i < 18; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      openedIds.add(i);
      time.addAndGet(112); // ~9 per sec
    }
    for (int i = 18; i < 200; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));
      openedIds.add(i);

      Collections.shuffle(openedIds);
      Integer toRemove = openedIds.remove(0);

      writeOneInbound(
          new MuxFrame(
              new MuxId(dummyChannelId, toRemove, true), Flag.CLOSE, Unpooled.EMPTY_BUFFER));
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200 * 2 - 18).doesNotHaveDuplicates();
  }

  @Test
  void testThatResetStreamFromLocalIsTracked() throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));

      channel
          .writeAndFlush(
              new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.RESET, Unpooled.EMPTY_BUFFER))
          .await(1000);
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200).doesNotHaveDuplicates();
    assertThat(channel.outboundMessages().stream().map(Object::toString))
        .hasSize(200)
        .doesNotHaveDuplicates();
  }

  @Test
  void testThatResetStreamFromRemoteIsTracked() {
    for (int i = 0; i < 200; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));

      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.RESET, Unpooled.EMPTY_BUFFER));
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isTrue();
    assertThat(passedMessages).hasSize(200 * 2).doesNotHaveDuplicates();
  }

  @Test
  void testThatDisconnectsOnLocalClose() throws InterruptedException {
    // CLOSE (unlike RESET) sent from local doesn't close the stream and still allows remote to
    // write
    // so it shouldn't affect the number of opened tracked streams
    for (int i = 0; i < 25; i++) {
      writeOneInbound(
          new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.OPEN, Unpooled.EMPTY_BUFFER));

      channel
          .writeAndFlush(
              new MuxFrame(new MuxId(dummyChannelId, i, true), Flag.CLOSE, Unpooled.EMPTY_BUFFER))
          .await(1000);
      time.addAndGet(112); // ~9 per sec
    }

    assertThat(channel.isOpen()).isFalse();
    assertThat(passedMessages).hasSizeBetween(18, 22).doesNotHaveDuplicates();
    // hasSizeBetween: giving the Firewall some flexibility to pass 1 extra OPEN frame before
    // closing connection
    assertThat(channel.outboundMessages().stream().map(Object::toString))
        .hasSizeBetween(20, 22)
        .doesNotHaveDuplicates();
  }
}
