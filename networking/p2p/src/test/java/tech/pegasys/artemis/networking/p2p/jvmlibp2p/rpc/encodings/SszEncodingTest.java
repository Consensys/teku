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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcException;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

class SszEncodingTest {

  private final SszEncoding encoding = new SszEncoding();
  private static final Bytes EXCEEDS_MAXIMUM_LENGTH = Bytes.fromHexString("0x818040");

  @Test
  public void shouldReturnErrorWhenMessageLengthIsInvalid() {
    assertThatThrownBy(
            () ->
                encoding.decodeMessage(
                    Bytes.fromHexString("0xAAAAAAAAAAAAAAAAAAAA"), StatusMessage.class))
        .isEqualTo(RpcException.MALFORMED_REQUEST_ERROR);
  }

  @Test
  public void shouldReturnErrorWhenMessageDataIsInvalid() {
    final Bytes invalidMessage = Bytes.fromHexString("0x01AA");
    assertThatThrownBy(() -> encoding.decodeMessage(invalidMessage, StatusMessage.class))
        .isEqualTo(RpcException.MALFORMED_REQUEST_ERROR);
  }

  @Test
  public void shouldReturnErrorWhenMessageTooShort() {
    final Bytes correctMessage = createValidStatusMessage();
    assertThatThrownBy(
            () ->
                encoding.decodeMessage(
                    correctMessage.slice(0, correctMessage.size() - 5), StatusMessage.class))
        .isEqualTo(RpcException.INCORRECT_LENGTH_ERRROR);
  }

  @Test
  public void shouldReturnErrorWhenMessageTooLong() {
    final Bytes correctMessage = createValidStatusMessage();
    assertThatThrownBy(
            () ->
                encoding.decodeMessage(
                    Bytes.concatenate(correctMessage, Bytes.of(1, 2, 3, 4)), StatusMessage.class))
        .isEqualTo(RpcException.INCORRECT_LENGTH_ERRROR);
  }

  @Test
  public void shouldRejectMessagesThatAreTooLong() {
    // We should reject the message based on the length prefix and skip reading the data entirely.
    assertThatThrownBy(() -> encoding.decodeMessage(EXCEEDS_MAXIMUM_LENGTH, StatusMessage.class))
        .isEqualTo(RpcException.CHUNK_TOO_LONG_ERROR);
  }

  private Bytes createValidStatusMessage() {
    return encoding.encodeMessage(
        new StatusMessage(
            new Bytes4(Bytes.of(0, 0, 0, 0)),
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO));
  }
}
