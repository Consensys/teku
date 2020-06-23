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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.util.config.Constants.MAX_CHUNK_SIZE;

import com.google.common.primitives.UnsignedLong;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

class LengthPrefixedEncodingTest {

  private final RpcEncoding encoding = RpcEncoding.SSZ;
  private static final Bytes ONE_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x0A");
  private static final Bytes TWO_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x8002");
  private static final Bytes LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH =
      ProtobufEncoder.encodeVarInt(MAX_CHUNK_SIZE + 1);

  @Test
  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooLong() {
    ByteBuf input = inputByteBuffer("0xAAAAAAAAAAAAAAAAAAAA80");
    RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.class);
    assertThatThrownBy(() -> decoder.decodeOneMessage(input))
        .isEqualTo(RpcException.CHUNK_TOO_LONG);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenNoPayloadIsPresent() {
    ByteBuf input = inputByteBuffer(ONE_BYTE_LENGTH_PREFIX);
    assertThatThrownBy(
            () -> {
              RpcByteBufDecoder<StatusMessage> decoder =
                  encoding.createDecoder(StatusMessage.class);

              assertThat(decoder.decodeOneMessage(input)).isEmpty();
              decoder.complete();
            })
        .isEqualTo(RpcException.PAYLOAD_TRUNCATED);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenPayloadTooShort() {
    final Bytes correctMessage = createValidStatusMessage();
    final int truncatedSize = correctMessage.size() - 5;
    ByteBuf input = inputByteBuffer(correctMessage.slice(0, truncatedSize));
    assertThatThrownBy(
            () -> {
              RpcByteBufDecoder<StatusMessage> decoder =
                  encoding.createDecoder(StatusMessage.class);
              assertThat(decoder.decodeOneMessage(input)).isEmpty();
              decoder.complete();
            })
        .isEqualTo(RpcException.PAYLOAD_TRUNCATED);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldReadPayloadWhenExtraDataIsAppended() throws RpcException {
    final StatusMessage originalMessage = StatusMessage.createPreGenesisStatus();
    final Bytes encoded = encoding.encodePayload(originalMessage);
    final Bytes extraData = Bytes.of(1, 2, 3, 4);
    final ByteBuf input = inputByteBuffer(encoded, extraData);
    RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.class);
    final Optional<StatusMessage> result = decoder.decodeOneMessage(input);
    decoder.complete();
    assertThat(result).contains(originalMessage);
    assertThat(input.readableBytes()).isEqualTo(4);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldRejectMessagesThatAreTooLong() {
    // We should reject the message based on the length prefix and skip reading the data entirely
    final ByteBuf input = inputByteBuffer(LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH);
    RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.class);
    assertThatThrownBy(() -> decoder.decodeOneMessage(input))
        .isEqualTo(RpcException.CHUNK_TOO_LONG);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldRejectEmptyMessages() {
    final ByteBuf input = Utils.emptyBuf();
    RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.class);
    assertThatThrownBy(
            () -> {
              decoder.decodeOneMessage(input);
              decoder.complete();
            })
        .isEqualTo(RpcException.MESSAGE_TRUNCATED);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldThrowErrorWhenPrefixTruncated() {
    final ByteBuf input = inputByteBuffer(TWO_BYTE_LENGTH_PREFIX.slice(0, 1));
    assertThatThrownBy(
            () -> {
              RpcByteBufDecoder<StatusMessage> decoder =
                  encoding.createDecoder(StatusMessage.class);
              decoder.decodeOneMessage(input);
              decoder.complete();
            })
        .isEqualTo(RpcException.MESSAGE_TRUNCATED);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldThrowRpcExceptionIfMessageLengthPrefixIsMoreThanThreeBytes() {
    final ByteBuf input = inputByteBuffer("0x80808001");
    RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.class);
    assertThatThrownBy(() -> decoder.decodeOneMessage(input))
        .isEqualTo(RpcException.CHUNK_TOO_LONG);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void encodePayload_shouldEncodeBlocksByRootRequest() {
    final Bytes encoded =
        encoding.encodePayload(new BeaconBlocksByRootRequestMessage(singletonList(Bytes32.ZERO)));
    // Just the length prefix and the hash itself.
    assertThat(encoded).isEqualTo(Bytes.wrap(Bytes.fromHexString("0x20"), Bytes32.ZERO));
  }

  @Test
  public void encodePayload_shouldEncodeStringWithoutWrapper() {
    final String expected = "Some string to test";
    final Bytes payloadBytes = Bytes.wrap(expected.getBytes(StandardCharsets.UTF_8));
    final Bytes encoded = encoding.encodePayload(expected);
    // Length prefix plus UTF-8 bytes.
    assertThat(encoded).isEqualTo(Bytes.wrap(Bytes.fromHexString("0x13"), payloadBytes));
  }

  @Test
  public void roundtrip_blocksByRootRequest() throws Exception {
    final BeaconBlocksByRootRequestMessage request =
        new BeaconBlocksByRootRequestMessage(
            List.of(Bytes32.ZERO, Bytes32.fromHexString("0x01"), Bytes32.fromHexString("0x02")));
    final Bytes data = encoding.encodePayload(request);
    final int expectedLengthPrefixLength = 1;
    RpcByteBufDecoder<BeaconBlocksByRootRequestMessage> decoder =
        encoding.createDecoder(BeaconBlocksByRootRequestMessage.class);
    assertThat(data.size())
        .isEqualTo(request.getBlockRoots().size() * Bytes32.SIZE + expectedLengthPrefixLength);
    assertThat(decoder.decodeOneMessage(inputByteBuffer(data))).contains(request);
  }

  @Test
  public void roundtrip_string() throws Exception {
    final String expected = "Some string to test";
    final Bytes encoded = encoding.encodePayload(expected);
    assertThat(encoding.createDecoder(String.class).decodeOneMessage(inputByteBuffer(encoded)))
        .contains(expected);
  }

  private Bytes createValidStatusMessage() {
    return encoding.encodePayload(
        new StatusMessage(
            new Bytes4(Bytes.of(0, 0, 0, 0)),
            Bytes32.ZERO,
            UnsignedLong.ZERO,
            Bytes32.ZERO,
            UnsignedLong.ZERO));
  }

  private ByteBuf inputByteBuffer(final Bytes... data) {
    return Utils.toByteBuf(data);
  }

  private ByteBuf inputByteBuffer(final String hexString) {
    return inputByteBuffer(Bytes.fromHexString(hexString));
  }
}
