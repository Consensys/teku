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

import java.io.InputStream;
import java.util.Optional;
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
    final InputStream input = inputStream(SUCCESS_CODE, LENGTH_PREFIX, MESSAGE_DATA);
    final Optional<BeaconBlocksByRootRequestMessage> result = decoder.decodeNextResponse(input);
    assertThat(result).contains(MESSAGE);

    // Next attempt to read should be empty
    final Optional<BeaconBlocksByRootRequestMessage> result2 = decoder.decodeNextResponse(input);
    assertThat(result2).isEmpty();
  }

  @Test
  public void decodeNextResponse_shouldParseMultipleResponses() throws Exception {
    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = PAYLOAD_ENCODER.encode(secondMessage);
    final InputStream input =
        inputStream(
            SUCCESS_CODE,
            LENGTH_PREFIX,
            MESSAGE_DATA,
            SUCCESS_CODE,
            getLengthPrefix(secondMessageData.size()),
            secondMessageData);

    final Optional<BeaconBlocksByRootRequestMessage> result1 = decoder.decodeNextResponse(input);
    assertThat(result1).contains(MESSAGE);

    final Optional<BeaconBlocksByRootRequestMessage> result2 = decoder.decodeNextResponse(input);
    assertThat(result2).contains(secondMessage);

    final Optional<BeaconBlocksByRootRequestMessage> result3 = decoder.decodeNextResponse(input);
    assertThat(result3).isEmpty();
  }

  @Test
  public void decodeNextResponse_shouldThrowErrorIfStatusCodeIsNotSuccess() throws Exception {
    assertThatThrownBy(
            () ->
                decoder.decodeNextResponse(
                    inputStream(ERROR_CODE, ERROR_MESSAGE_LENGTH_PREFIX, ERROR_MESSAGE_DATA)))
        .isEqualTo(new RpcException(ERROR_CODE.get(0), ERROR_MESSAGE));
  }
}
