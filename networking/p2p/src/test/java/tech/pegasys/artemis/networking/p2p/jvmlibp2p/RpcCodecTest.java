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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.RpcCodec;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

final class RpcCodecTest {

  @Test
  void testHelloRoundtripSerialization() {
    StatusMessage hello =
        new StatusMessage(
            Bytes4.rightPad(Bytes.of(4)),
            Bytes32.random(),
            UnsignedLong.ZERO,
            Bytes32.random(),
            UnsignedLong.ZERO);

    Bytes encoded = RpcCodec.encode(hello);
    StatusMessage message = RpcCodec.decode(encoded, StatusMessage.class);

    assertEquals(hello, message);
  }
}
