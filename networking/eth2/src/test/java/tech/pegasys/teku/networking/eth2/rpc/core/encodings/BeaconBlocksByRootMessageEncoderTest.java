/*
 * Copyright 2020 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.BeaconBlocksByRootRequestMessageEncoder;
import tech.pegasys.teku.util.config.Constants;

public class BeaconBlocksByRootMessageEncoderTest {

  @Test
  public void shouldThrowErrorWhenMessageContainsTooManyBlockRoots() {
    Bytes message = Bytes32.ZERO;
    for (int i = 0; i < Constants.MAX_REQUEST_BLOCKS; i++) {
      message = Bytes.concatenate(message, Bytes32.ZERO);
    }

    final Bytes finalMessage = message;

    BeaconBlocksByRootRequestMessageEncoder encoder = new BeaconBlocksByRootRequestMessageEncoder();
    assertThatThrownBy(() -> encoder.decode(finalMessage))
        .isEqualTo(RpcException.TOO_MANY_BLOCKS_REQUESTED);
  }
}
