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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.teku.util.config.Constants.MAX_DEPOSITS;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;

class BeaconBlockBodyTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private BLSSignature blsSignature = dataStructureUtil.randomSignature();
  private Eth1Data eth1Data = dataStructureUtil.randomEth1Data();
  private Bytes32 graffiti = dataStructureUtil.randomBytes32();
  private final SszList<ProposerSlashing> proposerSlashings =
      BeaconBlockBodyLists.createProposerSlashings(
          dataStructureUtil.randomProposerSlashing(),
          dataStructureUtil.randomProposerSlashing(),
          dataStructureUtil.randomProposerSlashing());
  private final SszList<AttesterSlashing> attesterSlashings =
      BeaconBlockBodyLists.createAttesterSlashings(dataStructureUtil.randomAttesterSlashing());
  private final SszList<Attestation> attestations =
      BeaconBlockBodyLists.createAttestations(
          dataStructureUtil.randomAttestation(),
          dataStructureUtil.randomAttestation(),
          dataStructureUtil.randomAttestation());
  private final SszList<Deposit> deposits =
      BeaconBlockBodyLists.createDeposits(
          dataStructureUtil.randomDeposits(MAX_DEPOSITS).toArray(new Deposit[0]));
  private final SszList<SignedVoluntaryExit> voluntaryExits =
      BeaconBlockBodyLists.createVoluntaryExits(
          dataStructureUtil.randomSignedVoluntaryExit(),
          dataStructureUtil.randomSignedVoluntaryExit(),
          dataStructureUtil.randomSignedVoluntaryExit());

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

  private <T extends SszData> SszList<T> reversed(SszList<T> list) {
    List<T> reversedList = list.stream().collect(Collectors.toList());
    Collections.reverse(reversedList);
    return list.getSchema().createFromElements(reversedList);
  }

  @Test
  void equalsReturnsFalseWhenProposerSlashingsAreDifferent() {
    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            blsSignature,
            eth1Data,
            graffiti,
            reversed(proposerSlashings),
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttesterSlashingsAreDifferent() {
    // Create copy of attesterSlashings and change the element to ensure it is different.
    SszList<AttesterSlashing> otherAttesterSlashings =
        Stream.concat(
                Stream.of(dataStructureUtil.randomAttesterSlashing()), attesterSlashings.stream())
            .collect(BeaconBlockBody.getSszSchema().getAttesterSlashingsSchema().collector());

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
    SszList<Attestation> reverseAttestations = reversed(attestations);

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
    SszList<Deposit> reverseDeposits = reversed(deposits);

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
    SszList<SignedVoluntaryExit> reverseVoluntaryExits = reversed(voluntaryExits);

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
        BeaconBlockBody.SSZ_SCHEMA.get().sszDeserialize(beaconBlockBody.sszSerialize());
    assertEquals(beaconBlockBody, newBeaconBlockBody);
  }
}
