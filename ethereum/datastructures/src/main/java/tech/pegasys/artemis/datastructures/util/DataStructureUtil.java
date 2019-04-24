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

package tech.pegasys.artemis.datastructures.util;

import static tech.pegasys.artemis.datastructures.Constants.MAX_DEPOSIT_AMOUNT;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Proposal;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public final class DataStructureUtil {

  private static final ALogger LOG = new ALogger(DataStructureUtil.class.getName());

  public static long randomInt() {
    return Math.round(Math.random() * 1000000);
  }

  public static int randomInt(int seed) {
    return new Random(seed).nextInt();
  }

  public static long randomLong() {
    return Math.round(Math.random() * 1000000);
  }

  public static long randomLong(long seed) {
    return new Random(seed).nextLong();
  }

  public static Bytes32 randomBytes32(long seed) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(seed);
    return Bytes32.random(new SecureRandom(buffer.array()));
  }

  public static Bytes32 randomBytes32() {
    return Bytes32.random();
  }

  public static BLSPublicKey randomPublicKey() {
    return BLSPublicKey.random();
  }

  public static BLSPublicKey randomPublicKey(int seed) {
    return BLSPublicKey.random(seed);
  }

  public static Eth1Data randomEth1Data() {
    return new Eth1Data(randomBytes32(), randomBytes32());
  }

  public static Eth1Data randomEth1Data(int seed) {
    return new Eth1Data(randomBytes32(seed), randomBytes32(seed + 1));
  }

  public static Crosslink randomCrosslink() {
    return new Crosslink(randomLong(), randomBytes32());
  }

  public static Crosslink randomCrosslink(int seed) {
    return new Crosslink(randomLong(seed), randomBytes32(seed + 1));
  }

  public static AttestationData randomAttestationData(long slotNum) {
    return new AttestationData(
        slotNum,
        randomLong(),
        randomBytes32(),
        randomBytes32(),
        randomBytes32(),
        randomCrosslink(),
        randomLong(),
        randomBytes32());
  }

  public static AttestationData randomAttestationData(long slotNum, int seed) {
    return new AttestationData(
        slotNum,
        randomLong(seed),
        randomBytes32(seed++),
        randomBytes32(seed++),
        randomBytes32(seed++),
        randomCrosslink(seed++),
        randomLong(seed++),
        randomBytes32(seed++));
  }

  public static AttestationData randomAttestationData() {
    return randomAttestationData(randomLong());
  }

  public static Attestation randomAttestation(long slotNum) {
    return new Attestation(
        randomBytes32(), randomAttestationData(), randomBytes32(), BLSSignature.random());
  }

  public static Attestation randomAttestation() {
    return randomAttestation(randomLong());
  }

  public static AttesterSlashing randomAttesterSlashing() {
    return new AttesterSlashing(randomSlashableAttestation(), randomSlashableAttestation());
  }

  public static AttesterSlashing randomAttesterSlashing(int seed) {
    return new AttesterSlashing(
        randomSlashableAttestation(seed), randomSlashableAttestation(seed++));
  }

  public static Proposal randomProposal() {
    return new Proposal(randomLong(), randomLong(), randomBytes32(), BLSSignature.random());
  }

  public static Proposal randomProposal(int seed) {
    return new Proposal(
        randomLong(seed++), randomLong(seed++), randomBytes32(seed++), BLSSignature.random(seed));
  }

  public static ProposerSlashing randomProposerSlashing() {
    return new ProposerSlashing(randomLong(), randomProposal(), randomProposal());
  }

  public static ProposerSlashing randomProposerSlashing(int seed) {
    return new ProposerSlashing(randomLong(seed++), randomProposal(seed++), randomProposal(seed));
  }

  public static SlashableAttestation randomSlashableAttestation() {
    return new SlashableAttestation(
        Arrays.asList(randomLong(), randomLong(), randomLong()),
        randomAttestationData(),
        randomBytes32(),
        BLSSignature.random());
  }

  public static SlashableAttestation randomSlashableAttestation(int seed) {
    return new SlashableAttestation(
        Arrays.asList(randomLong(seed), randomLong(seed++), randomLong(seed++)),
        randomAttestationData(seed++),
        randomBytes32(seed++),
        BLSSignature.random(seed));
  }

  public static DepositInput randomDepositInput() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    BLSPublicKey pubkey = keyPair.getPublicKey();
    Bytes32 withdrawal_credentials = Bytes32.random();

    DepositInput proof_of_possession_data =
        new DepositInput(pubkey, withdrawal_credentials, Constants.EMPTY_SIGNATURE);

    BLSSignature proof_of_possession =
        BLSSignature.sign(
            keyPair,
            proof_of_possession_data.signedRoot("proof_of_possession"),
            Constants.DOMAIN_DEPOSIT);

    return new DepositInput(keyPair.getPublicKey(), withdrawal_credentials, proof_of_possession);
  }

  public static DepositInput randomDepositInput(int seed) {
    return new DepositInput(
        randomPublicKey(seed), randomBytes32(seed++), BLSSignature.random(seed));
  }

  public static DepositData randomDepositData() {
    return new DepositData(MAX_DEPOSIT_AMOUNT, randomLong(), randomDepositInput());
  }

  public static DepositData randomDepositData(int seed) {
    return new DepositData(MAX_DEPOSIT_AMOUNT, randomLong(seed++), randomDepositInput(seed++));
  }

  public static Deposit randomDeposit() {
    return new Deposit(
        Arrays.asList(randomBytes32(), randomBytes32(), randomBytes32()),
        randomLong(),
        randomDepositData());
  }

  public static Deposit randomDeposit(int seed) {
    return new Deposit(
        Arrays.asList(randomBytes32(seed), randomBytes32(seed++), randomBytes32(seed++)),
        randomLong(seed++),
        randomDepositData(seed++));
  }

  public static ArrayList<Deposit> randomDeposits(int num) {
    ArrayList<Deposit> deposits = new ArrayList<Deposit>();

    for (int i = 0; i < num; i++) {
      deposits.add(randomDeposit());
    }

    return deposits;
  }

  public static ArrayList<Deposit> randomDeposits(int num, int seed) {
    ArrayList<Deposit> deposits = new ArrayList<Deposit>();

    for (int i = 0; i < num; i++) {
      deposits.add(randomDeposit(i + seed));
    }

    return deposits;
  }

  public static VoluntaryExit randomVoluntaryExit() {
    return new VoluntaryExit(randomLong(), randomLong(), BLSSignature.random());
  }

  public static VoluntaryExit randomVoluntaryExit(int seed) {
    return new VoluntaryExit(randomLong(seed), randomLong(seed++), BLSSignature.random());
  }

  public static Transfer randomTransfer() {
    return new Transfer(
        randomLong(),
        randomLong(),
        randomLong(),
        randomLong(),
        randomLong(),
        BLSPublicKey.random(),
        BLSSignature.random());
  }

  public static Transfer randomTransfer(int seed) {
    return new Transfer(
        randomLong(seed),
        randomLong(seed + 1),
        randomLong(seed + 2),
        randomLong(seed + 3),
        randomLong(seed + 4),
        BLSPublicKey.random(seed + 5),
        BLSSignature.random(seed + 6));
  }

  public static BeaconBlockBody randomBeaconBlockBody() {
    return new BeaconBlockBody(
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing()),
        Arrays.asList(randomAttesterSlashing(), randomAttesterSlashing(), randomAttesterSlashing()),
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation()),
        randomDeposits(100),
        Arrays.asList(randomVoluntaryExit(), randomVoluntaryExit(), randomVoluntaryExit()),
        Arrays.asList(randomTransfer()));
  }

  public static BeaconBlockBody randomBeaconBlockBody(int seed) {
    return new BeaconBlockBody(
        Arrays.asList(
            randomProposerSlashing(seed),
            randomProposerSlashing(seed++),
            randomProposerSlashing(seed++)),
        Arrays.asList(
            randomAttesterSlashing(seed++),
            randomAttesterSlashing(seed++),
            randomAttesterSlashing(seed++)),
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation()),
        randomDeposits(100, seed++),
        Arrays.asList(
            randomVoluntaryExit(seed++), randomVoluntaryExit(seed++), randomVoluntaryExit(seed++)),
        Arrays.asList(randomTransfer(seed++)));
  }

  public static BeaconBlock randomBeaconBlock(long slotNum) {
    return new BeaconBlock(
        slotNum,
        randomBytes32(),
        randomBytes32(),
        BLSSignature.random(),
        randomEth1Data(),
        randomBeaconBlockBody(),
        BLSSignature.random());
  }

  public static ArrayList<Deposit> newDeposits(int numDeposits) {
    ArrayList<Deposit> deposits = new ArrayList<Deposit>();

    for (int i = 0; i < numDeposits; i++) {
      // https://github.com/ethereum/eth2.0-specs/blob/0.4.0/specs/validator/0_beacon-chain-validator.md#submit-deposit
      BLSKeyPair keypair = BLSKeyPair.random(i);
      DepositInput deposit_input =
          new DepositInput(keypair.getPublicKey(), Bytes32.ZERO, BLSSignature.empty());
      BLSSignature proof_of_possession =
          BLSSignature.sign(
              keypair, deposit_input.signedRoot("proof_of_possession"), Constants.DOMAIN_DEPOSIT);
      deposit_input.setProof_of_possession(proof_of_possession);

      long timestamp = i;
      DepositData deposit_data = new DepositData(MAX_DEPOSIT_AMOUNT, timestamp, deposit_input);
      long index = i;
      List<Bytes32> branch = Arrays.asList(Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO);
      Deposit deposit = new Deposit(branch, index, deposit_data);
      deposits.add(deposit);
    }
    return deposits;
  }

  public static BeaconBlock newBeaconBlock(
      long slotNum,
      Bytes32 parent_root,
      Bytes32 state_root,
      ArrayList<Deposit> deposits,
      List<Attestation> attestations) {
    return new BeaconBlock(
        slotNum,
        parent_root,
        state_root,
        BLSSignature.random((int) slotNum),
        new Eth1Data(Bytes32.ZERO, Bytes32.ZERO),
        new BeaconBlockBody(
            new ArrayList<>(),
            new ArrayList<>(),
            attestations,
            deposits,
            new ArrayList<>(),
            new ArrayList<>()),
        BLSSignature.empty());
  }

  public static BeaconStateWithCache createInitialBeaconState(int numValidators) {
    BeaconStateWithCache state = new BeaconStateWithCache();
    return BeaconStateUtil.get_genesis_beacon_state(
        state,
        newDeposits(numValidators),
        Constants.GENESIS_SLOT,
        new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));
  }

  public static Validator randomValidator() {
    return new Validator(
        randomPublicKey(),
        randomBytes32(),
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        false,
        false);
  }

  public static Validator randomValidator(int seed) {
    return new Validator(
        randomPublicKey(seed),
        randomBytes32(seed++),
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        false,
        false);
  }
}
