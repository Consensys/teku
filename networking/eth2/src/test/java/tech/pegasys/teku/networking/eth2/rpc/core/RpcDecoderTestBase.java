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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static java.lang.Integer.min;
import static org.mockito.Mockito.mock;

import com.google.common.collect.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ProtobufEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.NoopCompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.SnappyFramedCompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.BeaconBlocksByRootRequestMessageEncoder;
import tech.pegasys.teku.util.async.AsyncRunner;
import tech.pegasys.teku.util.async.StubAsyncRunner;

public class RpcDecoderTestBase {

  // Message long enough to require a three byte length prefix.
  protected static final BeaconBlocksByRootRequestMessage MESSAGE = createRequestMessage(600);
  protected static final RpcPayloadEncoder<BeaconBlocksByRootRequestMessage> PAYLOAD_ENCODER =
      new BeaconBlocksByRootRequestMessageEncoder();
  protected static final RpcEncoding ENCODING = RpcEncoding.SSZ_SNAPPY;
  protected static final Compressor COMPRESSOR =
      ENCODING == RpcEncoding.SSZ ? new NoopCompressor() : new SnappyFramedCompressor();
  protected static final Bytes MESSAGE_PLAIN_DATA = PAYLOAD_ENCODER.encode(MESSAGE);
  protected static final Bytes MESSAGE_DATA = COMPRESSOR.compress(MESSAGE_PLAIN_DATA);
  protected static final Bytes LENGTH_PREFIX = getLengthPrefix(MESSAGE_PLAIN_DATA.size());
  protected static final String ERROR_MESSAGE = "Bad request";
  protected static final Bytes ERROR_MESSAGE_PLAIN_DATA =
      Bytes.wrap(ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8));
  protected static final Bytes ERROR_MESSAGE_DATA = COMPRESSOR.compress(ERROR_MESSAGE_PLAIN_DATA);
  protected static final Bytes ERROR_MESSAGE_LENGTH_PREFIX =
      getLengthPrefix(ERROR_MESSAGE_PLAIN_DATA.size());

  protected static final AsyncRunner asyncRunner = new StubAsyncRunner();
  protected static final PeerLookup peerLookup = mock(PeerLookup.class);

  @SuppressWarnings("unchecked")
  protected static final Eth2RpcMethod<
          BeaconBlocksByRootRequestMessage, BeaconBlocksByRootRequestMessage>
      METHOD =
          new Eth2RpcMethod<>(
              asyncRunner,
              "",
              ENCODING,
              BeaconBlocksByRootRequestMessage.class,
              BeaconBlocksByRootRequestMessage.class,
              false,
              mock(LocalMessageHandler.class),
              peerLookup);

  @BeforeAll
  public static void sanityCheckConstants() {
//    assertThat(LENGTH_PREFIX.size()).isEqualTo(3);
  }

  protected Iterable<Iterable<ByteBuf>> testByteBufSlices(final Bytes... bytes) {
    return List.of(
        List.of(toByteBuf(bytes)),
        Arrays.stream(bytes).map(RpcDecoderTestBase::toByteBuf).collect(Collectors.toList()),
        Arrays.stream(bytes)
            .map(RpcDecoderTestBase::toByteBuf)
            .flatMap(b -> slice(b, 1).stream())
            .collect(Collectors.toList()),
        Arrays.stream(bytes)
            .map(RpcDecoderTestBase::toByteBuf)
            .flatMap(b -> slice(b, 2).stream())
            .collect(Collectors.toList()),
        Arrays.stream(bytes)
            .map(RpcDecoderTestBase::toByteBuf)
            .flatMap(b -> slice(b, 1, 2).stream())
            .collect(Collectors.toList())
    );
  }

  protected static BeaconBlocksByRootRequestMessage createRequestMessage(
      final int blocksRequested) {
    final List<Bytes32> roots = new ArrayList<>();
    for (int i = 0; i < blocksRequested; i++) {
      roots.add(Bytes32.leftPad(Bytes.ofUnsignedInt(i)));
    }
    return new BeaconBlocksByRootRequestMessage(roots);
  }

  static Collection<ByteBuf> slice(ByteBuf src, int... pos) {
    return Streams.zip(
            IntStream.concat(IntStream.of(0), Arrays.stream(pos))
                .map(i -> min(i, src.readableBytes()))
                .boxed(),
            IntStream.concat(Arrays.stream(pos), IntStream.of(src.readableBytes()))
                .map(i -> min(i, src.readableBytes()))
                .boxed(),
            Pair::of)
        .map(rng -> Pair.of(rng.getLeft(), rng.getRight() - rng.getLeft()))
        .map(il -> src.slice(il.getLeft(), il.getRight()).copy())
        .collect(Collectors.toList());
  }

  static ByteBuf toByteBuf(final Bytes... bytes) {
    return Unpooled.wrappedBuffer(Bytes.concatenate(bytes).toArray());
  }

  protected static Bytes getLengthPrefix(final int size) {
    return ProtobufEncoder.encodeVarInt(size);
  }
}
