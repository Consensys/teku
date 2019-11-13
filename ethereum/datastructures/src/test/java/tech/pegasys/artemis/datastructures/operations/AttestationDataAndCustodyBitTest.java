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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttestationData;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class AttestationDataAndCustodyBitTest {

  private int seed = 100;
  private AttestationData data = randomAttestationData(seed);
  private boolean custodyBit = false;

  private AttestationDataAndCustodyBit attestationDataAndCustodyBit =
      new AttestationDataAndCustodyBit(data, custodyBit);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    AttestationDataAndCustodyBit testAttestationDataAndCustodyBit = attestationDataAndCustodyBit;

    assertEquals(attestationDataAndCustodyBit, testAttestationDataAndCustodyBit);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttestationDataAndCustodyBit testAttestationDataAndCustodyBit =
        new AttestationDataAndCustodyBit(data, custodyBit);

    assertEquals(attestationDataAndCustodyBit, testAttestationDataAndCustodyBit);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same
    // as the original.
    AttestationData otherAttestationData = randomAttestationData(seed++);
    while (Objects.equals(otherAttestationData, data)) {
      otherAttestationData = randomAttestationData(seed++);
    }
    AttestationDataAndCustodyBit testAttestationDataAndCustodyBit =
        new AttestationDataAndCustodyBit(otherAttestationData, custodyBit);

    assertNotEquals(attestationDataAndCustodyBit, testAttestationDataAndCustodyBit);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitIsDifferent() {
    AttestationDataAndCustodyBit testAttestationDataAndCustodyBit =
        new AttestationDataAndCustodyBit(data, !custodyBit);

    assertNotEquals(attestationDataAndCustodyBit, testAttestationDataAndCustodyBit);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszAttestationDataAndCustodyBitBytes =
        SimpleOffsetSerializer.serialize(attestationDataAndCustodyBit);
    assertEquals(
        attestationDataAndCustodyBit,
        SimpleOffsetSerializer.deserialize(
            sszAttestationDataAndCustodyBitBytes, AttestationDataAndCustodyBit.class));
  }
}
