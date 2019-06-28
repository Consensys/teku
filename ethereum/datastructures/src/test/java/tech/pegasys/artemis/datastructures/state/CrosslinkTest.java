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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class CrosslinkTest {

  UnsignedLong shard = randomUnsignedLong();
  UnsignedLong start_epoch = randomUnsignedLong();
  UnsignedLong end_epoch = randomUnsignedLong();
  Bytes32 parentRoot = Bytes32.random();
  Bytes32 dataRoot = Bytes32.random();

  private Crosslink crosslink = new Crosslink(shard, start_epoch, end_epoch, parentRoot, dataRoot);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Crosslink testCrosslink = crosslink;

    assertEquals(crosslink, testCrosslink);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Crosslink testCrosslink = new Crosslink(shard, start_epoch, end_epoch, parentRoot, dataRoot);

    assertEquals(crosslink, testCrosslink);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    Crosslink testCrosslink =
        new Crosslink(
            shard, start_epoch.plus(randomUnsignedLong()), end_epoch, parentRoot, dataRoot);

    assertNotEquals(crosslink, testCrosslink);
  }

  @Test
  void equalsReturnsFalseWhenCrosslinkDataRootsAreDifferent() {
    Crosslink testCrosslink =
        new Crosslink(shard, start_epoch, end_epoch, parentRoot, dataRoot.not());

    assertNotEquals(crosslink, testCrosslink);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszCrosslinkBytes = crosslink.toBytes();
    assertEquals(crosslink, Crosslink.fromBytes(sszCrosslinkBytes));
  }
}
