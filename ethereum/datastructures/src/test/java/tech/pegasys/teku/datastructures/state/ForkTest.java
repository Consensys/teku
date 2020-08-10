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

package tech.pegasys.teku.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

class ForkTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Bytes4 previousVersion = new Bytes4(Bytes.of(1, 2, 3, 4));
  private Bytes4 currentVersion = new Bytes4(Bytes.of(5, 6, 7, 8));
  private UInt64 epoch = dataStructureUtil.randomUInt64();

  private Fork fork = new Fork(previousVersion, currentVersion, epoch);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Fork testFork = fork;

    assertEquals(fork, testFork);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Fork testFork = new Fork(previousVersion, currentVersion, epoch);

    assertEquals(fork, testFork);
  }

  @Test
  void equalsReturnsFalseWhenPreviousVersionsAreDifferent() {
    Fork testFork =
        new Fork(new Bytes4(previousVersion.getWrappedBytes().not()), currentVersion, epoch);

    assertNotEquals(fork, testFork);
  }

  @Test
  void equalsReturnsFalseWhenCurrentVersionsAreDifferent() {
    Fork testFork =
        new Fork(previousVersion, new Bytes4(currentVersion.getWrappedBytes().not()), epoch);

    assertNotEquals(fork, testFork);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    Fork testFork =
        new Fork(previousVersion, currentVersion, epoch.plus(dataStructureUtil.randomUInt64()));

    assertNotEquals(fork, testFork);
  }

  @Test
  void roundTripViaSsz() {
    Fork fork = dataStructureUtil.randomFork();
    Fork newFork =
        SimpleOffsetSerializer.deserialize(SimpleOffsetSerializer.serialize(fork), Fork.class);
    assertEquals(fork, newFork);
  }
}
