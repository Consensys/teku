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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomLong;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class ForkTest {

  private long previousVersion = randomLong();
  private long currentVersion = randomLong();
  private long epoch = randomLong();

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
    Fork testFork = new Fork(previousVersion + randomLong(), currentVersion, epoch);

    assertNotEquals(fork, testFork);
  }

  @Test
  void equalsReturnsFalseWhenCurrentVersionsAreDifferent() {
    Fork testFork = new Fork(previousVersion, currentVersion + randomLong(), epoch);

    assertNotEquals(fork, testFork);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    Fork testFork = new Fork(previousVersion, currentVersion, epoch + randomLong());

    assertNotEquals(fork, testFork);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszForkBytes = fork.toBytes();
    assertEquals(fork, Fork.fromBytes(sszForkBytes));
  }
}
