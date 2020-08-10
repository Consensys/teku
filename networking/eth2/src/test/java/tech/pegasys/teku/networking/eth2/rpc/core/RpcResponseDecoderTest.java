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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;

class RpcResponseDecoderTest extends RpcDecoderTestBase {
  private static final Bytes SUCCESS_CODE = Bytes.of(RpcResponseStatus.SUCCESS_RESPONSE_CODE);
  private static final Bytes ERROR_CODE = Bytes.of(1);

  private final RpcResponseDecoder<BeaconBlocksByRootRequestMessage> decoder =
      METHOD.createResponseDecoder();

  @Test
  public void decodeNextResponse_shouldParseSingleResponse() throws Exception {
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA)) {

      List<BeaconBlocksByRootRequestMessage> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(MESSAGE);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldParseMultipleResponses() throws Exception {
    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = PAYLOAD_ENCODER.encode(secondMessage);
    final Bytes compressedPayload = COMPRESSOR.compress(secondMessageData);

    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(
            SUCCESS_CODE,
            LENGTH_PREFIX,
            MESSAGE_DATA,
            SUCCESS_CODE,
            getLengthPrefix(secondMessageData.size()),
            compressedPayload)) {

      List<BeaconBlocksByRootRequestMessage> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(MESSAGE, secondMessage);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldThrowErrorIfStatusCodeIsNotSuccess() throws Exception {
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(ERROR_CODE, ERROR_MESSAGE_LENGTH_PREFIX, ERROR_MESSAGE_DATA)) {

      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                }
                decoder.complete();
              })
          .isEqualTo(new RpcException(ERROR_CODE.get(0), ERROR_MESSAGE));
    }
  }

  @Test
  public void decodeNextResponse_shouldThrowErrorIfStatusCodeIsNotSuccess_largeErrorCode()
      throws Exception {
    final Bytes errorCode = Bytes.of(255);
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(errorCode, ERROR_MESSAGE_LENGTH_PREFIX, ERROR_MESSAGE_DATA)) {

      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                }
                decoder.complete();
              })
          .isEqualTo(new RpcException(errorCode.get(0), ERROR_MESSAGE));
    }
  }
}
