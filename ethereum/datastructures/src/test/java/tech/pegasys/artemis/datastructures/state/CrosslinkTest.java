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

package tech.pegasys.artemis.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class CrosslinkTest {

  UnsignedLong epoch = randomUnsignedLong();
  Bytes32 shardBlockRoot = Bytes32.random();

  private Crosslink crosslink = new Crosslink(epoch, shardBlockRoot);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Crosslink testCrosslink = crosslink;

    assertEquals(crosslink, testCrosslink);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Crosslink testCrosslink = new Crosslink(epoch, shardBlockRoot);

    assertEquals(crosslink, testCrosslink);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    Crosslink testCrosslink = new Crosslink(epoch.plus(randomUnsignedLong()), shardBlockRoot);

    assertNotEquals(crosslink, testCrosslink);
  }

  @Test
  void equalsReturnsFalseWhenShardBlockRootsAreDifferent() {
    Crosslink testCrosslink = new Crosslink(epoch, shardBlockRoot.not());

    assertNotEquals(crosslink, testCrosslink);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszCrosslinkBytes = crosslink.toBytes();
    assertEquals(crosslink, Crosslink.fromBytes(sszCrosslinkBytes));
  }
}
