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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.primitives.UnsignedLong;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

class RequestRpcDecoderTest extends RpcDecoderTestBase {

  private static final Bytes RECORDED_STATUS_REQUEST_BYTES =
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

  private final RpcEncoding encoding = RpcEncoding.SSZ;

  private final RequestRpcDecoder<BeaconBlocksByRootRequestMessage> codec =
      new RequestRpcDecoder<>(METHOD);

  @Test
  void testStatusRoundtripSerialization() throws Exception {
    final StatusMessage expected =
        new StatusMessage(
            Bytes4.rightPad(Bytes.of(4)),
            Bytes32.random(),
            UnsignedLong.ZERO,
            Bytes32.random(),
            UnsignedLong.ZERO);

    final Bytes encoded = new RpcEncoder(encoding).encodeRequest(expected);
    final RequestRpcDecoder<StatusMessage> decoder =
        new RequestRpcDecoder<>(BeaconChainMethods.STATUS);
    StatusMessage decodedRequest =
        decoder.onDataReceived(Unpooled.wrappedBuffer(encoded.toArrayUnsafe())).orElseThrow();
    decoder.close();

    assertThat(decodedRequest).isEqualTo(expected);
  }

  @Test
  public void shouldDecodeStatusMessageRequest() throws Exception {
    final RequestRpcDecoder<StatusMessage> decoder =
        new RequestRpcDecoder<>(BeaconChainMethods.STATUS);
    final StatusMessage decodedRequest =
        decoder
            .onDataReceived(Unpooled.wrappedBuffer(RECORDED_STATUS_REQUEST_BYTES.toArrayUnsafe()))
            .orElseThrow();
    assertThat(decodedRequest).isEqualTo(RECORDED_STATUS_MESSAGE_DATA);
  }

  @Test
  public void shouldParseSingleResponseReceivedInSinglePacket() throws Exception {
    final Optional<BeaconBlocksByRootRequestMessage> request =
        codec.onDataReceived(buffer(LENGTH_PREFIX, MESSAGE_DATA));

    assertThat(request).contains(MESSAGE);
  }

  @Test
  public void shouldParseSingleResponseReceivedWithLengthAndMessageInDifferentPackets()
      throws Exception {
    assertThat(codec.onDataReceived(buffer(LENGTH_PREFIX))).isEmpty();
    assertThat(codec.onDataReceived(buffer(MESSAGE_DATA))).contains(MESSAGE);
  }

  @Test
  public void shouldParseSingleResponseReceivedWithLengthSplitInMultiplePackets() throws Exception {
    assertThat(codec.onDataReceived(buffer(LENGTH_PREFIX.slice(0, 2)))).isEmpty();
    assertThat(codec.onDataReceived(buffer(LENGTH_PREFIX.slice(2), MESSAGE_DATA)))
        .contains(MESSAGE);
  }

  @Test
  public void shouldParseSingleResponseReceivedWithDataInMultiplePackets() throws Exception {
    Optional<BeaconBlocksByRootRequestMessage> request = Optional.empty();
    codec.onDataReceived(buffer(LENGTH_PREFIX));
    // Split message data into a number of separate packets
    for (int i = 0; i < MESSAGE_DATA.size(); i += 5) {
      assertThat(request).isEmpty(); // Haven't sent all data, shouldn't have a message
      request =
          codec.onDataReceived(buffer(MESSAGE_DATA.slice(i, Math.min(MESSAGE_DATA.size() - i, 5))));
    }
    assertThat(request).contains(MESSAGE);
  }

  @Test
  public void shouldThrowErrorIfMessagesHaveTrailingData() {
    assertThatThrownBy(
            () ->
                codec.onDataReceived(
                    buffer(LENGTH_PREFIX, MESSAGE_DATA, Bytes.fromHexString("0x1234"))))
        .isEqualTo(RpcException.INCORRECT_LENGTH_ERROR);
    codec.close();
  }
}
