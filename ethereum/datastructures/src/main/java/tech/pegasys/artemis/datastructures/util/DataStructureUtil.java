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
import java.util.ArrayList;
import java.util.Arrays;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.ProposalSignedData;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.BLSSignature;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.operations.Exit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SlashableAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;

public final class DataStructureUtil {

  public static int randomInt() {
    return (int) (Math.random() * 1000000);
  }

  public static long randomLong() {
    return Math.round(Math.random() * 1000000);
  }

  public static UnsignedLong randomUnsignedLong() {
    return UnsignedLong.fromLongBits(randomLong());
  }

  public static Eth1Data randomEth1Data() {
    return new Eth1Data(Bytes32.random(), Bytes32.random());
  }

  public static AttestationData randomAttestationData(long slotNum) {
    return new AttestationData(
        UnsignedLong.valueOf(slotNum),
        randomUnsignedLong(),
        Bytes32.random(),
        Bytes32.random(),
        Bytes32.random(),
        Bytes32.random(),
        randomUnsignedLong(),
        Bytes32.random());
  }

  public static AttestationData randomAttestationData() {
    return randomAttestationData(randomLong());
  }

  public static Attestation randomAttestation(long slotNum) {
    return new Attestation(
        Bytes32.random(),
        randomAttestationData(slotNum),
        Bytes32.random(),
        new BLSSignature(Bytes48.random(), Bytes48.random()));
  }

  public static Attestation randomAttestation() {
    return randomAttestation(randomLong());
  }

  public static AttesterSlashing randomAttesterSlashing() {
    return new AttesterSlashing(randomSlashableAttestation(), randomSlashableAttestation());
  }

  public static ProposalSignedData randomProposalSignedData() {
    return new ProposalSignedData(randomUnsignedLong(), randomUnsignedLong(), Bytes32.random());
  }

  public static ProposerSlashing randomProposerSlashing() {
    return new ProposerSlashing(
        randomUnsignedLong(),
        randomProposalSignedData(),
        new BLSSignature(Bytes48.random(), Bytes48.random()),
        randomProposalSignedData(),
        new BLSSignature(Bytes48.random(), Bytes48.random()));
  }

  public static SlashableAttestation randomSlashableAttestation() {
    return new SlashableAttestation(
        Arrays.asList(randomUnsignedLong(), randomUnsignedLong(), randomUnsignedLong()),
        randomAttestationData(),
        Bytes32.random(),
        new BLSSignature(Bytes48.random(), Bytes48.random()));
  }

  public static DepositInput randomDepositInput() {
    return new DepositInput(
        Bytes48.random(), Bytes32.random(), new BLSSignature(Bytes48.random(), Bytes48.random()));
  }

  public static DepositData randomDepositData() {
    return new DepositData(
        UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT), randomUnsignedLong(), randomDepositInput());
  }

  public static Deposit randomDeposit() {
    return new Deposit(
        Arrays.asList(Bytes32.random(), Bytes32.random(), Bytes32.random()),
        randomUnsignedLong(),
        randomDepositData());
  }

  public static ArrayList<Deposit> randomDeposits(int num) {
    ArrayList<Deposit> deposits = new ArrayList<Deposit>();

    for (int i = 0; i < num; i++) {
      deposits.add(randomDeposit());
    }

    return deposits;
  }

  public static Exit randomExit() {
    return new Exit(
        randomUnsignedLong(),
        randomUnsignedLong(),
        new BLSSignature(Bytes48.random(), Bytes48.random()));
  }

  public static BeaconBlockBody randomBeaconBlockBody() {
    return new BeaconBlockBody(
        Arrays.asList(randomProposerSlashing(), randomProposerSlashing(), randomProposerSlashing()),
        Arrays.asList(randomAttesterSlashing(), randomAttesterSlashing(), randomAttesterSlashing()),
        Arrays.asList(randomAttestation(), randomAttestation(), randomAttestation()),
        Arrays.asList(randomDeposit(), randomDeposit(), randomDeposit()),
        Arrays.asList(randomExit(), randomExit(), randomExit()));
  }

  public static BeaconBlock randomBeaconBlock(long slotNum) {
    return new BeaconBlock(
        slotNum,
        Bytes32.random(),
        Bytes32.random(),
        new BLSSignature(Bytes48.random(), Bytes48.random()),
        randomEth1Data(),
        new BLSSignature(Bytes48.random(), Bytes48.random()),
        randomBeaconBlockBody());
  }

  public static BeaconBlock randomBeaconBlock() {
    return randomBeaconBlock(randomLong());
  }

  public static Validator randomValidator() {
    return new Validator(
        Bytes48.random(),
        Bytes32.random(),
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        randomUnsignedLong());
  }
}
