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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.hobbits.Message;
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
    assertEquals(RPCMethod.GOODBYE.code(), message.method());
    Map map = message.bodyAs(Map.class);
    assertTrue(map.isEmpty());
  }

  @Test
  void testHello() {
    HelloMessage hello =
        new HelloMessage(
            (short) 1,
            (short) 1,
            Bytes32.random().toArrayUnsafe(),
            BigInteger.ZERO,
            Bytes32.random().toArrayUnsafe(),
            BigInteger.ZERO);
    Message encoded = RPCCodec.encode(RPCMethod.HELLO.code(), hello, BigInteger.TEN);
    RPCMessage message = RPCCodec.decode(encoded);
    assertEquals(RPCMethod.HELLO.code(), message.method());
    HelloMessage read = message.bodyAs(HelloMessage.class);
    assertTrue(Arrays.equals(hello.bestRoot(), read.bestRoot()));
  }
}
