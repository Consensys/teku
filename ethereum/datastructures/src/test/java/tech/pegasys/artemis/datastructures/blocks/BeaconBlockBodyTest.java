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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomBytes32;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposits;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomEth1Data;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomProposerSlashing;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomSignedVoluntaryExit;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTER_SLASHINGS;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.util.config.Constants.MAX_PROPOSER_SLASHINGS;
import static tech.pegasys.artemis.util.config.Constants.MAX_VOLUNTARY_EXITS;

import java.util.Collections;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSSignature;

class BeaconBlockBodyTest {
  private int seed = 100;
  private BLSSignature blsSignature = BLSSignature.random(seed);
  private Eth1Data eth1Data = randomEth1Data(seed++);
  private Bytes32 graffiti = randomBytes32(seed++);
  private SSZList<ProposerSlashing> proposerSlashings =
      SSZList.create(ProposerSlashing.class, MAX_PROPOSER_SLASHINGS);
  private SSZList<AttesterSlashing> attesterSlashings =
      SSZList.create(AttesterSlashing.class, MAX_ATTESTER_SLASHINGS);
  private SSZList<Attestation> attestations = SSZList.create(Attestation.class, MAX_ATTESTATIONS);
  private SSZList<Deposit> deposits = SSZList.create(Deposit.class, MAX_DEPOSITS);
  private SSZList<SignedVoluntaryExit> voluntaryExits =
      SSZList.create(SignedVoluntaryExit.class, MAX_VOLUNTARY_EXITS);

  {
    proposerSlashings.add(randomProposerSlashing(seed++));
    proposerSlashings.add(randomProposerSlashing(seed++));
    proposerSlashings.add(randomProposerSlashing(seed++));
    attesterSlashings.add(randomAttesterSlashing(seed++));
    attestations.add(randomAttestation(seed++));
    attestations.add(randomAttestation(seed++));
    attestations.add(randomAttestation(seed++));
    deposits.addAll(randomDeposits(100, seed++));
    voluntaryExits.add(randomSignedVoluntaryExit(seed++));
    voluntaryExits.add(randomSignedVoluntaryExit(seed++));
    voluntaryExits.add(randomSignedVoluntaryExit(seed++));
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
          voluntaryExits);

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
            voluntaryExits);

    assertEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenProposerSlashingsAreDifferent() {
    // Create copy of proposerSlashings and reverse to ensure it is different.
    SSZList<ProposerSlashing> reverseProposerSlashings =
        SSZList.create(proposerSlashings, MAX_PROPOSER_SLASHINGS, ProposerSlashing.class);
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
            voluntaryExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttesterSlashingsAreDifferent() {
    // Create copy of attesterSlashings and change the element to ensure it is different.
    SSZList<AttesterSlashing> otherAttesterSlashings =
        SSZList.create(attesterSlashings, MAX_ATTESTER_SLASHINGS, AttesterSlashing.class);
    otherAttesterSlashings.add(0, randomAttesterSlashing(seed++));

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            proposerSlashings,
            otherAttesterSlashings,
            attestations,
            deposits,
            voluntaryExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttestationsAreDifferent() {
    // Create copy of attestations and reverse to ensure it is different.
    SSZList<Attestation> reverseAttestations =
        SSZList.create(attestations, MAX_ATTESTATIONS, Attestation.class);
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
            voluntaryExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenDepositsAreDifferent() {
    // Create copy of deposits and reverse to ensure it is different.
    SSZList<Deposit> reverseDeposits = SSZList.create(deposits, MAX_DEPOSITS, Deposit.class);
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
            voluntaryExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenExitsAreDifferent() {
    // Create copy of exits and reverse to ensure it is different.
    SSZList<SignedVoluntaryExit> reverseVoluntaryExits =
        SSZList.create(voluntaryExits, MAX_VOLUNTARY_EXITS, SignedVoluntaryExit.class);
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
            reverseVoluntaryExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }
}
