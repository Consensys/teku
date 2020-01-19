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

package tech.pegasys.artemis.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBeaconBlockBody;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class BeaconBlockTest {

  private UnsignedLong slot = randomUnsignedLong(100);
  private Bytes32 previous_root = Bytes32.random(new Random(100));
  private Bytes32 state_root = Bytes32.random(new Random(101));
  private BeaconBlockBody body = randomBeaconBlockBody(100);

  private BeaconBlock beaconBlock = new BeaconBlock(slot, previous_root, state_root, body);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    BeaconBlock testBeaconBlock = beaconBlock;

    assertEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlock testBeaconBlock = new BeaconBlock(slot, previous_root, state_root, body);

    assertEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(slot.plus(UnsignedLong.ONE), previous_root, state_root, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenParentRootsAreDifferent() {
    BeaconBlock testBeaconBlock = new BeaconBlock(slot, previous_root.not(), state_root, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenStateRootsAreDifferent() {
    BeaconBlock testBeaconBlock = new BeaconBlock(slot, previous_root, state_root.not(), body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenBeaconBlockBodiesAreDifferent() {
    // BeaconBlockBody is rather involved to create. Just create a random one until it is not the
    // same
    // as the original.
    BeaconBlockBody otherBody = randomBeaconBlockBody(100);
    while (Objects.equals(otherBody, body)) {
      otherBody = randomBeaconBlockBody(101);
    }

    BeaconBlock testBeaconBlock = new BeaconBlock(slot, previous_root, state_root, otherBody);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void roundtripSSZ() {
    final Bytes ssz = SimpleOffsetSerializer.serialize(beaconBlock);
    final BeaconBlock result = SimpleOffsetSerializer.deserialize(ssz, BeaconBlock.class);
    assertThat(result).isEqualTo(beaconBlock);
  }
}
