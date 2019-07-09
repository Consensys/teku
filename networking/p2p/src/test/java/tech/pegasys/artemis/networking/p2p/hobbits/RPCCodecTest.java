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

package tech.pegasys.artemis.networking.p2p.hobbits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.hobbits.Message;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.HelloMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RPCCodec;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RPCMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RPCMethod;

final class RPCCodecTest {

  @Test
  @SuppressWarnings("rawtypes")
  void testGoodbye() {
    Message goodbye = RPCCodec.createGoodbye();
    RPCMessage message = RPCCodec.decode(goodbye);
    assertEquals(RPCMethod.GOODBYE, message.method());
    Map map = message.bodyAs(Map.class);
    assertTrue(map.isEmpty());
  }

  @Test
  void testHello() {
    HelloMessage hello =
        new HelloMessage(
            1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0));
    Message encoded = RPCCodec.encode(RPCMethod.HELLO, hello, 23);
    RPCMessage message = RPCCodec.decode(encoded);
    assertEquals(RPCMethod.HELLO, message.method());
    HelloMessage read = message.bodyAs(HelloMessage.class);
    assertEquals(read.bestRoot(), hello.bestRoot());
  }
}
