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
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.AttestationData;

class PendingAttestationTest {

  private Bytes participationBitfield = Bytes32.random();
  private AttestationData data = randomAttestationData();
  private Bytes custodyBitfield = Bytes32.random();
  private UnsignedLong slotIncluded = randomUnsignedLong();

  private PendingAttestation pendingAttestation =
      new PendingAttestation(participationBitfield, data, custodyBitfield, slotIncluded);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    PendingAttestation testPendingAttestation = pendingAttestation;

    assertEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(participationBitfield, data, custodyBitfield, slotIncluded);

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
            participationBitfield, otherAttestationData, custodyBitfield, slotIncluded);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenParticipationBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(participationBitfield.not(), data, custodyBitfield, slotIncluded);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(participationBitfield, data, custodyBitfield.not(), slotIncluded);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenSlotIncludedsAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            participationBitfield, data, custodyBitfield, slotIncluded.plus(randomUnsignedLong()));

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void rountripSSZ() {
    Bytes sszPendingAttestationBytes = pendingAttestation.toBytes();
    assertEquals(pendingAttestation, PendingAttestation.fromBytes(sszPendingAttestationBytes));
  }

  @Test
  void rountripSSZVariableLengthBitfield() {
    PendingAttestation byte1BitfieldPendingAttestation =
        new PendingAttestation(
            Bytes.fromHexString("0x00"), data, Bytes.fromHexString("0x00"), slotIncluded);
    PendingAttestation byte4BitfieldPendingAttestation =
        new PendingAttestation(
            Bytes.fromHexString("0x00000000"),
            data,
            Bytes.fromHexString("0x00000000"),
            slotIncluded);
    PendingAttestation byte8BitfieldPendingAttestation =
        new PendingAttestation(
            Bytes.fromHexString("0x0000000000000000"),
            data,
            Bytes.fromHexString("0x0000000000000000"),
            slotIncluded);
    PendingAttestation byte16BitfieldPendingAttestation =
        new PendingAttestation(
            Bytes.fromHexString("0x00000000000000000000000000000000"),
            data,
            Bytes.fromHexString("0x00000000000000000000000000000000"),
            slotIncluded);
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
