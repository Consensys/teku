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

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.operations.Exit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

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
    return new Eth1Data(randomBytes32(), randomBytes32());
  }

  public static Eth1Data randomEth1Data(int seed) {
    return new Eth1Data(randomBytes32(seed), randomBytes32(seed + 1));
  }

  public static AttestationData randomAttestationData(long slotNum) {
    return new AttestationData(
        UnsignedLong.valueOf(slotNum),
        randomUnsignedLong(),
        randomBytes32(),
        randomBytes32(),
        randomBytes32(),
        randomBytes32(),
        randomUnsignedLong(),
        randomBytes32());
  }

  public static AttestationData randomAttestationData(long slotNum, int seed) {
    return new AttestationData(
        UnsignedLong.valueOf(slotNum),
        randomUnsignedLong(seed),
        randomBytes32(seed++),
        randomBytes32(seed++),
        randomBytes32(seed++),
        randomBytes32(seed++),
        randomUnsignedLong(seed++),
        randomBytes32(seed++));
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
    return new AttesterSlashing(randomSlashableAttestation(), randomSlashableAttestation());
  }

  public static AttesterSlashing randomAttesterSlashing(int seed) {
    return new AttesterSlashing(
        randomSlashableAttestation(seed), randomSlashableAttestation(seed++));
  }

  public static ProposalSignedData randomProposalSignedData() {
    return new ProposalSignedData(randomUnsignedLong(), randomUnsignedLong(), randomBytes32());
  }

  public static ProposalSignedData randomProposalSignedData(int seed) {
    return new ProposalSignedData(
        randomUnsignedLong(seed), randomUnsignedLong(seed++), randomBytes32(seed++));
  }

  public static ProposerSlashing randomProposerSlashing() {
    return new ProposerSlashing(
        randomUnsignedLong(),
        randomProposalSignedData(),
        BLSSignature.random(),
        randomProposalSignedData(),
        BLSSignature.random());
  }

  public static ProposerSlashing randomProposerSlashing(int seed) {
    return new ProposerSlashing(
        randomUnsignedLong(seed),
        randomProposalSignedData(seed++),
        BLSSignature.random(seed),
        randomProposalSignedData(seed++),
        BLSSignature.random(seed));
  }

  public static SlashableAttestation randomSlashableAttestation() {
    return new SlashableAttestation(
        Arrays.asList(randomUnsignedLong(), randomUnsignedLong(), randomUnsignedLong()),
        randomAttestationData(),
        randomBytes32(),
        BLSSignature.random());
  }

  public static SlashableAttestation randomSlashableAttestation(int seed) {
    return new SlashableAttestation(
        Arrays.asList(
            randomUnsignedLong(seed), randomUnsignedLong(seed++), randomUnsignedLong(seed++)),
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
            HashTreeUtil.hash_tree_root(proof_of_possession_data.toBytes()),
            Constants.DOMAIN_DEPOSIT);

    return new DepositInput(keyPair.getPublicKey(), withdrawal_credentials, proof_of_possession);
  }

  public static DepositInput randomDepositInput(int seed) {
    return new DepositInput(
        randomPublicKey(seed), randomBytes32(seed++), BLSSignature.random(seed));
  }

  public static DepositData randomDepositData() {
    return new DepositData(
        UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT), randomUnsignedLong(), randomDepositInput());
  }

  public static DepositData randomDepositData(int seed) {
    return new DepositData(
        UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT),
        randomUnsignedLong(seed++),
        randomDepositInput(seed++));
  }

  public static Deposit randomDeposit() {
    return new Deposit(
        Arrays.asList(randomBytes32(), randomBytes32(), randomBytes32()),
        randomUnsignedLong(),
        randomDepositData());
  }

  public static Deposit randomDeposit(int seed) {
    return new Deposit(
        Arrays.asList(randomBytes32(seed), randomBytes32(seed++), randomBytes32(seed++)),
        randomUnsignedLong(seed++),
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

  public static Exit randomExit() {
    return new Exit(randomUnsignedLong(), randomUnsignedLong(), BLSSignature.random());
  }

  public static Exit randomExit(int seed) {
    return new Exit(randomUnsignedLong(seed), randomUnsignedLong(seed++), BLSSignature.random());
  }

  public static BeaconBlockBody randomBeaconBlockBody() {
    return new BeaconBlockBody(
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing()),
        Arrays.asList(randomAttesterSlashing(), randomAttesterSlashing(), randomAttesterSlashing()),
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation()),
        randomDeposits(100),
        Arrays.asList(randomExit(), randomExit(), randomExit()));
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
        Arrays.asList(randomExit(seed++), randomExit(seed++), randomExit(seed++)));
  }

  public static BeaconBlock randomBeaconBlock(long slotNum) {
    return new BeaconBlock(
        slotNum,
        randomBytes32(),
        randomBytes32(),
        BLSSignature.random(),
        randomEth1Data(),
        BLSSignature.random(),
        randomBeaconBlockBody());
  }

  public static ArrayList<Deposit> newDeposits(int numDeposits, int slot, Integer entropy) {
    ArrayList<Deposit> deposits = new ArrayList<Deposit>();

    for (int i = 0; i < numDeposits; i++) {
      // https://github.com/ethereum/eth2.0-specs/blob/0.4.0/specs/validator/0_beacon-chain-validator.md#submit-deposit
      ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
      buffer.putInt(entropy);
      entropy = ByteBuffer.wrap(Hash.keccak256(buffer.array())).getInt();
      BLSKeyPair keypair = BLSKeyPair.random(entropy);
      DepositInput deposit_input =
          new DepositInput(keypair.getPublicKey(), Bytes32.ZERO, BLSSignature.empty());
      BLSSignature proof_of_possession =
          BLSSignature.sign(
              keypair,
              HashTreeUtil.hash_tree_root(deposit_input.toBytes()),
              Constants.DOMAIN_DEPOSIT);
      deposit_input.setProof_of_possession(proof_of_possession);

      UnsignedLong timestamp = UnsignedLong.valueOf(i);
      DepositData deposit_data =
          new DepositData(UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT), timestamp, deposit_input);
      UnsignedLong index = UnsignedLong.valueOf(i);
      List<Bytes32> branch = Arrays.asList(Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO);
      Deposit deposit = new Deposit(branch, index, deposit_data);
      deposits.add(deposit);
    }
    return deposits;
  }

  public static BeaconBlock newBeaconBlock(
      UnsignedLong slotNum, Bytes32 parent_root, Bytes32 state_root, ArrayList<Deposit> deposits) {
    return new BeaconBlock(
        slotNum.longValue(),
        parent_root,
        state_root,
        BLSSignature.random(slotNum.intValue()),
        new Eth1Data(Bytes32.ZERO, Bytes32.ZERO),
        BLSSignature.empty(),
        new BeaconBlockBody(
            new ArrayList<ProposerSlashing>(),
            new ArrayList<AttesterSlashing>(),
            new ArrayList<Attestation>(),
            deposits,
            new ArrayList<Exit>()));
  }

  public static BeaconState createInitialBeaconState(int numValidators, int entropy) {
    return BeaconStateUtil.get_initial_beacon_state(
        newDeposits(numValidators, Math.toIntExact(Constants.GENESIS_SLOT), entropy),
        UnsignedLong.valueOf(Constants.GENESIS_SLOT),
        new Eth1Data(Bytes32.ZERO, Bytes32.ZERO));
  }

  public static Validator randomValidator() {
    return new Validator(
        randomPublicKey(),
        randomBytes32(),
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        randomUnsignedLong());
  }

  public static Validator randomValidator(int seed) {
    return new Validator(
        randomPublicKey(seed),
        randomBytes32(seed++),
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        randomUnsignedLong(seed++));
  }
}
