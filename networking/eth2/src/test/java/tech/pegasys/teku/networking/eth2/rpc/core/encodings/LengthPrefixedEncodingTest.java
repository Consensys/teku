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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ChunkTooLongException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.MessageTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyFramedCompressor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;

class LengthPrefixedEncodingTest {
  private static final Bytes TWO_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x8002");
  private static final Bytes LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH =
      ProtobufEncoder.encodeVarInt(MAX_CHUNK_SIZE + 1);

  private final Spec spec = TestSpecFactory.createDefault();
  private final RpcEncoding encoding = RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);

  @Test
  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooLong() {
    List<List<ByteBuf>> testByteBufSlices =
        Utils.generateTestSlices(Bytes.fromHexString("0xAAAAAAAAAAAAAAAAAAAA80"));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  decoder.decodeOneMessage(bufSlice);
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
              })
          .isInstanceOf(ChunkTooLongException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooShortForMessageType() {
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(Bytes.fromHexString("0x52"));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  decoder.decodeOneMessage(bufSlice);
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
              })
          .isInstanceOf(LengthOutOfBoundsException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooLongForMessageType() {
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(Bytes.fromHexString("0x55"));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  decoder.decodeOneMessage(bufSlice);
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
              })
          .isInstanceOf(LengthOutOfBoundsException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenNoPayloadIsPresent() {
    final Bytes statusMessageLengthPrefix = Bytes.fromHexString("0x54");
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(statusMessageLengthPrefix);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
                decoder.complete();
              })
          .isInstanceOf(PayloadTruncatedException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenPayloadTooShort() {
    final Bytes correctMessage = createValidStatusMessage();
    final int truncatedSize = correctMessage.size() - 5;
    List<List<ByteBuf>> testByteBufSlices =
        Utils.generateTestSlices(correctMessage.slice(0, truncatedSize));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
                decoder.complete();
              })
          .isInstanceOf(PayloadTruncatedException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReadPayloadWhenExtraDataIsAppended() throws RpcException {
    final StatusMessage originalMessage = StatusMessage.createPreGenesisStatus(spec);
    final Bytes encoded = encoding.encodePayload(originalMessage);
    final Bytes extraData = Bytes.of(1, 2, 3, 4);
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(encoded, extraData);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
      Optional<StatusMessage> result = Optional.empty();
      int unreadBytes = 0;
      for (ByteBuf bufSlice : bufSlices) {
        if (result.isEmpty()) {
          result = decoder.decodeOneMessage(bufSlice);
        }
        if (result.isPresent()) {
          unreadBytes += bufSlice.readableBytes();
        }
        bufSlice.release();
      }
      decoder.complete();
      assertThat(result).contains(originalMessage);
      assertThat(unreadBytes).isEqualTo(4);
      assertThat(bufSlices).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldRejectMessagesThatAreTooLong() {
    // We should reject the message based on the length prefix and skip reading the data entirely
    List<List<ByteBuf>> testByteBufSlices =
        Utils.generateTestSlices(LENGTH_PREFIX_EXCEEDING_MAXIMUM_LENGTH);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
                decoder.complete();
              })
          .isInstanceOf(ChunkTooLongException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldRejectEmptyMessages() {
    final ByteBuf input = Utils.emptyBuf();
    RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);

    assertThatThrownBy(
            () -> {
              decoder.decodeOneMessage(input);
              decoder.complete();
            })
        .isInstanceOf(MessageTruncatedException.class);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldThrowErrorWhenPrefixTruncated() {
    final ByteBuf input = inputByteBuffer(TWO_BYTE_LENGTH_PREFIX.slice(0, 1));
    assertThatThrownBy(
            () -> {
              RpcByteBufDecoder<StatusMessage> decoder =
                  encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
              decoder.decodeOneMessage(input);
              decoder.complete();
            })
        .isInstanceOf(MessageTruncatedException.class);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldThrowRpcExceptionIfMessageLengthPrefixIsMoreThanThreeBytes() {
    final ByteBuf input = inputByteBuffer("0x80808001");
    RpcByteBufDecoder<StatusMessage> decoder = encoding.createDecoder(StatusMessage.SSZ_SCHEMA);
    assertThatThrownBy(() -> decoder.decodeOneMessage(input))
        .isInstanceOf(ChunkTooLongException.class);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void encodePayload_shouldEncodeBlocksByRootRequest() {
    final Bytes encoded =
        encoding.encodePayload(new BeaconBlocksByRootRequestMessage(singletonList(Bytes32.ZERO)));
    // Just the length prefix and the hash itself.
    assertThat(encoded)
        .isEqualTo(
            Bytes.wrap(
                Bytes.fromHexString("0x20"), new SnappyFramedCompressor().compress(Bytes32.ZERO)));
  }

  @Test
  void encodePayload_shouldReturnZeroBytesForEmptyMessages() {
    final Bytes result = encoding.encodePayload(EmptyMessage.EMPTY_MESSAGE);
    assertThat(result).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void shouldDecodeEmptyMessage() throws Exception {
    final RpcByteBufDecoder<EmptyMessage> decoder = encoding.createDecoder(EmptyMessage.SSZ_SCHEMA);
    final Optional<EmptyMessage> message =
        decoder.decodeOneMessage(Unpooled.wrappedBuffer(new byte[0]));
    assertThat(message).contains(EmptyMessage.EMPTY_MESSAGE);
  }

  @Test
  public void roundtrip_blocksByRootRequest() throws Exception {
    final BeaconBlocksByRootRequestMessage request =
        new BeaconBlocksByRootRequestMessage(
            List.of(Bytes32.ZERO, Bytes32.fromHexString("0x01"), Bytes32.fromHexString("0x02")));
    final Bytes data = encoding.encodePayload(request);
    final int expectedLengthPrefixLength = 1;
    final Bytes uncompressedPayload =
        Bytes.wrap(Bytes32.ZERO, Bytes32.fromHexString("0x01"), Bytes32.fromHexString("0x02"));
    final Bytes payload = new SnappyFramedCompressor().compress(uncompressedPayload);
    assertThat(data.size()).isEqualTo(payload.size() + expectedLengthPrefixLength);

    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(data);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<BeaconBlocksByRootRequestMessage> decoder =
          encoding.createDecoder(BeaconBlocksByRootRequestMessage.SSZ_SCHEMA);
      Optional<BeaconBlocksByRootRequestMessage> result = Optional.empty();
      for (ByteBuf bufSlice : bufSlices) {
        if (result.isEmpty()) {
          result = decoder.decodeOneMessage(bufSlice);
        } else {
          assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
        }
        bufSlice.release();
      }
      decoder.complete();
      assertThat(result).contains(request);
      assertThat(bufSlices).allMatch(b -> b.refCnt() == 0);
    }
  }

  private Bytes createValidStatusMessage() {
    return encoding.encodePayload(
        new StatusMessage(
            new Bytes4(Bytes.of(0, 0, 0, 0)),
            Bytes32.ZERO,
            UInt64.ZERO,
            Bytes32.ZERO,
            UInt64.ZERO));
  }

  private ByteBuf inputByteBuffer(final Bytes... data) {
    return Utils.toByteBuf(data);
  }

  private ByteBuf inputByteBuffer(final String hexString) {
    return inputByteBuffer(Bytes.fromHexString(hexString));
  }
}
