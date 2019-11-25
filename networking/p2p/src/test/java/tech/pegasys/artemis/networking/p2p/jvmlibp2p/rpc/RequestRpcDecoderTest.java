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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.primitives.UnsignedLong;
import io.netty.buffer.Unpooled;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings.SszEncoding;
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

  private final SszEncoding encoding = new SszEncoding();

  @SuppressWarnings("unchecked")
  private final Consumer<BeaconBlocksByRootRequestMessage> callback = mock(Consumer.class);

  private final RpcDecoder codec = new RequestRpcDecoder<>(callback, METHOD);

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
    final AtomicReference<StatusMessage> decodedRequest = new AtomicReference<>();
    final RequestRpcDecoder<StatusMessage> decoder =
        new RequestRpcDecoder<>(decodedRequest::set, StatusMessage.class, encoding);
    decoder.onDataReceived(Unpooled.wrappedBuffer(encoded.toArrayUnsafe()));
    decoder.close();

    assertThat(decodedRequest.get()).isEqualTo(expected);
  }

  @Test
  public void shouldDecodeStatusMessageRequest() throws Exception {
    final AtomicReference<StatusMessage> decodedRequest = new AtomicReference<>();
    final RequestRpcDecoder<StatusMessage> decoder =
        new RequestRpcDecoder<>(decodedRequest::set, StatusMessage.class, encoding);
    decoder.onDataReceived(Unpooled.wrappedBuffer(RECORDED_STATUS_REQUEST_BYTES.toArrayUnsafe()));
    assertThat(decodedRequest.get())
        .usingRecursiveComparison()
        .isEqualTo(RECORDED_STATUS_MESSAGE_DATA);
  }

  @Test
  public void shouldParseSingleResponseReceivedInSinglePacket() throws Exception {
    codec.onDataReceived(buffer(LENGTH_PREFIX, MESSAGE_DATA));

    verifyRequestReceived();
  }

  @Test
  public void shouldParseSingleResponseReceivedWithLengthAndMessageInDifferentPackets()
      throws Exception {
    codec.onDataReceived(buffer(LENGTH_PREFIX));
    codec.onDataReceived(buffer(MESSAGE_DATA));

    verifyRequestReceived();
  }

  @Test
  public void shouldParseSingleResponseReceivedWithLengthSplitInMultiplePackets() throws Exception {
    codec.onDataReceived(buffer(LENGTH_PREFIX.slice(0, 2)));
    codec.onDataReceived(buffer(LENGTH_PREFIX.slice(2), MESSAGE_DATA));

    verifyRequestReceived();
  }

  @Test
  public void shouldParseSingleResponseReceivedWithDataInMultiplePackets() throws Exception {
    codec.onDataReceived(buffer(LENGTH_PREFIX));
    // Split message data into a number of separate packets
    for (int i = 0; i < MESSAGE_DATA.size(); i += 5) {
      codec.onDataReceived(buffer(MESSAGE_DATA.slice(i, Math.min(MESSAGE_DATA.size() - i, 5))));
    }
    verifyRequestReceived();
  }

  @Test
  public void shouldThrowErrorIfMessagesHaveTrailingData() throws Exception {
    codec.onDataReceived(buffer(LENGTH_PREFIX, MESSAGE_DATA, Bytes.fromHexString("0x1234")));
    verify(callback).accept(MESSAGE);

    assertThatThrownBy(codec::close).isEqualTo(RpcException.INCORRECT_LENGTH_ERROR);
  }

  @Test
  public void shouldNotThrowErrorFromCloseAfterErrorAlreadyThrown() throws Exception {
    assertThatThrownBy(() -> codec.onDataReceived(buffer(Bytes.fromHexString("0x0234678934"))))
        .isInstanceOf(RpcException.class);

    // Should not throw an exception despite the trailing data.
    codec.close();
  }

  private void verifyRequestReceived() {
    verify(callback).accept(MESSAGE);
    verifyNoMoreInteractions(callback);
  }
}
