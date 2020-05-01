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

package tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.primitives.UnsignedLong;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.eth2.peers.PeerLookup;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcRequestDecoder;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.async.AsyncRunner;
import tech.pegasys.artemis.util.async.StubAsyncRunner;

public class BeaconChainMethodsTest {

  private static final Bytes SSZ_RECORDED_STATUS_REQUEST_BYTES =
      Bytes.fromHexString(
          "0x54000000000000000000000000000000000000000000000000000000000000000000000000000000000000000030A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E60000000000000000");
  private static final StatusMessage RECORDED_STATUS_MESSAGE_DATA =
      new StatusMessage(
          new Bytes4(Bytes.of(0, 0, 0, 0)),
          Bytes32.ZERO,
          UnsignedLong.ZERO,
          Bytes32.fromHexString(
              "0x30A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E6"),
          UnsignedLong.ZERO);

  private final PeerLookup peerLookup = mock(PeerLookup.class);
  final AsyncRunner asyncRunner = new StubAsyncRunner();
  final CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
  final RecentChainData recentChainData = mock(RecentChainData.class);
  final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  final StatusMessageFactory statusMessageFactory = new StatusMessageFactory(recentChainData);

  @ParameterizedTest(name = "encoding: {0}")
  @MethodSource("getEncodings")
  void testStatusRoundtripSerialization(String name, RpcEncoding encoding) throws Exception {
    final BeaconChainMethods methods = getMethods(encoding);
    final StatusMessage expected =
        new StatusMessage(
            Bytes4.rightPad(Bytes.of(4)),
            Bytes32.random(),
            UnsignedLong.ZERO,
            Bytes32.random(),
            UnsignedLong.ZERO);

    final Bytes encoded = methods.status().encodeRequest(expected);
    final RpcRequestDecoder<StatusMessage> decoder = methods.status().createRequestDecoder();
    StatusMessage decodedRequest = decoder.decodeRequest(inputStream(encoded));

    assertThat(decodedRequest).isEqualTo(expected);
  }

  @Test
  public void shouldDecodeStatusMessageRequest() throws Exception {
    final BeaconChainMethods methods = getMethods(RpcEncoding.SSZ);
    final RpcRequestDecoder<StatusMessage> decoder = methods.status().createRequestDecoder();
    final StatusMessage decodedRequest =
        decoder.decodeRequest(inputStream(SSZ_RECORDED_STATUS_REQUEST_BYTES));
    assertThat(decodedRequest).isEqualTo(RECORDED_STATUS_MESSAGE_DATA);
  }

  private InputStream inputStream(final Bytes bytes) {
    return new ByteArrayInputStream(bytes.toArrayUnsafe());
  }

  public static Stream<Arguments> getEncodings() {
    final List<RpcEncoding> encodings = List.of(RpcEncoding.SSZ, RpcEncoding.SSZ_SNAPPY);
    return encodings.stream().map(e -> Arguments.of(e.getName(), e));
  }

  private BeaconChainMethods getMethods(final RpcEncoding rpcEncoding) {
    return BeaconChainMethods.create(
        asyncRunner,
        peerLookup,
        combinedChainDataClient,
        recentChainData,
        metricsSystem,
        statusMessageFactory,
        rpcEncoding);
  }
}
