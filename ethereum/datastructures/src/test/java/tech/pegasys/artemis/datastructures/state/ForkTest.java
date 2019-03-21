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
import org.junit.jupiter.api.Test;

class ForkTest {

  private UnsignedLong previousVersion = randomUnsignedLong();
  private UnsignedLong currentVersion = randomUnsignedLong();
  private UnsignedLong epoch = randomUnsignedLong();

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
    Fork testFork = new Fork(previousVersion.plus(randomUnsignedLong()), currentVersion, epoch);

    assertNotEquals(fork, testFork);
  }

  @Test
  void equalsReturnsFalseWhenCurrentVersionsAreDifferent() {
    Fork testFork = new Fork(previousVersion, currentVersion.plus(randomUnsignedLong()), epoch);

    assertNotEquals(fork, testFork);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    Fork testFork = new Fork(previousVersion, currentVersion, epoch.plus(randomUnsignedLong()));

    assertNotEquals(fork, testFork);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszForkBytes = fork.toBytes();
    assertEquals(fork, Fork.fromBytes(sszForkBytes));
  }
}
