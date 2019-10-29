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

package org.ethereum.beacon.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconBlockBody;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.Transfer;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.operations.deposit.DepositData;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.operations.slashing.IndexedAttestation;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.state.Fork;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.crypto.Hashes;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.collections.Bitlist;
import tech.pegasys.artemis.util.uint.UInt64;

public class TestDataFactory {
  private SpecConstants specConstants;

  public TestDataFactory() {
    this(BeaconChainSpec.DEFAULT_CONSTANTS);
  }

  public TestDataFactory(SpecConstants specConstants) {
    this.specConstants = specConstants;
  }

  public AttestationData createAttestationData() {
    AttestationData expected =
        new AttestationData(
            Hashes.sha256(BytesValue.fromHexString("aa")),
            new Checkpoint(EpochNumber.ZERO, Hashes.sha256(BytesValue.fromHexString("bb"))),
            new Checkpoint(EpochNumber.of(123), Hashes.sha256(BytesValue.fromHexString("cc"))),
            Crosslink.EMPTY);

    return expected;
  }

  public Attestation createAttestation() {
    return createAttestation(BytesValue.fromHexString("aa"));
  }

  public Attestation createAttestation(BytesValue someValue) {
    AttestationData attestationData = createAttestationData();
    Attestation attestation =
        new Attestation(
            Bitlist.of(
                someValue.size() * 8,
                someValue,
                specConstants.getMaxValidatorsPerCommittee().getValue()),
            attestationData,
            Bitlist.of(
                8,
                BytesValue.fromHexString("bb"),
                specConstants.getMaxValidatorsPerCommittee().getValue()),
            BLSSignature.wrap(Bytes96.fromHexString("cc")),
            specConstants);

    return attestation;
  }

  public DepositData createDepositData() {
    DepositData depositData =
        new DepositData(
            BLSPubkey.wrap(Bytes48.TRUE),
            Hashes.sha256(BytesValue.fromHexString("aa")),
            Gwei.ZERO,
            BLSSignature.wrap(Bytes96.ZERO));

    return depositData;
  }

  public Deposit createDeposit1() {
    Deposit deposit =
        Deposit.create(
            Collections.nCopies(
                specConstants.getDepositContractTreeDepthPlusOne().getIntValue(), Hash32.ZERO),
            createDepositData());

    return deposit;
  }

  public Deposit createDeposit2() {
    ArrayList<Hash32> hashes = new ArrayList<>();
    hashes.add(Hashes.sha256(BytesValue.fromHexString("aa")));
    hashes.add(Hashes.sha256(BytesValue.fromHexString("bb")));
    hashes.addAll(
        Collections.nCopies(
            specConstants.getDepositContractTreeDepthPlusOne().getIntValue() - hashes.size(),
            Hash32.ZERO));
    Deposit deposit = Deposit.create(hashes, createDepositData());

    return deposit;
  }

  public VoluntaryExit createExit() {
    VoluntaryExit voluntaryExit =
        new VoluntaryExit(
            EpochNumber.of(123),
            ValidatorIndex.MAX,
            BLSSignature.wrap(Bytes96.fromHexString("aa")));

    return voluntaryExit;
  }

  public ProposerSlashing createProposerSlashing(Random random) {
    ProposerSlashing proposerSlashing =
        new ProposerSlashing(
            ValidatorIndex.MAX,
            BeaconBlockTestUtil.createRandomHeader(random),
            BeaconBlockTestUtil.createRandomHeader(random));

    return proposerSlashing;
  }

  public BeaconBlockBody createBeaconBlockBody() {
    Random random = new Random(1);
    List<ProposerSlashing> proposerSlashings = new ArrayList<>();
    proposerSlashings.add(createProposerSlashing(random));
    List<AttesterSlashing> attesterSlashings = new ArrayList<>();
    attesterSlashings.add(createAttesterSlashings());
    List<Attestation> attestations = new ArrayList<>();
    attestations.add(createAttestation());
    attestations.add(createAttestation());
    List<Deposit> deposits = new ArrayList<>();
    deposits.add(createDeposit1());
    deposits.add(createDeposit2());
    List<VoluntaryExit> voluntaryExits = new ArrayList<>();
    voluntaryExits.add(createExit());
    List<Transfer> transfers = new ArrayList<>();
    BeaconBlockBody beaconBlockBody =
        new BeaconBlockBody(
            BLSSignature.ZERO,
            new Eth1Data(Hash32.ZERO, UInt64.ZERO, Hash32.ZERO),
            Bytes32.ZERO,
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits,
            transfers,
            specConstants);

    return beaconBlockBody;
  }

  public AttesterSlashing createAttesterSlashings() {
    return new AttesterSlashing(createSlashableAttestation(), createSlashableAttestation());
  }

  public IndexedAttestation createSlashableAttestation() {
    return new IndexedAttestation(
        Arrays.asList(ValidatorIndex.of(234), ValidatorIndex.of(235)),
        Arrays.asList(ValidatorIndex.of(678), ValidatorIndex.of(679)),
        createAttestationData(),
        BLSSignature.wrap(Bytes96.fromHexString("aa")),
        specConstants);
  }

  public BeaconBlock createBeaconBlock() {
    return createBeaconBlock(BytesValue.fromHexString("aa"));
  }

  public BeaconBlock createBeaconBlock(BytesValue someValue) {
    BeaconBlock beaconBlock =
        new BeaconBlock(
            SlotNumber.castFrom(UInt64.MAX_VALUE),
            Hashes.sha256(someValue),
            Hashes.sha256(BytesValue.fromHexString("bb")),
            createBeaconBlockBody(),
            BLSSignature.wrap(Bytes96.fromHexString("aa")));

    return beaconBlock;
  }

  public BeaconState createBeaconState() {
    BeaconState beaconState = BeaconState.getEmpty();

    return beaconState;
  }

  public Crosslink createCrosslink() {
    Crosslink crosslink = Crosslink.EMPTY;

    return crosslink;
  }

  public Fork createFork() {
    Fork fork = Fork.EMPTY;

    return fork;
  }

  public PendingAttestation createPendingAttestation() {
    PendingAttestation pendingAttestation =
        new PendingAttestation(
            Bitlist.of(
                8,
                BytesValue.fromHexString("aa"),
                specConstants.getMaxValidatorsPerCommittee().getValue()),
            createAttestationData(),
            SlotNumber.ZERO,
            ValidatorIndex.ZERO,
            specConstants);

    return pendingAttestation;
  }

  public ValidatorRecord createValidatorRecord() {
    ValidatorRecord validatorRecord =
        ValidatorRecord.Builder.fromDepositData(createDepositData())
            .withPubKey(BLSPubkey.ZERO)
            .withWithdrawalCredentials(Hash32.ZERO)
            .withActivationEligibilityEpoch(EpochNumber.ZERO)
            .withActivationEpoch(EpochNumber.ZERO)
            .withExitEpoch(EpochNumber.ZERO)
            .withWithdrawableEpoch(EpochNumber.ZERO)
            .withSlashed(Boolean.FALSE)
            .withEffectiveBalance(Gwei.ZERO)
            .build();

    return validatorRecord;
  }
}
