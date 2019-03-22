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
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;

final class RPCCodecTest {

  @Test
  @SuppressWarnings("rawtypes")
  void testGoodbye() {
    Bytes goodbye = RPCCodec.createGoodbye();
    RPCMessage message = RPCCodec.decode(goodbye);
    assertEquals(RPCMethod.GOODBYE, message.method());
    Map map = message.bodyAs(Map.class);
    assertTrue(map.isEmpty());
  }

  @Test
  void testHello() {}
}
