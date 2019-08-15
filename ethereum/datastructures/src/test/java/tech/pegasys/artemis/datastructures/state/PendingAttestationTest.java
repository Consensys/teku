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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttestationData;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.AttestationData;

class PendingAttestationTest {

  private Bytes participationBitfield = Bytes32.random();
  private AttestationData data = randomAttestationData();
  private UnsignedLong inclusionDelay = randomUnsignedLong();
  private UnsignedLong proposerIndex = randomUnsignedLong();

  private PendingAttestation pendingAttestation =
      new PendingAttestation(participationBitfield, data, inclusionDelay, proposerIndex);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    PendingAttestation testPendingAttestation = pendingAttestation;

    assertEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(participationBitfield, data, inclusionDelay, proposerIndex);

    assertEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenAttestationDataIsDifferent() {
    // BeaconBlock is rather involved to create. Just create a random one until it is not the same
    // as the original.
    AttestationData otherAttestationData = randomAttestationData();
    while (Objects.equals(otherAttestationData, data)) {
      otherAttestationData = randomAttestationData();
    }
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            participationBitfield, otherAttestationData, inclusionDelay, proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenParticipationBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(participationBitfield.not(), data, inclusionDelay, proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            participationBitfield, data, inclusionDelay.plus(randomUnsignedLong()), proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndicesAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            participationBitfield, data, inclusionDelay, proposerIndex.plus(randomUnsignedLong()));

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszPendingAttestationBytes = pendingAttestation.toBytes();
    assertEquals(pendingAttestation, PendingAttestation.fromBytes(sszPendingAttestationBytes));
  }

  @Test
  void roundtripSSZVariableLengthBitfield() {
    PendingAttestation byte1BitfieldPendingAttestation =
        new PendingAttestation(Bytes.fromHexString("0x00"), data, inclusionDelay, proposerIndex);
    PendingAttestation byte4BitfieldPendingAttestation =
        new PendingAttestation(
            Bytes.fromHexString("0x00000000"), data, inclusionDelay, proposerIndex);
    PendingAttestation byte8BitfieldPendingAttestation =
        new PendingAttestation(
            Bytes.fromHexString("0x0000000000000000"), data, inclusionDelay, proposerIndex);
    PendingAttestation byte16BitfieldPendingAttestation =
        new PendingAttestation(
            Bytes.fromHexString("0x00000000000000000000000000000000"),
            data,
            inclusionDelay,
            proposerIndex);
    Bytes byte1BitfieldPendingAttestationBytes = byte1BitfieldPendingAttestation.toBytes();
    Bytes byte4BitfieldPendingAttestationBytes = byte4BitfieldPendingAttestation.toBytes();
    Bytes byte8BitfieldPendingAttestationBytes = byte8BitfieldPendingAttestation.toBytes();
    Bytes byte16BitfieldPendingAttestationBytes = byte16BitfieldPendingAttestation.toBytes();
    assertEquals(
        byte1BitfieldPendingAttestation,
        PendingAttestation.fromBytes(byte1BitfieldPendingAttestationBytes));
    assertEquals(
        byte4BitfieldPendingAttestation,
        PendingAttestation.fromBytes(byte4BitfieldPendingAttestationBytes));
    assertEquals(
        byte8BitfieldPendingAttestation,
        PendingAttestation.fromBytes(byte8BitfieldPendingAttestationBytes));
    assertEquals(
        byte16BitfieldPendingAttestation,
        PendingAttestation.fromBytes(byte16BitfieldPendingAttestationBytes));
  }

}
