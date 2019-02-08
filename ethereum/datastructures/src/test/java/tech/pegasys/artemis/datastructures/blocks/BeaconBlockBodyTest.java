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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.CasperSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.operations.Exit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableVoteData;

class BeaconBlockBodyTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    List<Attestation> attestations =
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
    List<ProposerSlashing> proposerSlashings =
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
    List<CasperSlashing> casperSlashings =
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
    List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
    List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

    BeaconBlockBody bbb1 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);
    BeaconBlockBody bbb2 = bbb1;

    assertEquals(bbb1, bbb2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    List<Attestation> attestations =
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
    List<ProposerSlashing> proposerSlashings =
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
    List<CasperSlashing> casperSlashings =
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
    List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
    List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

    BeaconBlockBody bbb1 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);
    BeaconBlockBody bbb2 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);

    assertEquals(bbb1, bbb2);
  }

  @Test
  void equalsReturnsFalseWhenAttestationsAreDifferent() {
    List<Attestation> attestations =
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
    List<ProposerSlashing> proposerSlashings =
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
    List<CasperSlashing> casperSlashings =
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
    List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
    List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

    // Create copy of attestations and reverse to ensure it is different.
    List<Attestation> reverseAttestations = new ArrayList<Attestation>(attestations);
    Collections.reverse(reverseAttestations);

    BeaconBlockBody bbb1 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);
    BeaconBlockBody bbb2 =
        new BeaconBlockBody(
            reverseAttestations, proposerSlashings, casperSlashings, deposits, exits);

    assertNotEquals(bbb1, bbb2);
  }

  @Test
  void equalsReturnsFalseWhenProposerSlashingsAreDifferent() {
    List<Attestation> attestations =
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
    List<ProposerSlashing> proposerSlashings =
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
    List<CasperSlashing> casperSlashings =
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
    List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
    List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

    // Create copy of proposerSlashings and reverse to ensure it is different.
    List<ProposerSlashing> reverseProposerSlashings =
        new ArrayList<ProposerSlashing>(proposerSlashings);
    Collections.reverse(reverseProposerSlashings);

    BeaconBlockBody bbb1 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);
    BeaconBlockBody bbb2 =
        new BeaconBlockBody(
            attestations, reverseProposerSlashings, casperSlashings, deposits, exits);

    assertNotEquals(bbb1, bbb2);
  }

  @Test
  void equalsReturnsFalseWhenCasperSlashingsAreDifferent() {
    List<Attestation> attestations =
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
    List<ProposerSlashing> proposerSlashings =
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
    List<CasperSlashing> casperSlashings =
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
    List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
    List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

    // Create copy of casperSlashings and reverse to ensure it is different.
    List<CasperSlashing> reverseCasperSlashings = new ArrayList<CasperSlashing>(casperSlashings);
    Collections.reverse(reverseCasperSlashings);

    BeaconBlockBody bbb1 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);
    BeaconBlockBody bbb2 =
        new BeaconBlockBody(
            attestations, proposerSlashings, reverseCasperSlashings, deposits, exits);

    assertNotEquals(bbb1, bbb2);
  }

  @Test
  void equalsReturnsFalseWhenDepositsAreDifferent() {
    List<Attestation> attestations =
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
    List<ProposerSlashing> proposerSlashings =
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
    List<CasperSlashing> casperSlashings =
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
    List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
    List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

    // Create copy of deposits and reverse to ensure it is different.
    List<Deposit> reverseDeposits = new ArrayList<Deposit>(deposits);
    Collections.reverse(reverseDeposits);

    BeaconBlockBody bbb1 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);
    BeaconBlockBody bbb2 =
        new BeaconBlockBody(
            attestations, proposerSlashings, casperSlashings, reverseDeposits, exits);

    assertNotEquals(bbb1, bbb2);
  }

  @Test
  void equalsReturnsFalseWhenExitsAreDifferent() {
    List<Attestation> attestations =
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation());
    List<ProposerSlashing> proposerSlashings =
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing());
    List<CasperSlashing> casperSlashings =
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing());
    List<Deposit> deposits = Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit());
    List<Exit> exits = Arrays.asList(randomExit(), randomExit(), randomExit());

    // Create copy of exits and reverse to ensure it is different.
    List<Exit> reverseExits = new ArrayList<Exit>(exits);
    Collections.reverse(reverseExits);

    BeaconBlockBody bbb1 =
        new BeaconBlockBody(attestations, proposerSlashings, casperSlashings, deposits, exits);
    BeaconBlockBody bbb2 =
        new BeaconBlockBody(
            attestations, proposerSlashings, casperSlashings, deposits, reverseExits);

    assertNotEquals(bbb1, bbb2);
  }

  @Test
  void rountripSSZ() {
    BeaconBlockBody beaconBlockBody =
        new BeaconBlockBody(
            Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation()),
            Arrays.asList(
                randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing()),
            Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing()),
            Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit()),
            Arrays.asList(randomExit(), randomExit(), randomExit()));
    Bytes sszBeaconBlockBodyBytes = beaconBlockBody.toBytes();
    assertEquals(beaconBlockBody, BeaconBlockBody.fromBytes(sszBeaconBlockBodyBytes));
  }

  private int randomInt() {
    return (int) (Math.random() * 1000000);
  }

  private long randomLong() {
    return Math.round(Math.random() * 1000000);
  }

  private UnsignedLong randomUnsignedLong() {
    return UnsignedLong.fromLongBits(randomLong());
  }

  private List<Bytes48> randomSignature() {
    return Arrays.asList(Bytes48.random(), Bytes48.random());
  }

  private AttestationData randomAttestationData() {
    return new AttestationData(
        randomLong(),
        randomUnsignedLong(),
        Bytes32.random(),
        Bytes32.random(),
        Bytes32.random(),
        Bytes32.random(),
        randomUnsignedLong(),
        Bytes32.random());
  }

  private Attestation randomAttestation() {
    return new Attestation(
        randomAttestationData(), Bytes32.random(), Bytes32.random(), randomSignature());
  }

  private ProposalSignedData randomProposalSignedData() {
    return new ProposalSignedData(randomUnsignedLong(), randomUnsignedLong(), Bytes32.random());
  }

  private ProposerSlashing randomProposerSlashing() {
    return new ProposerSlashing(
        randomInt(),
        randomProposalSignedData(),
        randomSignature(),
        randomProposalSignedData(),
        randomSignature());
  }

  private SlashableVoteData randomSlashableVoteData() {
    return new SlashableVoteData(
        Arrays.asList(randomInt(), randomInt(), randomInt()),
        Arrays.asList(randomInt(), randomInt(), randomInt()),
        randomAttestationData(),
        randomSignature());
  }

  private CasperSlashing randomCasperSlashing() {
    return new CasperSlashing(randomSlashableVoteData(), randomSlashableVoteData());
  }

  private DepositInput randomDepositInput() {
    return new DepositInput(Bytes48.random(), Bytes32.random(), randomSignature());
  }

  private DepositData randomDepositData() {
    return new DepositData(randomDepositInput(), randomUnsignedLong(), randomUnsignedLong());
  }

  private Deposit randomDeposit() {
    return new Deposit(
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random()),
        randomUnsignedLong(),
        randomDepositData());
  }

  private Exit randomExit() {
    return new Exit(randomUnsignedLong(), randomUnsignedLong(), randomSignature());
  }
}
