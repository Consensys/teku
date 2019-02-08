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
import java.util.Objects;
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

class BeaconBlockTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);
    BeaconBlock b2 = b1;

    assertEquals(b1, b2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);
    BeaconBlock b2 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);

    assertEquals(b1, b2);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);
    BeaconBlock b2 =
        new BeaconBlock(
            slot + randomLong(),
            ancestorHashes,
            stateRoot,
            randaoReveal,
            eth1Data,
            signature,
            body);

    assertNotEquals(b1, b2);
  }

  @Test
  void equalsReturnsFalseWhenAncestorHashesAreDifferent() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    // Create copy of ancestorHashes and reverse to ensure it is different.
    List<Bytes32> reverseAncestorHashes = new ArrayList<Bytes32>(ancestorHashes);
    Collections.reverse(reverseAncestorHashes);

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);
    BeaconBlock b2 =
        new BeaconBlock(
            slot, reverseAncestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);

    assertNotEquals(b1, b2);
  }

  @Test
  void equalsReturnsFalseWhenStateRootsAreDifferent() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);
    BeaconBlock b2 =
        new BeaconBlock(
            slot, ancestorHashes, stateRoot.not(), randaoReveal, eth1Data, signature, body);

    assertNotEquals(b1, b2);
  }

  @Test
  void equalsReturnsFalseWhenRandaoRevealsAreDifferent() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    // Create copy of randaoReveal and reverse to ensure it is different.
    List<Bytes48> reverseRandaoReveal = new ArrayList<Bytes48>(randaoReveal);
    Collections.reverse(reverseRandaoReveal);

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);

    BeaconBlock b2 =
        new BeaconBlock(
            slot, ancestorHashes, stateRoot, reverseRandaoReveal, eth1Data, signature, body);

    assertNotEquals(b1, b2);
  }

  @Test
  void equalsReturnsFalseWhenEth1DataIsDifferent() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);
    BeaconBlock b2 =
        new BeaconBlock(
            slot,
            ancestorHashes,
            stateRoot,
            randaoReveal,
            new Eth1Data(eth1Data.getDeposit_root().not(), eth1Data.getBlock_hash().not()),
            signature,
            body);

    assertNotEquals(b1, b2);
  }

  @Test
  void equalsReturnsFalseWhenSignaturesAreDifferent() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    // Create copy of signature and reverse to ensure it is different.
    List<Bytes48> reverseSignature = new ArrayList<Bytes48>(signature);
    Collections.reverse(reverseSignature);

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);

    BeaconBlock b2 =
        new BeaconBlock(
            slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, reverseSignature, body);

    assertNotEquals(b1, b2);
  }

  @Test
  void equalsReturnsFalseWhenBeaconBlockBodiesAreDifferent() {
    long slot = randomLong();
    List<Bytes32> ancestorHashes =
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random());
    Bytes32 stateRoot = Bytes32.random();
    List<Bytes48> randaoReveal = randomSignature();
    Eth1Data eth1Data = randomEth1Data();
    List<Bytes48> signature = randomSignature();
    BeaconBlockBody body = randomBeaconBlockBody();

    // BeaconBlock is rather involved to create. Just create a random one until it is not the same
    // as the original.
    BeaconBlockBody otherBody = randomBeaconBlockBody();
    while (Objects.equals(otherBody, body)) {
      otherBody = randomBeaconBlockBody();
    }

    BeaconBlock b1 =
        new BeaconBlock(slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, body);

    BeaconBlock b2 =
        new BeaconBlock(
            slot, ancestorHashes, stateRoot, randaoReveal, eth1Data, signature, otherBody);

    assertNotEquals(b1, b2);
  }

  @Test
  void rountripSSZ() {
    BeaconBlock beaconBlock =
        new BeaconBlock(
            randomLong(),
            Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random()),
            Bytes32.random(),
            randomSignature(),
            randomEth1Data(),
            randomSignature(),
            randomBeaconBlockBody());
    Bytes sszBeaconBlockBytes = beaconBlock.toBytes();
    assertEquals(beaconBlock, BeaconBlock.fromBytes(sszBeaconBlockBytes));
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

  private Eth1Data randomEth1Data() {
    return new Eth1Data(Bytes32.random(), Bytes32.random());
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

  private BeaconBlockBody randomBeaconBlockBody() {
    return new BeaconBlockBody(
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation()),
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing()),
        Arrays.asList(randomCasperSlashing(), randomCasperSlashing(), randomCasperSlashing()),
        Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit()),
        Arrays.asList(randomExit(), randomExit(), randomExit()));
  }
}
