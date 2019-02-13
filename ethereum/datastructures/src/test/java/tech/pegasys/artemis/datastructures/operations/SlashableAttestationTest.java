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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class SlashableAttestationTest {

  private List<UnsignedLong> validatorIndices =
      Arrays.asList(randomUnsignedLong(), randomUnsignedLong(), randomUnsignedLong());
  private AttestationData data = randomAttestationData();
  private Bytes32 custodyBitfield = Bytes32.random();
  private BLSSignature aggregateSignature = new BLSSignature(Bytes48.random(), Bytes48.random());

  private SlashableAttestation slashableAttestation =
      new SlashableAttestation(validatorIndices, data, custodyBitfield, aggregateSignature);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    SlashableAttestation testSlashableAttestation = slashableAttestation;

    assertEquals(slashableAttestation, testSlashableAttestation);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    SlashableAttestation testSlashableAttestation =
        new SlashableAttestation(validatorIndices, data, custodyBitfield, aggregateSignature);

    assertEquals(slashableAttestation, testSlashableAttestation);
  }

  @Test
  void equalsReturnsFalseWhenValidatorIndicesAreDifferent() {
    // Create copy of validatorIndices and reverse to ensure it is different.
    List<UnsignedLong> reverseValidatorIndices = new ArrayList<>(validatorIndices);
    Collections.reverse(reverseValidatorIndices);

    SlashableAttestation testSlashableAttestation =
        new SlashableAttestation(
            reverseValidatorIndices, data, custodyBitfield, aggregateSignature);

    assertNotEquals(slashableAttestation, testSlashableAttestation);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitfieldIsDifferent() {
    Bytes32 otherCustodyBitfield = custodyBitfield.and(Bytes32.random());

    SlashableAttestation testSlashableAttestation =
        new SlashableAttestation(validatorIndices, data, otherCustodyBitfield, aggregateSignature);

    assertNotEquals(slashableAttestation, testSlashableAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // AttestationData is rather involved to create. Just create a random one until it is not the
    // same as the original.
    AttestationData otherData = randomAttestationData();
    while (Objects.equals(otherData, data)) {
      otherData = randomAttestationData();
    }

    SlashableAttestation testSlashableAttestation =
        new SlashableAttestation(validatorIndices, otherData, custodyBitfield, aggregateSignature);

    assertNotEquals(slashableAttestation, testSlashableAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAggregrateSignaturesAreDifferent() {
    BLSSignature reverseAggregateSignature =
        new BLSSignature(aggregateSignature.getC1(), aggregateSignature.getC0());

    SlashableAttestation testSlashableAttestation =
        new SlashableAttestation(
            validatorIndices, data, custodyBitfield, reverseAggregateSignature);

    assertNotEquals(slashableAttestation, testSlashableAttestation);
  }

  @Test
  void rountripSSZ() {
    Bytes sszSlashableVoteDataBytes = slashableAttestation.toBytes();
    assertEquals(slashableAttestation, SlashableAttestation.fromBytes(sszSlashableVoteDataBytes));
  }
}
