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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class AttestationTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    AttestationData data = randomAttestationData();
    Bytes32 participationBitfield = Bytes32.random();
    Bytes32 custodyBitfield = Bytes32.random();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Attestation a1 =
        new Attestation(data, participationBitfield, custodyBitfield, aggregateSignature);
    Attestation a2 = a1;

    assertEquals(a1, a2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    AttestationData data = randomAttestationData();
    Bytes32 participationBitfield = Bytes32.random();
    Bytes32 custodyBitfield = Bytes32.random();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Attestation a1 =
        new Attestation(data, participationBitfield, custodyBitfield, aggregateSignature);
    Attestation a2 =
        new Attestation(data, participationBitfield, custodyBitfield, aggregateSignature);

    assertEquals(a1, a2);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    AttestationData data = randomAttestationData();
    Bytes32 participationBitfield = Bytes32.random();
    Bytes32 custodyBitfield = Bytes32.random();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = randomAttestationData();
    while (Objects.equals(otherData, data)) {
      otherData = randomAttestationData();
    }

    Attestation a1 =
        new Attestation(data, participationBitfield, custodyBitfield, aggregateSignature);
    Attestation a2 =
        new Attestation(otherData, participationBitfield, custodyBitfield, aggregateSignature);

    assertNotEquals(a1, a2);
  }

  @Test
  void equalsReturnsFalseWhenParticipationBitfieldsAreDifferent() {
    AttestationData data = randomAttestationData();
    Bytes32 participationBitfield = Bytes32.random();
    Bytes32 custodyBitfield = Bytes32.random();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Attestation a1 =
        new Attestation(data, participationBitfield, custodyBitfield, aggregateSignature);
    Attestation a2 =
        new Attestation(data, participationBitfield.not(), custodyBitfield, aggregateSignature);

    assertNotEquals(a1, a2);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitfieldsAreDifferent() {
    AttestationData data = randomAttestationData();
    Bytes32 participationBitfield = Bytes32.random();
    Bytes32 custodyBitfield = Bytes32.random();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Attestation a1 =
        new Attestation(data, participationBitfield, custodyBitfield, aggregateSignature);
    Attestation a2 =
        new Attestation(data, participationBitfield, custodyBitfield.not(), aggregateSignature);

    assertNotEquals(a1, a2);
  }

  @Test
  void equalsReturnsFalseWhenAggregrateSignaturesAreDifferent() {
    AttestationData data = randomAttestationData();
    Bytes32 participationBitfield = Bytes32.random();
    Bytes32 custodyBitfield = Bytes32.random();
    List<Bytes48> aggregateSignature = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of aggregateSignature and reverse to ensure it is different.
    List<Bytes48> reverseAggregateSignature = new ArrayList<Bytes48>(aggregateSignature);
    Collections.reverse(reverseAggregateSignature);

    Attestation a1 =
        new Attestation(data, participationBitfield, custodyBitfield, aggregateSignature);
    Attestation a2 =
        new Attestation(data, participationBitfield, custodyBitfield, reverseAggregateSignature);

    assertNotEquals(a1, a2);
  }

  @Test
  void rountripSSZ() {
    Attestation attestation =
        new Attestation(
            randomAttestationData(),
            Bytes32.random(),
            Bytes32.random(),
            Arrays.asList(Bytes48.random(), Bytes48.random()));
    Bytes sszAttestationBytes = attestation.toBytes();
    assertEquals(attestation, Attestation.fromBytes(sszAttestationBytes));
  }
}
