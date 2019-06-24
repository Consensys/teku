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

import static tech.pegasys.artemis.datastructures.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.bls_domain;

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
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

  public static int randomInt() {
    return (int) Math.round(Math.random() * 1000000);
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

  public static UnsignedLong randomUnsignedLong(long seed) {
    return UnsignedLong.fromLongBits(randomLong(seed));
  }

  public static UnsignedLong randomUnsignedLong() {
    return UnsignedLong.fromLongBits(randomLong());
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
    return new Eth1Data(randomBytes32(), randomUnsignedLong(), randomBytes32());
  }

  public static Eth1Data randomEth1Data(int seed) {
    return new Eth1Data(randomBytes32(seed), randomUnsignedLong(seed), randomBytes32(seed));
  }

  public static Crosslink randomCrosslink() {
    return new Crosslink(
        randomUnsignedLong(),
        randomUnsignedLong(),
        randomUnsignedLong(),
        randomBytes32(),
        randomBytes32());
  }

  public static Crosslink randomCrosslink(int seed) {
    return new Crosslink(
        randomUnsignedLong(seed),
        randomUnsignedLong(seed),
        randomUnsignedLong(seed),
        randomBytes32(seed),
        randomBytes32(seed));
  }

  public static AttestationData randomAttestationData(long slotNum) {
    return new AttestationData(
        randomBytes32(),
        UnsignedLong.valueOf(slotNum),
        randomBytes32(),
        randomUnsignedLong(),
        randomBytes32(),
        randomCrosslink());
  }

  public static AttestationData randomAttestationData(long slotNum, int seed) {
    return new AttestationData(
        randomBytes32(seed),
        UnsignedLong.valueOf(slotNum),
        randomBytes32(seed),
        randomUnsignedLong(seed),
        randomBytes32(seed),
        randomCrosslink(seed));
  }

  public static AttestationData randomAttestationData() {
    return randomAttestationData(randomLong());
  }

  public static Attestation randomAttestation(UnsignedLong slotNum) {
    return new Attestation(
        randomBytes32(), randomAttestationData(), randomBytes32(), BLSSignature.random());
  }

  public static Attestation randomAttestation() {
    return randomAttestation(UnsignedLong.valueOf(randomLong()));
  }

  public static AttesterSlashing randomAttesterSlashing() {
    return new AttesterSlashing(randomIndexedAttestation(), randomIndexedAttestation());
  }

  public static AttesterSlashing randomAttesterSlashing(int seed) {
    return new AttesterSlashing(randomIndexedAttestation(seed), randomIndexedAttestation(seed));
  }

  public static BeaconBlock randomBeaconBlock(long slotNum) {
    UnsignedLong slot = UnsignedLong.valueOf(slotNum);
    Bytes32 previous_root = Bytes32.random();
    Bytes32 state_root = Bytes32.random();
    BeaconBlockBody body = randomBeaconBlockBody();
    BLSSignature signature = BLSSignature.random();

    return new BeaconBlock(slot, previous_root, state_root, body, signature);
  }

  public static BeaconBlockHeader randomBeaconBlockHeader() {
    return new BeaconBlockHeader(
        randomUnsignedLong(),
        randomBytes32(),
        randomBytes32(),
        randomBytes32(),
        BLSSignature.random());
  }

  public static BeaconBlockHeader randomBeaconBlockHeader(int seed) {
    return new BeaconBlockHeader(
        randomUnsignedLong(seed++),
        randomBytes32(seed++),
        randomBytes32(seed++),
        randomBytes32(seed++),
        BLSSignature.random(seed));
  }

  public static BeaconBlockBody randomBeaconBlockBody() {
    return new BeaconBlockBody(
        BLSSignature.random(),
        randomEth1Data(),
        Bytes32.ZERO,
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing()),
        Arrays.asList(randomAttesterSlashing(), randomAttesterSlashing(), randomAttesterSlashing()),
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation()),
        randomDeposits(100),
        Arrays.asList(randomVoluntaryExit(), randomVoluntaryExit(), randomVoluntaryExit()),
        Arrays.asList(randomTransfer()));
  }

  public static ProposerSlashing randomProposerSlashing() {
    return new ProposerSlashing(
        randomUnsignedLong(), randomBeaconBlockHeader(), randomBeaconBlockHeader());
  }

  public static ProposerSlashing randomProposerSlashing(int seed) {
    return new ProposerSlashing(
        randomUnsignedLong(seed++), randomBeaconBlockHeader(seed++), randomBeaconBlockHeader(seed));
  }

  public static IndexedAttestation randomIndexedAttestation() {
    return new IndexedAttestation(
        Arrays.asList(randomInt(), randomInt(), randomInt()),
        Arrays.asList(randomInt(), randomInt(), randomInt()),
        randomAttestationData(),
        BLSSignature.random());
  }

  public static IndexedAttestation randomIndexedAttestation(int seed) {
    return new IndexedAttestation(
        Arrays.asList(randomInt(seed), randomInt(seed), randomInt(seed)),
        Arrays.asList(randomInt(seed), randomInt(seed), randomInt(seed)),
        randomAttestationData(seed++),
        BLSSignature.random(seed));
  }

  public static DepositData randomDepositData() {
    BLSKeyPair keyPair = BLSKeyPair.random();
    BLSPublicKey pubkey = keyPair.getPublicKey();
    Bytes32 withdrawal_credentials = Bytes32.random();

    DepositData proof_of_possession_data =
        new DepositData(
            pubkey,
            withdrawal_credentials,
            UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
            Constants.EMPTY_SIGNATURE);

    BLSSignature proof_of_possession =
        BLSSignature.sign(
            keyPair,
            proof_of_possession_data.signing_root("signature"),
            bls_domain(Constants.DOMAIN_DEPOSIT));

    return new DepositData(
        keyPair.getPublicKey(),
        withdrawal_credentials,
        UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
        proof_of_possession);
  }

  public static DepositData randomDepositData(int seed) {
    BLSKeyPair keyPair = BLSKeyPair.random(seed);
    BLSPublicKey pubkey = keyPair.getPublicKey();
    Bytes32 withdrawal_credentials = randomBytes32(seed);

    DepositData proof_of_possession_data =
        new DepositData(
            pubkey,
            withdrawal_credentials,
            UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
            Constants.EMPTY_SIGNATURE);

    BLSSignature proof_of_possession =
        BLSSignature.sign(
            keyPair,
            proof_of_possession_data.signing_root("signature"),
            bls_domain(Constants.DOMAIN_DEPOSIT));

    return new DepositData(
        keyPair.getPublicKey(),
        withdrawal_credentials,
        UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
        proof_of_possession);
  }

  public static Deposit randomDeposit() {
    return new Deposit(Collections.nCopies(32, Bytes32.random()), randomDepositData());
  }

  public static Deposit randomDeposit(int seed) {
    return new Deposit(
        Arrays.asList(randomBytes32(seed), randomBytes32(seed++), randomBytes32(seed++)),
        randomDepositData(seed));
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
    return new VoluntaryExit(randomUnsignedLong(), randomUnsignedLong(), BLSSignature.random());
  }

  public static VoluntaryExit randomVoluntaryExit(int seed) {
    return new VoluntaryExit(
        randomUnsignedLong(seed), randomUnsignedLong(seed++), BLSSignature.random());
  }

  public static Transfer randomTransfer() {
    return new Transfer(
        randomUnsignedLong(),
        randomUnsignedLong(),
        randomUnsignedLong(),
        randomUnsignedLong(),
        randomUnsignedLong(),
        BLSPublicKey.random(),
        BLSSignature.random());
  }

  public static Transfer randomTransfer(int seed) {
    return new Transfer(
        randomUnsignedLong(seed),
        randomUnsignedLong(seed + 1),
        randomUnsignedLong(seed + 2),
        randomUnsignedLong(seed + 3),
        randomUnsignedLong(seed + 4),
        BLSPublicKey.random(seed + 5),
        BLSSignature.random(seed + 6));
  }

  public static ArrayList<Deposit> newDeposits(int numDeposits) {
    ArrayList<Deposit> deposits = new ArrayList<Deposit>();

    for (int i = 0; i < numDeposits; i++) {
      // https://github.com/ethereum/eth2.0-specs/blob/0.4.0/specs/validator/0_beacon-chain-validator.md#submit-deposit
      BLSKeyPair keypair = BLSKeyPair.random(i);
      DepositData depositData =
          new DepositData(
              keypair.getPublicKey(),
              Bytes32.ZERO,
              UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
              BLSSignature.empty());
      BLSSignature proof_of_possession =
          BLSSignature.sign(
              keypair, depositData.signing_root("signature"), bls_domain(Constants.DOMAIN_DEPOSIT));
      depositData.setSignature(proof_of_possession);

      List<Bytes32> proof =
          new ArrayList<>(Collections.nCopies(DEPOSIT_CONTRACT_TREE_DEPTH, Bytes32.ZERO));
      Deposit deposit = new Deposit(proof, depositData);
      deposits.add(deposit);
    }
    return deposits;
  }

  public static BeaconBlock newBeaconBlock(
      UnsignedLong slotNum,
      Bytes32 previous_block_root,
      Bytes32 state_root,
      ArrayList<Deposit> deposits,
      List<Attestation> attestations) {
    return new BeaconBlock(
        slotNum,
        previous_block_root,
        state_root,
        new BeaconBlockBody(
            Constants.EMPTY_SIGNATURE,
            new Eth1Data(ZERO_HASH, UnsignedLong.valueOf(16), ZERO_HASH),
            Bytes32.ZERO,
            new ArrayList<>(),
            new ArrayList<>(),
            attestations,
            deposits,
            new ArrayList<>(),
            new ArrayList<>()),
        BLSSignature.empty());
  }

  public static BeaconStateWithCache createInitialBeaconState(
      List<Deposit> deposits, Bytes32 deposit_root) {
    BeaconStateWithCache state = new BeaconStateWithCache();
    return BeaconStateUtil.get_genesis_beacon_state(
        state,
        deposits,
        UnsignedLong.valueOf(Constants.GENESIS_SLOT),
        new Eth1Data(deposit_root, UnsignedLong.ZERO, Bytes32.ZERO));
  }

  public static BeaconStateWithCache createInitialBeaconState(int numValidators) {
    BeaconStateWithCache state = new BeaconStateWithCache();
    return BeaconStateUtil.get_genesis_beacon_state(
        state,
        newDeposits(numValidators),
        UnsignedLong.valueOf(Constants.GENESIS_SLOT),
        new Eth1Data(Bytes32.ZERO, UnsignedLong.ZERO, Bytes32.ZERO));
  }

  public static Validator randomValidator() {
    return new Validator(
        randomPublicKey(),
        randomBytes32(),
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        false,
        UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE));
  }

  public static Validator randomValidator(int seed) {
    return new Validator(
        randomPublicKey(seed),
        randomBytes32(seed),
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        false,
        UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE));
  }
}
