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

import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.units.bigints.UInt64;
import org.junit.jupiter.api.Test;

class JsonRoundtripTest {

  @Test
  void roundtripGetStatus() throws Exception {
    GetStatus status = new GetStatus("foo", 123);
    byte[] value = RPCCodec.mapper.writerFor(GetStatus.class).writeValueAsBytes(status);
    GetStatus read = RPCCodec.mapper.readerFor(GetStatus.class).readValue(value);
    assertEquals("foo", read.userAgent());
    assertEquals(123, read.timestamp());
  }

  @Test
  void roundtripHello() throws Exception {
    Hello hello =
        new Hello(1, 1, Bytes32.random(), UInt64.valueOf(0), Bytes32.random(), UInt64.valueOf(0));
    byte[] value = RPCCodec.mapper.writerFor(Hello.class).writeValueAsBytes(hello);
    Hello read = RPCCodec.mapper.readerFor(Hello.class).readValue(value);
    assertEquals(hello.bestRoot(), read.bestRoot());
    assertEquals(hello.networkId(), read.networkId());
  }
}
