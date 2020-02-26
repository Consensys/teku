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

package tech.pegasys.artemis.networking.p2p.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class DiscoveryNodeIdTest {
  public static final Bytes NODE_ID_BYTES =
      Bytes.fromHexString("0x833246C198388F1B5E06EF1950B0A6705FBF6370E002656CDA2C6C803C06258D");

  private final DiscoveryNodeId id = new DiscoveryNodeId(NODE_ID_BYTES);

  @Test
  public void shouldSerializeToBytesCorrectly() {
    assertThat(id.toBytes()).isEqualTo(NODE_ID_BYTES);
  }

  @Test
  public void shouldSerializeToBase58Correctly() {
    assertThat(id.toBase58()).isEqualTo("9q8se7jAA4ngArgS5U1kyyYHrdfdLcmfjebUFvkQpHVE");
  }
}
