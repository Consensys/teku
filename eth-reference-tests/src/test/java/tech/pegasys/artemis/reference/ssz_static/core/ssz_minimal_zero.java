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

package tech.pegasys.artemis.reference.ssz_static.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.CompactCommittee;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.reference.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
class ssz_minimal_zero extends TestSuite {

  private static String testFile = "**/ssz_minimal_zero.yaml";

  @ParameterizedTest(name = "{index}. SSZ serialized, root, signing_root of Attestation")
  @MethodSource("readMessageSSZAttestation")
  void sszAttestationCheckSerializationRootAndSigningRoot(
      Attestation attestation, Bytes serialized, Bytes32 root, Bytes signing_root) {

    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(attestation),
        attestation.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        attestation.hash_tree_root(),
        attestation.getClass().getName() + " failed the root test");
    assertEquals(
        signing_root,
        attestation.signing_root("signature"),
        attestation.getClass().getName() + " failed the signing_root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Attestation.class, Arrays.asList("Attestation", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Attestation", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Attestation", "root")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Attestation", "signing_root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of AttestationData")
  @MethodSource("readMessageSSZAttestationData")
  void sszAttestationDataCheckSerializationRoot(
      AttestationData data, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(data),
        data.getClass().getName() + " failed the serialiaztion test");
    assertEquals(root, data.hash_tree_root(), data.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationData() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(AttestationData.class, Arrays.asList("AttestationData", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("AttestationData", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("AttestationData", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of AttestationDataAndCustodyBit")
  @MethodSource("readMessageSSZAttestationDataAndCustodyBit")
  void sszAttestationDataAndCustodyBitCheckSerializationRoot(
      AttestationDataAndCustodyBit dataAndCustodyBit, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(dataAndCustodyBit),
        dataAndCustodyBit.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        dataAndCustodyBit.hash_tree_root(),
        dataAndCustodyBit.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationDataAndCustodyBit() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            AttestationDataAndCustodyBit.class,
            Arrays.asList("AttestationDataAndCustodyBit", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("AttestationDataAndCustodyBit", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("AttestationDataAndCustodyBit", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of AttesterSlashing")
  @MethodSource("readMessageSSZAttesterSlashing")
  void sszAttesterSlashingCheckSerializationRoot(
      AttesterSlashing attesterSlashing, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(attesterSlashing),
        attesterSlashing.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        attesterSlashing.hash_tree_root(),
        attesterSlashing.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttesterSlashing() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(AttesterSlashing.class, Arrays.asList("AttesterSlashing", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("AttesterSlashing", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("AttesterSlashing", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconBlock")
  @MethodSource("readMessageSSZBeaconBlock")
  void sszBeaconBlockCheckSerializationRoot(
      BeaconBlock beaconBlock, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(beaconBlock),
        beaconBlock.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        beaconBlock.hash_tree_root(),
        beaconBlock.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlock() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(BeaconBlock.class, Arrays.asList("BeaconBlock", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("BeaconBlock", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("BeaconBlock", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconBlockBody")
  @MethodSource("readMessageSSZBeaconBlockBody")
  void sszBeaconBlockBodyCheckSerializationRoot(
      BeaconBlockBody beaconBlockBody, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(beaconBlockBody),
        beaconBlockBody.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        beaconBlockBody.hash_tree_root(),
        beaconBlockBody.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockBody() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(BeaconBlockBody.class, Arrays.asList("BeaconBlockBody", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("BeaconBlockBody", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("BeaconBlockBody", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconBlockHeader")
  @MethodSource("readMessageSSZBeaconBlockHeader")
  void sszBeaconBlockHeaderCheckSerializationRoot(
      BeaconBlockHeader beaconBlockHeader, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(beaconBlockHeader),
        beaconBlockHeader.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        beaconBlockHeader.hash_tree_root(),
        beaconBlockHeader.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockHeader() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(BeaconBlockHeader.class, Arrays.asList("BeaconBlockHeader", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("BeaconBlockHeader", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("BeaconBlockHeader", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconState")
  @MethodSource("readMessageSSZBeaconState")
  void sszBeaconStateCheckSerializationRoot(
      BeaconState beaconState, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(beaconState),
        beaconState.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        beaconState.hash_tree_root(),
        beaconState.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconState() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(BeaconState.class, Arrays.asList("BeaconState", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("BeaconState", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("BeaconState", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Checkpoint")
  @MethodSource("readMessageSSZCheckpoint")
  void sszCheckpointCheckSerializationRoot(Checkpoint checkpoint, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(checkpoint),
        checkpoint.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        checkpoint.hash_tree_root(),
        checkpoint.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCheckpoint() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Checkpoint.class, Arrays.asList("Checkpoint", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Checkpoint", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Checkpoint", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of CompactCommittee")
  @MethodSource("readMessageSSZCompactCommittee")
  void sszCompactCommitteeCheckSerializationRoot(
      CompactCommittee compactCommittee, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(compactCommittee),
        compactCommittee.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        compactCommittee.hash_tree_root(),
        compactCommittee.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCompactCommittee() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(CompactCommittee.class, Arrays.asList("CompactCommittee", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("CompactCommittee", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("CompactCommittee", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Crosslink")
  @MethodSource("readMessageSSZCrosslink")
  void sszCrosslinkCheckSerializationRoot(Crosslink crosslink, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(crosslink),
        crosslink.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root, crosslink.hash_tree_root(), crosslink.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCrosslink() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Crosslink.class, Arrays.asList("Crosslink", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Crosslink", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Crosslink", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Deposit")
  @MethodSource("readMessageSSZDeposit")
  void sszDepositCheckSerializationRoot(Deposit deposit, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(deposit),
        deposit.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root, deposit.hash_tree_root(), deposit.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZDeposit() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Deposit.class, Arrays.asList("Deposit", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Deposit", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Deposit", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of DepositData")
  @MethodSource("readMessageSSZDepositData")
  void sszDepositDataCheckSerializationRoot(
      DepositData depositData, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(depositData),
        depositData.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        depositData.hash_tree_root(),
        depositData.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZDepositData() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(DepositData.class, Arrays.asList("DepositData", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("DepositData", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("DepositData", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Eth1Data")
  @MethodSource("readMessageSSZEth1Data")
  void sszEth1DataCheckSerializationRoot(Eth1Data eth1Data, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(eth1Data),
        eth1Data.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root, eth1Data.hash_tree_root(), eth1Data.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZEth1Data() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Eth1Data.class, Arrays.asList("Eth1Data", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Eth1Data", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Eth1Data", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Fork")
  @MethodSource("readMessageSSZFork")
  void sszForkCheckSerializationRoot(Fork fork, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(fork),
        fork.getClass().getName() + " failed the serialiaztion test");
    assertEquals(root, fork.hash_tree_root(), fork.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZFork() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Fork.class, Arrays.asList("Fork", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Fork", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Fork", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of HistoricalBatch")
  @MethodSource("readMessageSSZHistoricalBatch")
  void sszHistoricalBatchCheckSerializationRoot(
      HistoricalBatch historicalBatch, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(historicalBatch),
        historicalBatch.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        historicalBatch.hash_tree_root(),
        historicalBatch.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZHistoricalBatch() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(HistoricalBatch.class, Arrays.asList("HistoricalBatch", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("HistoricalBatch", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("HistoricalBatch", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of IndexedAttestation")
  @MethodSource("readMessageSSZIndexedAttestation")
  void sszIndexedAttestationCheckSerializationRoot(
      IndexedAttestation indexedAttestation, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(indexedAttestation),
        indexedAttestation.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        indexedAttestation.hash_tree_root(),
        indexedAttestation.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZIndexedAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(IndexedAttestation.class, Arrays.asList("IndexedAttestation", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("IndexedAttestation", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("IndexedAttestation", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of PendingAttestation")
  @MethodSource("readMessageSSZPendingAttestation")
  void sszPendingAttestationCheckSerializationRoot(
      PendingAttestation pendingAttestation, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(pendingAttestation),
        pendingAttestation.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        pendingAttestation.hash_tree_root(),
        pendingAttestation.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZPendingAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(PendingAttestation.class, Arrays.asList("PendingAttestation", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("PendingAttestation", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("PendingAttestation", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of ProposerSlashing")
  @MethodSource("readMessageSSZProposerSlashing")
  void sszPendingAttestationCheckSerializationRoot(
      ProposerSlashing proposerSlashing, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(proposerSlashing),
        proposerSlashing.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        proposerSlashing.hash_tree_root(),
        proposerSlashing.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZProposerSlashing() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(ProposerSlashing.class, Arrays.asList("ProposerSlashing", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("ProposerSlashing", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("ProposerSlashing", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Transfer")
  @MethodSource("readMessageSSZTransfer")
  void sszTransferCheckSerializationRoot(Transfer transfer, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(transfer),
        transfer.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root, transfer.hash_tree_root(), transfer.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZTransfer() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Transfer.class, Arrays.asList("Transfer", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Transfer", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Transfer", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Validator")
  @MethodSource("readMessageSSZValidator")
  void sszValidatorCheckSerializationRoot(Validator validator, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(validator),
        validator.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root, validator.hash_tree_root(), validator.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZValidator() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Validator.class, Arrays.asList("Validator", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Validator", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Validator", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of VoluntaryExit")
  @MethodSource("readMessageSSZVoluntaryExit")
  void sszVoluntaryExitCheckSerializationRoot(
      VoluntaryExit voluntaryExit, Bytes serialized, Bytes32 root) {
    assertEquals(
        serialized,
        SimpleOffsetSerializer.serialize(voluntaryExit),
        voluntaryExit.getClass().getName() + " failed the serialiaztion test");
    assertEquals(
        root,
        voluntaryExit.hash_tree_root(),
        voluntaryExit.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZVoluntaryExit() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(VoluntaryExit.class, Arrays.asList("VoluntaryExit", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("VoluntaryExit", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("VoluntaryExit", "root")));

    return findTests(testFile, arguments);
  }
}
