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
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;

class AttestationDataAndCustodyBitTest {

  private AttestationData data = randomAttestationData();
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
    AttestationData otherAttestationData = randomAttestationData();
    while (Objects.equals(otherAttestationData, data)) {
      otherAttestationData = randomAttestationData();
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
    Bytes sszAttestationDataAndCustodyBitBytes = attestationDataAndCustodyBit.toBytes();
    assertEquals(
        attestationDataAndCustodyBit,
        AttestationDataAndCustodyBit.fromBytes(sszAttestationDataAndCustodyBitBytes));
  }
}
