/*
 * Copyright Consensys Software Inc., 2025
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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;

public class RpcRequestEncoderTest {
  private static final Bytes RECORDED_STATUS_REQUEST_BYTES =
      Bytes.fromHexString(
          "0x54ff060000734e61507059002d00007e8c1ea2540000aa01007c30a903798306695d21d1faa76363a0070677130835e503760b0e84479b7819e6114b");
  private static final StatusMessagePhase0 RECORDED_STATUS_MESSAGE_DATA =
      new StatusMessagePhase0(
          new Bytes4(Bytes.of(0, 0, 0, 0)),
          Bytes32.ZERO,
          UInt64.ZERO,
          Bytes32.fromHexString(
              "0x30A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E6"),
          UInt64.ZERO);

  private final RpcRequestEncoder requestEncoder =
      new RpcRequestEncoder(
          RpcEncoding.createSszSnappyEncoding(
              TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize()));

  @Test
  public void shouldEncodeStatusRequest() {
    final Bytes actualBytes = requestEncoder.encodeRequest(RECORDED_STATUS_MESSAGE_DATA);
    assertThat(actualBytes).isEqualTo(RECORDED_STATUS_REQUEST_BYTES);
  }
}
