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

import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;

class PendingAttestationTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Bitlist participationBitfield = dataStructureUtil.randomBitlist();
  private AttestationData data = dataStructureUtil.randomAttestationData();
  private UnsignedLong inclusionDelay = dataStructureUtil.randomUnsignedLong();
  private UnsignedLong proposerIndex = dataStructureUtil.randomUnsignedLong();

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
    AttestationData otherAttestationData = dataStructureUtil.randomAttestationData();
    while (Objects.equals(otherAttestationData, data)) {
      otherAttestationData = dataStructureUtil.randomAttestationData();
    }
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            participationBitfield, otherAttestationData, inclusionDelay, proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenParticipationBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            dataStructureUtil.randomBitlist(), data, inclusionDelay, proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenCustodyBitfieldsAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            participationBitfield,
            data,
            inclusionDelay.plus(dataStructureUtil.randomUnsignedLong()),
            proposerIndex);

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndicesAreDifferent() {
    PendingAttestation testPendingAttestation =
        new PendingAttestation(
            participationBitfield,
            data,
            inclusionDelay,
            proposerIndex.plus(dataStructureUtil.randomUnsignedLong()));

    assertNotEquals(pendingAttestation, testPendingAttestation);
  }
}
