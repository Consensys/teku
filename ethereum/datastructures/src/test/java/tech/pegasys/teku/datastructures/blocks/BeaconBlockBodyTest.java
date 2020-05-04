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

package tech.pegasys.teku.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.teku.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.teku.util.config.Constants.MAX_ATTESTER_SLASHINGS;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.teku.util.config.Constants.MAX_PROPOSER_SLASHINGS;
import static tech.pegasys.teku.util.config.Constants.MAX_VOLUNTARY_EXITS;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

class BeaconBlockBodyTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private BLSSignature blsSignature = dataStructureUtil.randomSignature();
  private Eth1Data eth1Data = dataStructureUtil.randomEth1Data();
  private Bytes32 graffiti = dataStructureUtil.randomBytes32();
  private SSZMutableList<ProposerSlashing> proposerSlashings =
      SSZList.createMutable(ProposerSlashing.class, MAX_PROPOSER_SLASHINGS);
  private SSZMutableList<AttesterSlashing> attesterSlashings =
      SSZList.createMutable(AttesterSlashing.class, MAX_ATTESTER_SLASHINGS);
  private SSZMutableList<Attestation> attestations =
      SSZList.createMutable(Attestation.class, MAX_ATTESTATIONS);
  private SSZMutableList<Deposit> deposits = SSZList.createMutable(Deposit.class, MAX_DEPOSITS);
  private SSZMutableList<SignedVoluntaryExit> voluntaryExits =
      SSZList.createMutable(SignedVoluntaryExit.class, MAX_VOLUNTARY_EXITS);

  {
    proposerSlashings.add(dataStructureUtil.randomProposerSlashing());
    proposerSlashings.add(dataStructureUtil.randomProposerSlashing());
    proposerSlashings.add(dataStructureUtil.randomProposerSlashing());
    attesterSlashings.add(dataStructureUtil.randomAttesterSlashing());
    attestations.add(dataStructureUtil.randomAttestation());
    attestations.add(dataStructureUtil.randomAttestation());
    attestations.add(dataStructureUtil.randomAttestation());
    deposits.addAll(dataStructureUtil.randomDeposits(MAX_DEPOSITS));
    voluntaryExits.add(dataStructureUtil.randomSignedVoluntaryExit());
    voluntaryExits.add(dataStructureUtil.randomSignedVoluntaryExit());
    voluntaryExits.add(dataStructureUtil.randomSignedVoluntaryExit());
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
    SSZList<ProposerSlashing> reverseProposerSlashings = proposerSlashings.reversed();

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            SSZList.createMutable(reverseProposerSlashings),
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttesterSlashingsAreDifferent() {
    // Create copy of attesterSlashings and change the element to ensure it is different.
    SSZMutableList<AttesterSlashing> otherAttesterSlashings =
        SSZList.concat(
            SSZList.singleton(dataStructureUtil.randomAttesterSlashing()), attesterSlashings);

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
    SSZList<Attestation> reverseAttestations = attestations.reversed();

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
    SSZList<Deposit> reverseDeposits = deposits.reversed();

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
    SSZList<SignedVoluntaryExit> reverseVoluntaryExits = voluntaryExits.reversed();

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

  @Test
  void roundTripsViaSsz() {
    BeaconBlockBody beaconBlockBody = dataStructureUtil.randomBeaconBlockBody();
    BeaconBlockBody newBeaconBlockBody =
        SimpleOffsetSerializer.deserialize(
            SimpleOffsetSerializer.serialize(beaconBlockBody), BeaconBlockBody.class);
    assertEquals(beaconBlockBody, newBeaconBlockBody);
  }
}
