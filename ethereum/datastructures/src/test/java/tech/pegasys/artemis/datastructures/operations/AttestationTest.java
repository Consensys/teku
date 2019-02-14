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
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class AttestationTest {

  private Bytes32 aggregationBitfield = Bytes32.random();
  private AttestationData data = randomAttestationData();
  private Bytes32 custodyBitfield = Bytes32.random();
  private BLSSignature aggregateSignature = new BLSSignature(Bytes48.random(), Bytes48.random());

  private Attestation attestation =
      new Attestation(aggregationBitfield, data, custodyBitfield, aggregateSignature);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    Attestation testAttestation = attestation;

    assertEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Attestation testAttestation =
        new Attestation(aggregationBitfield, data, custodyBitfield, aggregateSignature);

    assertEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAggregationBitfieldsAreDifferent() {
    Attestation testAttestation =
        new Attestation(aggregationBitfield.not(), data, custodyBitfield, aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = randomAttestationData();
    while (Objects.equals(otherData, data)) {
      otherData = randomAttestationData();
    }

    Attestation testAttestation =
        new Attestation(aggregationBitfield, otherData, custodyBitfield, aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitfieldsAreDifferent() {
    Attestation testAttestation =
        new Attestation(aggregationBitfield, data, custodyBitfield.not(), aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAggregrateSignaturesAreDifferent() {
    // Create copy of aggregateSignature and reverse to ensure it is different.
    BLSSignature reverseAggregateSignature =
        new BLSSignature(aggregateSignature.getC1(), aggregateSignature.getC0());

    Attestation testAttestation =
        new Attestation(aggregationBitfield, data, custodyBitfield, reverseAggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void rountripSSZ() {
    Bytes sszAttestationBytes = attestation.toBytes();
    assertEquals(attestation, Attestation.fromBytes(sszAttestationBytes));
  }
}
