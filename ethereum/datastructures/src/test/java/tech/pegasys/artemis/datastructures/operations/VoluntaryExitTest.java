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

package tech.pegasys.artemis.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.bls.BLSSignature;

class VoluntaryExitTest {

  private UnsignedLong epoch = randomUnsignedLong();
  private UnsignedLong validatorIndex = randomUnsignedLong();
  private BLSSignature signature = BLSSignature.random();

  private VoluntaryExit voluntaryExit = new VoluntaryExit(epoch, validatorIndex, signature);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    VoluntaryExit testVoluntaryExit = voluntaryExit;

    assertEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    VoluntaryExit testVoluntaryExit = new VoluntaryExit(epoch, validatorIndex, signature);

    assertEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    VoluntaryExit testVoluntaryExit =
        new VoluntaryExit(epoch.plus(randomUnsignedLong()), validatorIndex, signature);

    assertNotEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsFalseWhenValidatorIndicesAreDifferent() {
    VoluntaryExit testVoluntaryExit =
        new VoluntaryExit(epoch, validatorIndex.plus(randomUnsignedLong()), signature);

    assertNotEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsFalseWhenSignaturesAreDifferent() {
    BLSSignature differentSignature = BLSSignature.random();
    while (differentSignature.equals(signature)) {
      differentSignature = BLSSignature.random();
    }

    VoluntaryExit testVoluntaryExit = new VoluntaryExit(epoch, validatorIndex, differentSignature);

    assertNotEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszVoluntaryExitBytes = voluntaryExit.toBytes();
    assertEquals(voluntaryExit, VoluntaryExit.fromBytes(sszVoluntaryExitBytes));
  }
}
