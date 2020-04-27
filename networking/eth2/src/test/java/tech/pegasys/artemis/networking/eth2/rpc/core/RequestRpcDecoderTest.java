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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;

class RequestRpcDecoderTest extends RpcDecoderTestBase {

  private final RequestRpcDecoder<BeaconBlocksByRootRequestMessage> codec =
      METHOD.createRequestDecoder();

  @Test
  public void shouldParseSingleResponseReceivedInSinglePacket() throws Exception {
    final Optional<BeaconBlocksByRootRequestMessage> request =
        codec.onDataReceived(buffer(LENGTH_PREFIX, MESSAGE_DATA));

    assertThat(request).contains(MESSAGE);
  }

  @Test
  public void shouldParseSingleResponseReceivedWithLengthAndMessageInDifferentPackets()
      throws Exception {
    assertThat(codec.onDataReceived(buffer(LENGTH_PREFIX))).isEmpty();
    assertThat(codec.onDataReceived(buffer(MESSAGE_DATA))).contains(MESSAGE);
  }

  @Test
  public void shouldParseSingleResponseReceivedWithLengthSplitInMultiplePackets() throws Exception {
    assertThat(codec.onDataReceived(buffer(LENGTH_PREFIX.slice(0, 2)))).isEmpty();
    assertThat(codec.onDataReceived(buffer(LENGTH_PREFIX.slice(2), MESSAGE_DATA)))
        .contains(MESSAGE);
  }

  @Test
  public void shouldParseSingleResponseReceivedWithDataInMultiplePackets() throws Exception {
    Optional<BeaconBlocksByRootRequestMessage> request = Optional.empty();
    codec.onDataReceived(buffer(LENGTH_PREFIX));
    // Split message data into a number of separate packets
    for (int i = 0; i < MESSAGE_DATA.size(); i += 5) {
      assertThat(request).isEmpty(); // Haven't sent all data, shouldn't have a message
      request =
          codec.onDataReceived(buffer(MESSAGE_DATA.slice(i, Math.min(MESSAGE_DATA.size() - i, 5))));
    }
    assertThat(request).contains(MESSAGE);
  }

  @Test
  public void shouldThrowErrorIfMessagesHaveTrailingData() {
    assertThatThrownBy(
            () ->
                codec.onDataReceived(
                    buffer(LENGTH_PREFIX, MESSAGE_DATA, Bytes.fromHexString("0x1234"))))
        .isEqualTo(RpcException.INCORRECT_LENGTH_ERROR);
    codec.close();
  }
}
