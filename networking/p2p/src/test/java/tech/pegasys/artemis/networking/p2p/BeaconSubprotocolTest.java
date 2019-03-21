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

package tech.pegasys.artemis.networking.p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.consensys.cava.rlpx.wire.SubProtocolIdentifier;
import org.junit.jupiter.api.Test;

final class BeaconSubprotocolTest {

  @Test
  void testId() {
    BeaconSubprotocol subprotocol = new BeaconSubprotocol();
    assertEquals("bea", subprotocol.id().name());
    assertEquals(1, subprotocol.id().version());
  }

  @Test
  void supportsCheck() {
    BeaconSubprotocol subprotocol = new BeaconSubprotocol();
    assertTrue(subprotocol.supports(SubProtocolIdentifier.of("bea", 1)));
  }
}
