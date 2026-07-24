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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.TestForkDigestContextDecoder;

class AbstractByteBufDecoderTest {

  @Test
  void decodeOneMessage_afterComplete_returnsEmptyInsteadOfThrowing() throws Exception {
    final TestForkDigestContextDecoder decoder = new TestForkDigestContextDecoder();
    final ByteBuf buf = Unpooled.wrappedBuffer(Bytes.of(1, 2, 3, 4).toArray());

    final Optional<Bytes4> firstResult = decoder.decodeOneMessage(buf);
    assertThat(firstResult).isPresent();

    decoder.complete();

    // After complete(), decodeOneMessage must return empty rather than throw or access
    // the released CompositeByteBuf (Error 1) or re-enter complete() (Error 2).
    final ByteBuf buf2 = Unpooled.wrappedBuffer(Bytes.of(5, 6, 7, 8).toArray());
    final Optional<Bytes4> secondResult = decoder.decodeOneMessage(buf2);
    assertThat(secondResult).isEmpty();

    buf.release();
    buf2.release();
  }

  @Test
  void decodeOneMessage_afterClose_returnsEmptyInsteadOfThrowing() throws Exception {
    final TestForkDigestContextDecoder decoder = new TestForkDigestContextDecoder();
    final ByteBuf buf = Unpooled.wrappedBuffer(Bytes.of(1, 2, 3, 4).toArray());

    final Optional<Bytes4> firstResult = decoder.decodeOneMessage(buf);
    assertThat(firstResult).isPresent();

    decoder.close();

    final ByteBuf buf2 = Unpooled.wrappedBuffer(Bytes.of(5, 6, 7, 8).toArray());
    final Optional<Bytes4> secondResult = decoder.decodeOneMessage(buf2);
    assertThat(secondResult).isEmpty();

    buf.release();
    buf2.release();
  }
}
