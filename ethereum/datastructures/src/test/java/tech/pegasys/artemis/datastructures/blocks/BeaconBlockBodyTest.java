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
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomCasperSlashing;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomDeposit;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomExit;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomProposerSlashing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.CasperSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.Exit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;

class BeaconBlockBodyTest {

  List<Attestation> attestations =
      Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
  List<ProposerSlashing> proposerSlashings =
      Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
  List<CasperSlashing> casperSlashings =
      Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
  List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
  List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

  BeaconBlockBody beaconBlockBody =
      new BeaconBlockBody(proposerSlashings, casperSlashings, attestations, deposits, exits);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    BeaconBlockBody testBeaconBlockBody = beaconBlockBody;

    assertEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(proposerSlashings, casperSlashings, attestations, deposits, exits);

    assertEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenProposerSlashingsAreDifferent() {
    // Create copy of proposerSlashings and reverse to ensure it is different.
    List<ProposerSlashing> reverseProposerSlashings =
        new ArrayList<ProposerSlashing>(proposerSlashings);
    Collections.reverse(reverseProposerSlashings);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            reverseProposerSlashings, casperSlashings, attestations, deposits, exits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenCasperSlashingsAreDifferent() {
    // Create copy of casperSlashings and reverse to ensure it is different.
    List<CasperSlashing> reverseCasperSlashings = new ArrayList<CasperSlashing>(casperSlashings);
    Collections.reverse(reverseCasperSlashings);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings, reverseCasperSlashings, attestations, deposits, exits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenAttestationsAreDifferent() {
    // Create copy of attestations and reverse to ensure it is different.
    List<Attestation> reverseAttestations = new ArrayList<Attestation>(attestations);
    Collections.reverse(reverseAttestations);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings, casperSlashings, reverseAttestations, deposits, exits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenDepositsAreDifferent() {
    // Create copy of deposits and reverse to ensure it is different.
    List<Deposit> reverseDeposits = new ArrayList<Deposit>(deposits);
    Collections.reverse(reverseDeposits);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings, casperSlashings, attestations, reverseDeposits, exits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenExitsAreDifferent() {
    // Create copy of exits and reverse to ensure it is different.
    List<Exit> reverseExits = new ArrayList<Exit>(exits);
    Collections.reverse(reverseExits);

    BeaconBlockBody testBeaconBlockBody =
        new BeaconBlockBody(
            proposerSlashings, casperSlashings, attestations, deposits, reverseExits);

    assertNotEquals(beaconBlockBody, testBeaconBlockBody);
  }

  @Test
  void rountripSSZ() {
    Bytes sszBeaconBlockBodyBytes = beaconBlockBody.toBytes();
    assertEquals(beaconBlockBody, BeaconBlockBody.fromBytes(sszBeaconBlockBodyBytes));
  }
}
