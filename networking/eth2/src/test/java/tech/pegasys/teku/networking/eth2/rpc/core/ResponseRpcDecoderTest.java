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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;

class ResponseRpcDecoderTest extends RpcDecoderTestBase {
  private static final Bytes SUCCESS_CODE = Bytes.of(0);
  private static final Bytes ERROR_CODE = Bytes.of(1);

  @SuppressWarnings("unchecked")
  private final Consumer<BeaconBlocksByRootRequestMessage> callback = mock(Consumer.class);

  private final ResponseRpcDecoder<BeaconBlocksByRootRequestMessage> codec =
      new ResponseRpcDecoder<>(callback, METHOD);

  @Test
  public void shouldParseSingleResponseReceivedInSinglePacket() throws Exception {
    codec.onDataReceived(buffer(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA));

    verifySingleMessageReceivedSuccessfully();
  }

  @Test
  public void shouldParseSingleResponseReceivedWithStatusCodeSeparateToMessageData()
      throws Exception {
    codec.onDataReceived(buffer(SUCCESS_CODE));
    codec.onDataReceived(buffer(LENGTH_PREFIX, MESSAGE_DATA));

    verifySingleMessageReceivedSuccessfully();
  }

  @Test
  public void shouldParseSingleResponseReceivedWithStatusCodeLengthAndMessageInDifferentPackets()
      throws Exception {
    codec.onDataReceived(buffer(SUCCESS_CODE));
    codec.onDataReceived(buffer(LENGTH_PREFIX));
    codec.onDataReceived(buffer(MESSAGE_DATA));

    verifySingleMessageReceivedSuccessfully();
  }

  @Test
  public void shouldParseSingleResponseReceivedWithLengthSplitInMultiplePackets() throws Exception {
    codec.onDataReceived(buffer(SUCCESS_CODE));
    codec.onDataReceived(buffer(LENGTH_PREFIX.slice(0, 2)));
    codec.onDataReceived(buffer(LENGTH_PREFIX.slice(2), MESSAGE_DATA));

    verifySingleMessageReceivedSuccessfully();
  }

  @Test
  public void shouldParseSingleResponseReceivedWIthDataInMultiplePackets() throws Exception {
    codec.onDataReceived(buffer(SUCCESS_CODE));
    codec.onDataReceived(buffer(LENGTH_PREFIX));
    // Split message data into a number of separate packets
    for (int i = 0; i < MESSAGE_DATA.size(); i += 5) {
      codec.onDataReceived(buffer(MESSAGE_DATA.slice(i, Math.min(MESSAGE_DATA.size() - i, 5))));
    }
    verifySingleMessageReceivedSuccessfully();
  }

  @Test
  public void shouldParseMultipleResponsesFromSinglePacket() throws Exception {
    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = PAYLOAD_ENCODER.encode(secondMessage);
    codec.onDataReceived(
        buffer(
            SUCCESS_CODE,
            LENGTH_PREFIX,
            MESSAGE_DATA,
            SUCCESS_CODE,
            getLengthPrefix(secondMessageData.size()),
            secondMessageData));

    verify(callback).accept(MESSAGE);
    verify(callback).accept(secondMessage);

    codec.close();
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void shouldParseMultipleResponsesWhereSecondMessageIsSplit() throws Exception {
    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = PAYLOAD_ENCODER.encode(secondMessage);
    codec.onDataReceived(buffer(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA, SUCCESS_CODE));
    verify(callback).accept(MESSAGE);

    codec.onDataReceived(buffer(getLengthPrefix(secondMessageData.size()), secondMessageData));
    verify(callback).accept(secondMessage);

    codec.close();
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void shouldThrowErrorIfMessagesHaveTrailingData() throws Exception {
    codec.onDataReceived(
        buffer(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA, Bytes.fromHexString("0x1234")));
    verify(callback).accept(MESSAGE);

    assertThatThrownBy(codec::close).isEqualTo(RpcException.INCORRECT_LENGTH_ERROR);
  }

  @Test
  public void shouldThrowErrorIfStatusCodeIsNotSuccess() throws Exception {
    assertThatThrownBy(
            () ->
                codec.onDataReceived(
                    buffer(ERROR_CODE, ERROR_MESSAGE_LENGTH_PREFIX, ERROR_MESSAGE_DATA)))
        .isEqualTo(new RpcException(ERROR_CODE.get(0), ERROR_MESSAGE));
    codec.close();
  }

  @Test
  public void shouldNotThrowErrorFromCloseAfterErrorAlreadyThrown() throws Exception {
    assertThatThrownBy(
            () ->
                codec.onDataReceived(
                    buffer(
                        ERROR_CODE,
                        ERROR_MESSAGE_LENGTH_PREFIX,
                        ERROR_MESSAGE_DATA,
                        Bytes.fromHexString("0x1234"))))
        .isInstanceOf(RpcException.class);

    // Should not throw an exception despite the trailing data.
    codec.close();
  }

  @Test
  public void shouldReleaseByteBufsFromParsedMessages() throws Exception {
    final ByteBuf packet1 = buffer(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA);
    codec.onDataReceived(packet1);
    verify(callback).accept(MESSAGE);
    // Should have released first packet buffer as all data has been processed.
    assertThat(packet1.refCnt()).isEqualTo(1);

    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = PAYLOAD_ENCODER.encode(secondMessage);
    codec.onDataReceived(
        buffer(SUCCESS_CODE, getLengthPrefix(secondMessageData.size()), secondMessageData));
    verify(callback).accept(secondMessage);

    codec.close();
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void shouldNotReleaseByteBufsThatHaveTrailingData() throws Exception {
    final ByteBuf packet1 = buffer(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA, SUCCESS_CODE);
    codec.onDataReceived(packet1);
    verify(callback).accept(MESSAGE);
    // Should not release buffer as status code of next response hasn't been handled yet.
    assertThat(packet1.refCnt()).isEqualTo(2);

    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = PAYLOAD_ENCODER.encode(secondMessage);
    codec.onDataReceived(buffer(getLengthPrefix(secondMessageData.size()), secondMessageData));
    verify(callback).accept(secondMessage);

    codec.close();
    verifyNoMoreInteractions(callback);
  }

  private void verifySingleMessageReceivedSuccessfully() throws Exception {
    verify(callback).accept(MESSAGE);

    codec.close();
    verifyNoMoreInteractions(callback);
  }
}
