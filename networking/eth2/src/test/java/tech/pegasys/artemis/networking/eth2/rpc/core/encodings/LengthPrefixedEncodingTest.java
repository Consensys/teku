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

package tech.pegasys.artemis.networking.eth2.rpc.core.encodings;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.primitives.UnsignedLong;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcException;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

class LengthPrefixedEncodingTest {

  private final RpcEncoding encoding = RpcEncoding.SSZ;
  private static final int ONE_BYTE_LENGTH_PREFIX_VALUE = 10;
  private static final Bytes ONE_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x0A");
  private static final int TWO_BYTE_LENGTH_PREFIX_VALUE = 256;
  private static final Bytes TWO_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x8002");
  private static final int THREE_BYTE_LENGTH_PREFIX_VALUE = 1048573;
  private static final Bytes THREE_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0xFDFF3F");
  private static final Bytes LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH =
      Bytes.fromHexString("0x818040");

  @Test
  public void shouldReturnErrorWhenMessageLengthIsInvalid() {
    assertThatThrownBy(
            () ->
                encoding.decode(Bytes.fromHexString("0xAAAAAAAAAAAAAAAAAAAA"), StatusMessage.class))
        .isEqualTo(RpcException.MALFORMED_REQUEST_ERROR);
  }

  @Test
  public void shouldReturnErrorWhenMessageDataIsInvalid() {
    final Bytes invalidMessage = Bytes.fromHexString("0x01AA");
    assertThatThrownBy(() -> encoding.decode(invalidMessage, StatusMessage.class))
        .isEqualTo(RpcException.MALFORMED_REQUEST_ERROR);
  }

  @Test
  public void shouldReturnErrorWhenMessageTooShort() {
    final Bytes correctMessage = createValidStatusMessage();
    assertThatThrownBy(
            () ->
                encoding.decode(
                    correctMessage.slice(0, correctMessage.size() - 5), StatusMessage.class))
        .isEqualTo(RpcException.INCORRECT_LENGTH_ERROR);
  }

  @Test
  public void shouldReturnErrorWhenMessageTooLong() {
    final Bytes correctMessage = createValidStatusMessage();
    assertThatThrownBy(
            () ->
                encoding.decode(
                    Bytes.concatenate(correctMessage, Bytes.of(1, 2, 3, 4)), StatusMessage.class))
        .isEqualTo(RpcException.INCORRECT_LENGTH_ERROR);
  }

  @Test
  public void shouldRejectMessagesThatAreTooLong() {
    // We should reject the message based on the length prefix and skip reading the data entirely.
    assertThatThrownBy(
            () -> encoding.decode(LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH, StatusMessage.class))
        .isEqualTo(RpcException.CHUNK_TOO_LONG_ERROR);
  }

  @Test
  public void shouldNotHaveMessageLengthWhenNoDataProvided() throws Exception {
    assertThat(encoding.getMessageLength(Bytes.EMPTY)).isEmpty();
  }

  @Test
  public void shouldNotHaveMessageLengthWhenOnlyPartialLengthPrefixReceived() throws Exception {
    assertThat(encoding.getMessageLength(TWO_BYTE_LENGTH_PREFIX.slice(0, 1))).isEmpty();
  }

  @Test
  public void shouldIncludeBytesInLengthPrefixWhenCalculatingMessageLength() throws Exception {
    assertThat(encoding.getMessageLength(ONE_BYTE_LENGTH_PREFIX))
        .hasValue(ONE_BYTE_LENGTH_PREFIX_VALUE + 1);
    assertThat(encoding.getMessageLength(TWO_BYTE_LENGTH_PREFIX))
        .hasValue(TWO_BYTE_LENGTH_PREFIX_VALUE + 2);
    assertThat(encoding.getMessageLength(THREE_BYTE_LENGTH_PREFIX))
        .hasValue(THREE_BYTE_LENGTH_PREFIX_VALUE + 3);
  }

  @Test
  public void shouldThrowRpcExceptionIfMessageLengthPrefixIsMoreThanThreeBytes() {
    assertThatThrownBy(() -> encoding.getMessageLength(Bytes.fromHexString("0x80808001")))
        .isEqualTo(RpcException.CHUNK_TOO_LONG_ERROR);
  }

  @Test
  public void shouldEncodeBlocksByRootRequest() {
    final Bytes encoded =
        encoding.encode(new BeaconBlocksByRootRequestMessage(singletonList(Bytes32.ZERO)));
    // Just the length prefix and the hash itself.
    assertThat(encoded).isEqualTo(Bytes.wrap(Bytes.fromHexString("0x20"), Bytes32.ZERO));
  }

  @Test
  public void shouldRoundTripBlocksByRootRequest() throws Exception {
    final BeaconBlocksByRootRequestMessage request =
        new BeaconBlocksByRootRequestMessage(
            asList(Bytes32.ZERO, Bytes32.fromHexString("0x01"), Bytes32.fromHexString("0x02")));
    final Bytes data = encoding.encode(request);
    final int expectedLengthPrefixLength = 1;
    assertThat(data.size())
        .isEqualTo(request.getBlockRoots().size() * Bytes32.SIZE + expectedLengthPrefixLength);
    assertThat(encoding.decode(data, BeaconBlocksByRootRequestMessage.class))
        .isEqualTo(request);
  }

  @Test
  public void shouldEncodeStringWithoutWrapper() {
    final String expected = "Some string to test";
    final Bytes payloadBytes = Bytes.wrap(expected.getBytes(StandardCharsets.UTF_8));
    final Bytes encoded = encoding.encode(expected);
    // Length prefix plus UTF-8 bytes.
    assertThat(encoded).isEqualTo(Bytes.wrap(Bytes.fromHexString("0x13"), payloadBytes));
  }

  @Test
  public void shouldRoundtripString() throws Exception {
    final String expected = "Some string to test";
    final Bytes encoded = encoding.encode(expected);
    assertThat(encoding.decode(encoded, String.class)).isEqualTo(expected);
  }

  private Bytes createValidStatusMessage() {
    return encoding.encode(
        new StatusMessage(
            new Bytes4(Bytes.of(0, 0, 0, 0)),
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO));
  }
}
