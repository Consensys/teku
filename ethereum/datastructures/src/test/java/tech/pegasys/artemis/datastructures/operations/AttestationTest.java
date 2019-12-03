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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBitlist;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSSignature;

class AttestationTest {
  private int seed = 100;
  private Bitlist aggregationBitfield = randomBitlist(seed);
  private AttestationData data = randomAttestationData(seed++);
  private BLSSignature aggregateSignature = BLSSignature.random(seed++);

  private Attestation attestation = new Attestation(aggregationBitfield, data, aggregateSignature);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    Attestation testAttestation = attestation;

    assertEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Attestation testAttestation = new Attestation(aggregationBitfield, data, aggregateSignature);

    assertEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAggregationBitfieldsAreDifferent() {
    Attestation testAttestation = new Attestation(randomBitlist(seed++), data, aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = randomAttestationData(seed++);
    while (Objects.equals(otherData, data)) {
      otherData = randomAttestationData(seed++);
    }

    Attestation testAttestation =
        new Attestation(aggregationBitfield, otherData, aggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAggregrateSignaturesAreDifferent() {
    BLSSignature differentAggregateSignature = BLSSignature.random();
    while (differentAggregateSignature.equals(aggregateSignature)) {
      differentAggregateSignature = BLSSignature.random();
    }

    Attestation testAttestation =
        new Attestation(aggregationBitfield, data, differentAggregateSignature);

    assertNotEquals(attestation, testAttestation);
  }
}
