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

package tech.pegasys.artemis.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttestation;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttesterSlashing;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposit;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomProposerSlashing;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomTransfer;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomVoluntaryExit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;

class BeaconBlockBodyTest {

  private List<ProposerSlashing> proposerSlashings =
      Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
  private List<AttesterSlashing> attesterSlashings =
      Arrays.asList(randomAttesterSlashing(), randomAttesterSlashing(), randomAttesterSlashing());
  private List<Attestation> attestations =
      Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
  private List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
  private List<VoluntaryExit> voluntaryExits =
      Arrays.asList(randomVoluntaryExit(), randomVoluntaryExit(), randomVoluntaryExit());
  private List<Transfer> transfers = Arrays.asList(randomTransfer(), randomTransfer());

  private BeaconBlockBody beaconBlockBody =
      new BeaconBlockBody(
          proposerSlashings, attesterSlashings, attestations, deposits, voluntaryExits, transfers);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    BeaconBlockBody testBeaconBlockBody = beaconBlockBody;

    assertEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits,
            transfers);

    assertEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenProposerSlashingsAreDifferent() {
    // Create copy of proposerSlashings and reverse to ensure it is different.
    List<ProposerSlashing> reverseProposerSlashings = new ArrayList<>(proposerSlashings);
    Collections.reverse(reverseProposerSlashings);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            reverseProposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits,
            transfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttesterSlashingsAreDifferent() {
    // Create copy of attesterSlashings and reverse to ensure it is different.
    List<AttesterSlashing> reverseAttesterSlashings = new ArrayList<>(attesterSlashings);
    Collections.reverse(reverseAttesterSlashings);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings,
            reverseAttesterSlashings,
            attestations,
            deposits,
            voluntaryExits,
            transfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttestationsAreDifferent() {
    // Create copy of attestations and reverse to ensure it is different.
    List<Attestation> reverseAttestations = new ArrayList<>(attestations);
    Collections.reverse(reverseAttestations);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings,
            attesterSlashings,
            reverseAttestations,
            deposits,
            voluntaryExits,
            transfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenDepositsAreDifferent() {
    // Create copy of deposits and reverse to ensure it is different.
    List<Deposit> reverseDeposits = new ArrayList<>(deposits);
    Collections.reverse(reverseDeposits);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings,
            attesterSlashings,
            attestations,
            reverseDeposits,
            voluntaryExits,
            transfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenExitsAreDifferent() {
    // Create copy of exits and reverse to ensure it is different.
    List<VoluntaryExit> reverseVoluntaryExits = new ArrayList<VoluntaryExit>(voluntaryExits);
    Collections.reverse(reverseVoluntaryExits);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            reverseVoluntaryExits,
            transfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenTransfersAreDifferent() {
    // Create copy of exits and reverse to ensure it is different.
    List<Transfer> reverseTransfers = new ArrayList<Transfer>(transfers);
    Collections.reverse(reverseTransfers);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits,
            reverseTransfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszBeaconBlockBodyBytes = beaconBlockBody.toBytes();
    assertEquals(beaconBlockBody, BeaconBlockBody.fromBytes(sszBeaconBlockBodyBytes));
  }
}
