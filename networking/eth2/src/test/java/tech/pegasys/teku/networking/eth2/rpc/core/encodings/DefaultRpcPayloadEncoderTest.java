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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.DefaultRpcPayloadEncoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;

public class DefaultRpcPayloadEncoderTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DefaultRpcPayloadEncoder<StatusMessage> statusMessageEncoder =
      new DefaultRpcPayloadEncoder<>(StatusMessage.SSZ_SCHEMA);

  @Test
  public void decode_truncatedMessage() {
    final StatusMessage statusMessage = StatusMessage.createPreGenesisStatus(spec);
    final Bytes encoded = statusMessageEncoder.encode(statusMessage);

    for (int i = 0; i < encoded.size(); i++) {
      final Bytes truncated = encoded.slice(0, encoded.size() - 1);
      assertThatThrownBy(() -> statusMessageEncoder.decode(truncated))
          .isInstanceOf(DeserializationFailedException.class);
    }
  }
}
