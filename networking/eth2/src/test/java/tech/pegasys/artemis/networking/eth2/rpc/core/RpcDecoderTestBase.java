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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.protobuf.CodedOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.ssz.BeaconBlocksByRootRequestMessageEncoder;
import tech.pegasys.artemis.storage.clientside.CombinedChainDataClient;
import tech.pegasys.artemis.storage.clientside.RecentChainData;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class RpcDecoderTestBase {

  // Message long enough to require a three byte length prefix.
  protected static final BeaconBlocksByRootRequestMessage MESSAGE = createRequestMessage(600);
  protected static final RpcPayloadEncoder<BeaconBlocksByRootRequestMessage> PAYLOAD_ENCODER =
      new BeaconBlocksByRootRequestMessageEncoder();
  protected static final Bytes MESSAGE_DATA = PAYLOAD_ENCODER.encode(MESSAGE);
  protected static final Bytes LENGTH_PREFIX = getLengthPrefix(MESSAGE_DATA.size());
  protected static final String ERROR_MESSAGE = "Bad request";
  protected static final Bytes ERROR_MESSAGE_DATA =
      Bytes.wrap(ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8));
  protected static final Bytes ERROR_MESSAGE_LENGTH_PREFIX =
      getLengthPrefix(ERROR_MESSAGE_DATA.size());

  protected static final AsyncRunner asyncRunner = new StubAsyncRunner();
  protected static final PeerLookup peerLookup = mock(PeerLookup.class);
  protected static final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  protected static final RecentChainData RECENT_CHAIN_DATA = mock(RecentChainData.class);

  protected static final BeaconChainMethods BEACON_CHAIN_METHODS =
      BeaconChainMethods.create(
          asyncRunner,
          peerLookup,
          combinedChainDataClient,
          RECENT_CHAIN_DATA,
          new NoOpMetricsSystem(),
          new StatusMessageFactory(RECENT_CHAIN_DATA));

  @SuppressWarnings("unchecked")
  protected static final Eth2RpcMethod<
          BeaconBlocksByRootRequestMessage, BeaconBlocksByRootRequestMessage>
      METHOD =
          new Eth2RpcMethod<>(
              asyncRunner,
              "",
              RpcEncoding.SSZ,
              BeaconBlocksByRootRequestMessage.class,
              BeaconBlocksByRootRequestMessage.class,
              false,
              mock(LocalMessageHandler.class),
              peerLookup);

  private final List<ByteBuf> allocatedBuffers = new ArrayList<>();

  @BeforeAll
  public static void sanityCheckConstants() {
    assertThat(LENGTH_PREFIX.size()).isEqualTo(3);
  }

  @AfterEach
  public void tearDownBuffers() {
    allocatedBuffers.forEach(
        buffer -> {
          buffer.release(); // Release our own reference tracked on initial creation
          assertThat(buffer.refCnt()).describedAs("Did not release buffer").isZero();
        });
  }

  protected ByteBuf buffer(final Bytes... bytes) {
    final byte[][] data = Stream.of(bytes).map(Bytes::toArrayUnsafe).toArray(byte[][]::new);
    final ByteBuf buffer = Unpooled.wrappedBuffer(data);
    allocatedBuffers.add(buffer);
    return buffer;
  }

  protected static BeaconBlocksByRootRequestMessage createRequestMessage(
      final int blocksRequested) {
    final List<Bytes32> roots = new ArrayList<>();
    for (int i = 0; i < blocksRequested; i++) {
      roots.add(Bytes32.leftPad(Bytes.ofUnsignedInt(i)));
    }
    return new BeaconBlocksByRootRequestMessage(roots);
  }

  protected static Bytes getLengthPrefix(final int size) {
    try {
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(output);
      codedOutputStream.writeUInt32NoTag(size);
      codedOutputStream.flush();
      return Bytes.wrap(output.toByteArray());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
