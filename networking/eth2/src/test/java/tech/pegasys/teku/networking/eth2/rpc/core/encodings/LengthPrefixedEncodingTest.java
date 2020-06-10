///*
// * Copyright 2019 ConsenSys AG.
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
// * the License. You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
// * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
// * specific language governing permissions and limitations under the License.
// */
//
//package tech.pegasys.teku.networking.eth2.rpc.core.encodings;
//
//import static java.util.Arrays.asList;
//import static java.util.Collections.singletonList;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static tech.pegasys.teku.util.config.Constants.MAX_CHUNK_SIZE;
//
//import com.google.common.primitives.UnsignedLong;
//import java.io.ByteArrayInputStream;
//import java.io.InputStream;
//import java.nio.charset.StandardCharsets;
//import org.apache.tuweni.bytes.Bytes;
//import org.apache.tuweni.bytes.Bytes32;
//import org.junit.jupiter.api.Test;
//import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
//import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
//import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
//import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
//
//class LengthPrefixedEncodingTest {
//
//  private final RpcEncoding encoding = RpcEncoding.SSZ;
//  private static final Bytes ONE_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x0A");
//  private static final Bytes TWO_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x8002");
//  private static final Bytes LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH =
//      ProtobufEncoder.encodeVarInt(MAX_CHUNK_SIZE + 1);
//
//  @Test
//  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooLong() {
//    assertThatThrownBy(
//            () ->
//                encoding.decodePayload(
//                    inputStream("0xAAAAAAAAAAAAAAAAAAAA80"), StatusMessage.class))
//        .isEqualTo(RpcException.CHUNK_TOO_LONG);
//  }
//
//  @Test
//  public void decodePayload_shouldReturnErrorWhenNoPayloadIsPresent() {
//    final InputStream invalidMessage = inputStream(ONE_BYTE_LENGTH_PREFIX);
//    assertThatThrownBy(() -> encoding.decodePayload(invalidMessage, StatusMessage.class))
//        .isEqualTo(RpcException.PAYLOAD_TRUNCATED);
//  }
//
//  @Test
//  public void decodePayload_shouldReturnErrorWhenPayloadTooShort() {
//    final Bytes correctMessage = createValidStatusMessage();
//    final int truncatedSize = correctMessage.size() - 5;
//    final InputStream partialMessage = inputStream(correctMessage.slice(0, truncatedSize));
//    assertThatThrownBy(() -> encoding.decodePayload(partialMessage, StatusMessage.class))
//        .isEqualTo(RpcException.PAYLOAD_TRUNCATED);
//  }
//
//  @Test
//  public void decodePayload_shouldReadPayloadWhenExtraDataIsAppended() throws RpcException {
//    final StatusMessage originalMessage = StatusMessage.createPreGenesisStatus();
//    final Bytes encoded = encoding.encodePayload(originalMessage);
//    final Bytes extraData = Bytes.of(1, 2, 3, 4);
//    final InputStream input = inputStream(encoded, extraData);
//    final StatusMessage result = encoding.decodePayload(input, StatusMessage.class);
//    assertThat(result).isEqualTo(originalMessage);
//  }
//
//  @Test
//  public void decodePayload_shouldRejectMessagesThatAreTooLong() {
//    // We should reject the message based on the length prefix and skip reading the data entirely.
//    final InputStream input = inputStream(LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH);
//    assertThatThrownBy(() -> encoding.decodePayload(input, StatusMessage.class))
//        .isEqualTo(RpcException.CHUNK_TOO_LONG);
//  }
//
//  @Test
//  public void decodePayload_shouldRejectEmptyMessages() {
//    final InputStream input = inputStream(Bytes.EMPTY);
//    assertThatThrownBy(() -> encoding.decodePayload(input, StatusMessage.class))
//        .isEqualTo(RpcException.MESSAGE_TRUNCATED);
//  }
//
//  @Test
//  public void decodePayload_shouldThrowErrorWhenPrefixTruncated() {
//    final InputStream input = inputStream(TWO_BYTE_LENGTH_PREFIX.slice(0, 1));
//    assertThatThrownBy(() -> encoding.decodePayload(input, StatusMessage.class))
//        .isEqualTo(RpcException.MESSAGE_TRUNCATED);
//  }
//
//  @Test
//  public void decodePayload_shouldThrowRpcExceptionIfMessageLengthPrefixIsMoreThanThreeBytes() {
//    assertThatThrownBy(() -> encoding.decodePayload(inputStream("0x80808001"), StatusMessage.class))
//        .isEqualTo(RpcException.CHUNK_TOO_LONG);
//  }
//
//  @Test
//  public void encodePayload_shouldEncodeBlocksByRootRequest() {
//    final Bytes encoded =
//        encoding.encodePayload(new BeaconBlocksByRootRequestMessage(singletonList(Bytes32.ZERO)));
//    // Just the length prefix and the hash itself.
//    assertThat(encoded).isEqualTo(Bytes.wrap(Bytes.fromHexString("0x20"), Bytes32.ZERO));
//  }
//
//  @Test
//  public void encodePayload_shouldEncodeStringWithoutWrapper() {
//    final String expected = "Some string to test";
//    final Bytes payloadBytes = Bytes.wrap(expected.getBytes(StandardCharsets.UTF_8));
//    final Bytes encoded = encoding.encodePayload(expected);
//    // Length prefix plus UTF-8 bytes.
//    assertThat(encoded).isEqualTo(Bytes.wrap(Bytes.fromHexString("0x13"), payloadBytes));
//  }
//
//  @Test
//  public void roundtrip_blocksByRootRequest() throws Exception {
//    final BeaconBlocksByRootRequestMessage request =
//        new BeaconBlocksByRootRequestMessage(
//            asList(Bytes32.ZERO, Bytes32.fromHexString("0x01"), Bytes32.fromHexString("0x02")));
//    final Bytes data = encoding.encodePayload(request);
//    final int expectedLengthPrefixLength = 1;
//    assertThat(data.size())
//        .isEqualTo(request.getBlockRoots().size() * Bytes32.SIZE + expectedLengthPrefixLength);
//    assertThat(encoding.decodePayload(inputStream(data), BeaconBlocksByRootRequestMessage.class))
//        .isEqualTo(request);
//  }
//
//  @Test
//  public void roundtrip_string() throws Exception {
//    final String expected = "Some string to test";
//    final Bytes encoded = encoding.encodePayload(expected);
//    assertThat(encoding.decodePayload(inputStream(encoded), String.class)).isEqualTo(expected);
//  }
//
//  private Bytes createValidStatusMessage() {
//    return encoding.encodePayload(
//        new StatusMessage(
//            new Bytes4(Bytes.of(0, 0, 0, 0)),
//            Bytes32.ZERO,
//            UnsignedLong.ZERO,
//            Bytes32.ZERO,
//            UnsignedLong.ZERO));
//  }
//
//  private InputStream inputStream(final Bytes... data) {
//    return inputStream(Bytes.concatenate(data));
//  }
//
//  private InputStream inputStream(final String hexString) {
//    return inputStream(Bytes.fromHexString(hexString));
//  }
//
//  private InputStream inputStream(final Bytes bytes) {
//    return new ByteArrayInputStream(bytes.toArrayUnsafe());
//  }
//}
