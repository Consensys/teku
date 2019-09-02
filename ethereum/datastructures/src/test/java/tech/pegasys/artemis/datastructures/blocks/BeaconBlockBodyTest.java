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
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_ATTESTER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_PROPOSER_SLASHINGS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_TRANSFERS;
import static tech.pegasys.artemis.datastructures.Constants.MAX_VOLUNTARY_EXITS;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttestation;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomAttesterSlashing;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomEth1Data;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomProposerSlashing;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomVoluntaryExit;

import java.util.Collections;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSSignature;

class BeaconBlockBodyTest {

  private BLSSignature blsSignature = BLSSignature.random();
  private Eth1Data eth1Data = randomEth1Data();
  private Bytes32 graffiti = randomBytes32();
  private SSZList<ProposerSlashing> proposerSlashings =
      new SSZList<>(ProposerSlashing.class, MAX_PROPOSER_SLASHINGS);
  private SSZList<AttesterSlashing> attesterSlashings =
      new SSZList<>(AttesterSlashing.class, MAX_ATTESTER_SLASHINGS);
  private SSZList<Attestation> attestations = new SSZList<>(Attestation.class, MAX_ATTESTATIONS);
  private SSZList<Deposit> deposits = new SSZList<>(Deposit.class, MAX_DEPOSITS);
  private SSZList<VoluntaryExit> voluntaryExits =
      new SSZList<>(VoluntaryExit.class, MAX_VOLUNTARY_EXITS);
  private SSZList<Transfer> transfers = new SSZList<>(Transfer.class, MAX_TRANSFERS);

  {
    proposerSlashings.add(randomProposerSlashing());
    proposerSlashings.add(randomProposerSlashing());
    proposerSlashings.add(randomProposerSlashing());
    attesterSlashings.add(randomAttesterSlashing());
    attestations.add(randomAttestation());
    attestations.add(randomAttestation());
    attestations.add(randomAttestation());
    deposits.addAll(randomDeposits(100));
    voluntaryExits.add(randomVoluntaryExit());
    voluntaryExits.add(randomVoluntaryExit());
    voluntaryExits.add(randomVoluntaryExit());
  }

  private BeaconBlockBody beaconBlockBody =
      new BeaconBlockBody(
          blsSignature,
          eth1Data,
          graffiti,
          proposerSlashings,
          attesterSlashings,
          attestations,
          deposits,
          voluntaryExits,
          transfers);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    BeaconBlockBody testBeaconBlockBody = beaconBlockBody;

    assertEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
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
    SSZList<ProposerSlashing> reverseProposerSlashings =
        new SSZList<>(proposerSlashings, MAX_PROPOSER_SLASHINGS, ProposerSlashing.class);
    Collections.reverse(reverseProposerSlashings);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
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
    // Create copy of attesterSlashings and change the element to ensure it is different.
    SSZList<AttesterSlashing> otherAttesterSlashings =
        new SSZList<>(attesterSlashings, MAX_ATTESTER_SLASHINGS, AttesterSlashing.class);
    otherAttesterSlashings.add(0, randomAttesterSlashing());

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            proposerSlashings,
            otherAttesterSlashings,
            attestations,
            deposits,
            voluntaryExits,
            transfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttestationsAreDifferent() {
    // Create copy of attestations and reverse to ensure it is different.
    SSZList<Attestation> reverseAttestations =
        new SSZList<>(attestations, MAX_ATTESTATIONS, Attestation.class);
    Collections.reverse(reverseAttestations);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
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
    SSZList<Deposit> reverseDeposits = new SSZList<>(deposits, MAX_DEPOSITS, Deposit.class);
    Collections.reverse(reverseDeposits);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
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
    SSZList<VoluntaryExit> reverseVoluntaryExits =
        new SSZList<>(voluntaryExits, MAX_VOLUNTARY_EXITS, VoluntaryExit.class);
    Collections.reverse(reverseVoluntaryExits);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            reverseVoluntaryExits,
            transfers);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Disabled
  // TODO This is disabled because MAX_TRANSFERS is 0, meaning the list is always empty.
  // This is expected for now and should be reevaluated in future Phases.
  @Test
  void equalsReturnsFalseWhenTransfersAreDifferent() {
    // Create copy of exits and reverse to ensure it is different.
    SSZList<Transfer> reverseTransfers = new SSZList<>(transfers, MAX_TRANSFERS, Transfer.class);
    Collections.reverse(reverseTransfers);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
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
    // todo
    // Bytes sszBeaconBlockBodyBytes = beaconBlockBody.toBytes();
    // assertEquals(beaconBlockBody, BeaconBlockBody.fromBytes(sszBeaconBlockBodyBytes));
  }
}
