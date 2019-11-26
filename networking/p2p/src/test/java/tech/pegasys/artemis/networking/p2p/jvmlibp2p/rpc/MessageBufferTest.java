/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.MessageBuffer.DataConsumer;

class MessageBufferTest {

  private final DataConsumer consumer = mock(DataConsumer.class);
  private final MessageBuffer buffer = new MessageBuffer();
  private final ByteBuf input = Unpooled.wrappedBuffer(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});

  @Test
  public void shouldBeEmptyInitially() {
    assertThat(buffer.isEmpty()).isTrue();
  }

  @Test
  public void shouldNotBeEmptyWhenDataIsAdded() {
    buffer.appendData(Unpooled.wrappedBuffer(new byte[] {1, 2, 3}));
    assertThat(buffer.isEmpty()).isFalse();
  }

  @Test
  public void shouldRemoveConsumedBytes() throws Exception {
    when(consumer.consumeData(Bytes.wrapByteBuf(input))).thenReturn(3);
    when(consumer.consumeData(Bytes.wrapByteBuf(input).slice(3))).thenReturn(7);

    buffer.appendData(input);
    buffer.consumeData(consumer);

    verify(consumer).consumeData(Bytes.wrapByteBuf(input));
    verify(consumer).consumeData(Bytes.wrapByteBuf(input).slice(3));
    verifyNoMoreInteractions(consumer);
    assertThat(buffer.isEmpty()).isTrue();
  }

  @Test
  public void shouldStopConsumingDataWhenConsumerReturnsZero() throws Exception {
    when(consumer.consumeData(Bytes.wrapByteBuf(input))).thenReturn(3);
    when(consumer.consumeData(Bytes.wrapByteBuf(input).slice(3))).thenReturn(0);

    buffer.appendData(input);
    buffer.consumeData(consumer);

    verify(consumer).consumeData(Bytes.wrapByteBuf(input));
    verify(consumer).consumeData(Bytes.wrapByteBuf(input).slice(3));
    verifyNoMoreInteractions(consumer);
    assertThat(buffer.isEmpty()).isFalse();
  }

  @Test
  public void shouldRetainByteBufWhenAdded() {
    assertThat(input.refCnt()).isEqualTo(1);

    buffer.appendData(input);

    assertThat(input.refCnt()).isEqualTo(2);
  }

  @Test
  public void shouldReleaseAllByteBufsWhenClosed() {
    final ByteBuf input2 = Unpooled.wrappedBuffer(new byte[] {11, 22, 33});

    buffer.appendData(input);
    buffer.appendData(input2);

    assertThat(input.refCnt()).isEqualTo(2);
    assertThat(input2.refCnt()).isEqualTo(2);

    buffer.close();

    assertThat(input.refCnt()).isEqualTo(1);
    assertThat(input2.refCnt()).isEqualTo(1);
  }

  @Test
  public void shouldReleaseByteBufsWhenDataConsumed() throws Exception {
    final ByteBuf input2 = Unpooled.wrappedBuffer(new byte[] {11, 22, 33});
    when(consumer.consumeData(any())).thenReturn(11, 0);

    buffer.appendData(input);
    buffer.appendData(input2);

    buffer.consumeData(consumer);

    // All the data in input is consumed and it is released
    assertThat(input.refCnt()).isEqualTo(1);
    // but some data from input2 is left unconsumed so it is still retained
    assertThat(input2.refCnt()).isEqualTo(2);
  }

  @Test
  public void shouldBeAbleToCallCloseMultipleTimesSafely() {
    buffer.appendData(input);

    buffer.close();
    buffer.close();
    buffer.close();

    assertThat(buffer.isEmpty()).isTrue();
  }
}
