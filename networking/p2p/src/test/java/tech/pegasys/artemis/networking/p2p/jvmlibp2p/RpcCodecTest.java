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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.Response;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcCodec;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings.SszEncoding;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

final class RpcCodecTest {

  private static final Bytes RECORDED_STATUS_REQUEST_BYTES =
      Bytes.fromHexString(
          "0x54000000000000000000000000000000000000000000000000000000000000000000000000000000000000000030A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E60000000000000000");
  private static final Bytes RECORDED_STATUS_RESPONSE_BYTES =
      Bytes.fromHexString(
          "0x0054000000000000000000000000000000000000000000000000000000000000000000000000000000000000000030A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E60000000000000000");
  private static final StatusMessage RECORDED_STATUS_MESSAGE_DATA =
      new StatusMessage(
          new Bytes4(Bytes.of(0, 0, 0, 0)),
          Bytes32.ZERO,
          UnsignedLong.ZERO,
          Bytes32.fromHexString(
              "0x30A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E6"),
          UnsignedLong.ZERO);

  private final RpcCodec codec = new RpcCodec(new SszEncoding());

  @Test
  void testStatusRoundtripSerialization() {
    final StatusMessage expected =
        new StatusMessage(
            Bytes4.rightPad(Bytes.of(4)),
            Bytes32.random(),
            UnsignedLong.ZERO,
            Bytes32.random(),
            UnsignedLong.ZERO);

    final Bytes encoded = codec.encodeRequest(expected);
    final StatusMessage actual = codec.decodeRequest(encoded, StatusMessage.class);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void shouldDecodeStatusMessageRequest() {
    final StatusMessage actualMessage =
        codec.decodeRequest(RECORDED_STATUS_REQUEST_BYTES, StatusMessage.class);
    assertThat(actualMessage)
        .isEqualToComparingFieldByFieldRecursively(RECORDED_STATUS_MESSAGE_DATA);
  }

  @Test
  public void shouldEncodeStatusRequest() {
    final Bytes actualBytes = codec.encodeRequest(RECORDED_STATUS_MESSAGE_DATA);
    assertThat(actualBytes).isEqualTo(RECORDED_STATUS_REQUEST_BYTES);
  }

  @Test
  public void shouldDecodeStatusResponse() {
    final Response<StatusMessage> actualMessage =
        codec.decodeResponse(RECORDED_STATUS_RESPONSE_BYTES, StatusMessage.class);
    assertThat(actualMessage)
        .isEqualToComparingFieldByFieldRecursively(
            new Response<>(Bytes.of(0), RECORDED_STATUS_MESSAGE_DATA));
  }

  @Test
  public void shouldEncodeSuccessfulResponse() {
    final Bytes actual = codec.encodeSuccessfulResponse(RECORDED_STATUS_MESSAGE_DATA);
    assertThat(actual).isEqualTo(RECORDED_STATUS_RESPONSE_BYTES);
  }
}
