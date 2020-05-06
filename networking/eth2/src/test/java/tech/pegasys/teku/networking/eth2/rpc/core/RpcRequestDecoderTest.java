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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;

class RpcRequestDecoderTest extends RpcDecoderTestBase {

  private final RpcRequestDecoder<BeaconBlocksByRootRequestMessage> decoder =
      METHOD.createRequestDecoder();

  @Test
  public void shouldParseSingleResponseReceivedInSinglePacket() throws Exception {
    final BeaconBlocksByRootRequestMessage request =
        decoder.decodeRequest(inputStream(LENGTH_PREFIX, MESSAGE_DATA));

    assertThat(request).isEqualTo(MESSAGE);
  }

  @Test
  public void shouldThrowErrorIfMessagesHaveTrailingData() {
    assertThatThrownBy(
            () ->
                decoder.decodeRequest(
                    inputStream(LENGTH_PREFIX, MESSAGE_DATA, Bytes.fromHexString("0x1234"))))
        .isEqualTo(RpcException.EXTRA_DATA_APPENDED);
  }
}
