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

package tech.pegasys.teku.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;

class VoluntaryExitTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private UnsignedLong epoch = dataStructureUtil.randomUnsignedLong();
  private UnsignedLong validatorIndex = dataStructureUtil.randomUnsignedLong();

  private VoluntaryExit voluntaryExit = new VoluntaryExit(epoch, validatorIndex);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    VoluntaryExit testVoluntaryExit = voluntaryExit;

    assertEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    VoluntaryExit testVoluntaryExit = new VoluntaryExit(epoch, validatorIndex);

    assertEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    VoluntaryExit testVoluntaryExit =
        new VoluntaryExit(epoch.plus(dataStructureUtil.randomUnsignedLong()), validatorIndex);

    assertNotEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsFalseWhenValidatorIndicesAreDifferent() {
    VoluntaryExit testVoluntaryExit =
        new VoluntaryExit(epoch, validatorIndex.plus(dataStructureUtil.randomUnsignedLong()));

    assertNotEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszVoluntaryExitBytes = SimpleOffsetSerializer.serialize(voluntaryExit);
    assertEquals(
        voluntaryExit,
        SimpleOffsetSerializer.deserialize(sszVoluntaryExitBytes, VoluntaryExit.class));
  }
}
