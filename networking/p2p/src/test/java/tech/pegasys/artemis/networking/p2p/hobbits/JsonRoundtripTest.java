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

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.GetStatusMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.HelloMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RPCCodec;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.RequestBlocksMessage;

class JsonRoundtripTest {

  @Test
  void roundtripGetStatus() throws Exception {
    GetStatusMessage status = new GetStatusMessage("foo", 123);
    byte[] value = RPCCodec.getMapper().writerFor(GetStatusMessage.class).writeValueAsBytes(status);
    GetStatusMessage read = RPCCodec.getMapper().readerFor(GetStatusMessage.class).readValue(value);
    assertEquals("foo", read.userAgent());
    assertEquals(123, read.timestamp());
  }

  @Test
  void roundtripHello() throws Exception {
    HelloMessage hello =
        new HelloMessage(
            1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0));
    byte[] value = RPCCodec.getMapper().writerFor(HelloMessage.class).writeValueAsBytes(hello);
    HelloMessage read = RPCCodec.getMapper().readerFor(HelloMessage.class).readValue(value);
    assertEquals(hello.bestRoot(), read.bestRoot());
    assertEquals(hello.networkId(), read.networkId());
  }

  @Test
  void roundtripRequestBlocks() throws Exception {
    RequestBlocksMessage requestBlocksMessage =
        new RequestBlocksMessage(Bytes32.random(), 123, 3, 2, 1);
    byte[] value =
        RPCCodec.getMapper()
            .writerFor(RequestBlocksMessage.class)
            .writeValueAsBytes(requestBlocksMessage);
    RequestBlocksMessage read =
        RPCCodec.getMapper().readerFor(RequestBlocksMessage.class).readValue(value);
    assertEquals(requestBlocksMessage.startRoot(), read.startRoot());
    assertEquals(requestBlocksMessage.direction(), read.direction());
  }
}
