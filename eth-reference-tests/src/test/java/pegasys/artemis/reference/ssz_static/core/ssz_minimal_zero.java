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

package pegasys.artemis.reference.ssz_static.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
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

@ExtendWith(BouncyCastleExtension.class)
class ssz_minimal_zero extends TestSuite {

  private static String testFile = "**/ssz_minimal_zero.yaml";

  @ParameterizedTest(name = "{index}. SSZ serialized, root, signing_root of Attestation")
  @MethodSource("readMessageSSZAttestation")
  void sszAttestationCheckSerializationRootAndSigningRoot(
      Attestation attestation, Bytes serialized, Bytes32 root, Bytes signing_root) {

    assertTrue(
        serialized.equals(attestation.toBytes()),
        attestation.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(attestation.hash_tree_root()),
        attestation.getClass().getName() + " failed the root test");
    assertTrue(
        root.equals(attestation.signing_root("signature")),
        attestation.getClass().getName() + " failed the signing_root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(Attestation.class, Arrays.asList("test_cases", "0", "Attestation", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "0", "Attestation", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "0", "Attestation", "root")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "0", "Attestation", "signing_root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of AttestationData")
  @MethodSource("readMessageSSZAttestationData")
  void sszAttestationDataCheckSerializationRoot(
      AttestationData data, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(data.toBytes()),
        data.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(data.hash_tree_root()), data.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationData() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            AttestationData.class, Arrays.asList("test_cases", "1", "AttestationData", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "1", "AttestationData", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "1", "AttestationData", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of AttestationDataAndCustodyBit")
  @MethodSource("readMessageSSZAttestationDataAndCustodyBit")
  void sszAttestationDataAndCustodyBitCheckSerializationRoot(
      AttestationDataAndCustodyBit dataAndCustodyBit, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(dataAndCustodyBit.toBytes()),
        dataAndCustodyBit.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(dataAndCustodyBit.hash_tree_root()),
        dataAndCustodyBit.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestationDataAndCustodyBit() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            AttestationDataAndCustodyBit.class,
            Arrays.asList("test_cases", "2", "AttestationDataAndCustodyBit", "value")));
    arguments.add(
        getParams(
            Bytes.class,
            Arrays.asList("test_cases", "2", "AttestationDataAndCustodyBit", "serialized")));
    arguments.add(
        getParams(
            Bytes32.class,
            Arrays.asList("test_cases", "2", "AttestationDataAndCustodyBit", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of AttesterSlashing")
  @MethodSource("readMessageSSZAttesterSlashing")
  void sszAttesterSlashingCheckSerializationRoot(
      AttesterSlashing attesterSlashing, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(attesterSlashing.toBytes()),
        attesterSlashing.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(attesterSlashing.hash_tree_root()),
        attesterSlashing.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttesterSlashing() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            AttesterSlashing.class, Arrays.asList("test_cases", "3", "AttesterSlashing", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "3", "AttesterSlashing", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "3", "AttesterSlashing", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconBlock")
  @MethodSource("readMessageSSZBeaconBlock")
  void sszBeaconBlockCheckSerializationRoot(
      BeaconBlock beaconBlock, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(beaconBlock.toBytes()),
        beaconBlock.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(beaconBlock.hash_tree_root()),
        beaconBlock.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlock() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(BeaconBlock.class, Arrays.asList("test_cases", "4", "BeaconBlock", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "4", "BeaconBlock", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "4", "BeaconBlock", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconBlockBody")
  @MethodSource("readMessageSSZBeaconBlockBody")
  void sszBeaconBlockBodyCheckSerializationRoot(
      BeaconBlockBody beaconBlockBody, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(beaconBlockBody.toBytes()),
        beaconBlockBody.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(beaconBlockBody.hash_tree_root()),
        beaconBlockBody.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockBody() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            BeaconBlockBody.class, Arrays.asList("test_cases", "5", "BeaconBlockBody", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "5", "BeaconBlockBody", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "5", "BeaconBlockBody", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconBlockHeader")
  @MethodSource("readMessageSSZBeaconBlockHeader")
  void sszBeaconBlockHeaderCheckSerializationRoot(
      BeaconBlockHeader beaconBlockHeader, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(beaconBlockHeader.toBytes()),
        beaconBlockHeader.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(beaconBlockHeader.hash_tree_root()),
        beaconBlockHeader.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconBlockHeader() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            BeaconBlockHeader.class,
            Arrays.asList("test_cases", "6", "BeaconBlockHeader", "value")));
    arguments.add(
        getParams(
            Bytes.class, Arrays.asList("test_cases", "6", "BeaconBlockHeader", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "6", "BeaconBlockHeader", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of BeaconState")
  @MethodSource("readMessageSSZBeaconState")
  void sszBeaconStateCheckSerializationRoot(
      BeaconState beaconState, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(beaconState.toBytes()),
        beaconState.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(beaconState.hash_tree_root()),
        beaconState.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZBeaconState() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(BeaconState.class, Arrays.asList("test_cases", "7", "BeaconState", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "7", "BeaconState", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "7", "BeaconState", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Checkpoint")
  @MethodSource("readMessageSSZCheckpoint")
  void sszCheckpointCheckSerializationRoot(Checkpoint checkpoint, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(checkpoint.toBytes()),
        checkpoint.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(checkpoint.hash_tree_root()),
        checkpoint.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCheckpoint() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(Checkpoint.class, Arrays.asList("test_cases", "8", "Checkpoint", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "8", "Checkpoint", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("test_cases", "8", "Checkpoint", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of CompactCommittee")
  @MethodSource("readMessageSSZCompactCommittee")
  void sszCompactCommitteeCheckSerializationRoot(
      CompactCommittee compactCommittee, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(compactCommittee.toBytes()),
        compactCommittee.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(compactCommittee.hash_tree_root()),
        compactCommittee.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCompactCommittee() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            CompactCommittee.class, Arrays.asList("test_cases", "9", "CompactCommittee", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "9", "CompactCommittee", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "9", "CompactCommittee", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Crosslink")
  @MethodSource("readMessageSSZCrosslink")
  void sszCrosslinkCheckSerializationRoot(Crosslink crosslink, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(crosslink.toBytes()),
        crosslink.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(crosslink.hash_tree_root()),
        crosslink.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZCrosslink() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(Crosslink.class, Arrays.asList("test_cases", "10", "Crosslink", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "10", "Crosslink", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("test_cases", "10", "Crosslink", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Deposit")
  @MethodSource("readMessageSSZDeposit")
  void sszDepositCheckSerializationRoot(Deposit deposit, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(deposit.toBytes()),
        deposit.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(deposit.hash_tree_root()),
        deposit.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZDeposit() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Deposit.class, Arrays.asList("test_cases", "11", "Deposit", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "11", "Deposit", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("test_cases", "11", "Deposit", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of DepositData")
  @MethodSource("readMessageSSZDepositData")
  void sszDepositDataCheckSerializationRoot(
      DepositData depositData, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(depositData.toBytes()),
        depositData.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(depositData.hash_tree_root()),
        depositData.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZDepositData() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(DepositData.class, Arrays.asList("test_cases", "12", "DepositData", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "12", "DepositData", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "12", "DepositData", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Eth1Data")
  @MethodSource("readMessageSSZEth1Data")
  void sszEth1DataCheckSerializationRoot(Eth1Data eth1Data, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(eth1Data.toBytes()),
        eth1Data.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(eth1Data.hash_tree_root()),
        eth1Data.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZEth1Data() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(Eth1Data.class, Arrays.asList("test_cases", "13", "Eth1Data", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "13", "Eth1Data", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("test_cases", "13", "Eth1Data", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Fork")
  @MethodSource("readMessageSSZFork")
  void sszForkCheckSerializationRoot(Fork fork, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(fork.toBytes()),
        fork.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(fork.hash_tree_root()), fork.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZFork() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Fork.class, Arrays.asList("test_cases", "14", "Fork", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("test_cases", "14", "Fork", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("test_cases", "14", "Fork", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of HistoricalBatch")
  @MethodSource("readMessageSSZHistoricalBatch")
  void sszHistoricalBatchCheckSerializationRoot(
      HistoricalBatch historicalBatch, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(historicalBatch.toBytes()),
        historicalBatch.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(historicalBatch.hash_tree_root()),
        historicalBatch.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZHistoricalBatch() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            HistoricalBatch.class, Arrays.asList("test_cases", "15", "HistoricalBatch", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "15", "HistoricalBatch", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "15", "HistoricalBatch", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of IndexedAttestation")
  @MethodSource("readMessageSSZIndexedAttestation")
  void sszIndexedAttestationCheckSerializationRoot(
      IndexedAttestation indexedAttestation, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(indexedAttestation.toBytes()),
        indexedAttestation.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(indexedAttestation.hash_tree_root()),
        indexedAttestation.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZIndexedAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            IndexedAttestation.class,
            Arrays.asList("test_cases", "16", "IndexedAttestation", "value")));
    arguments.add(
        getParams(
            Bytes.class, Arrays.asList("test_cases", "16", "IndexedAttestation", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "16", "IndexedAttestation", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of PendingAttestation")
  @MethodSource("readMessageSSZPendingAttestation")
  void sszPendingAttestationCheckSerializationRoot(
      PendingAttestation pendingAttestation, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(pendingAttestation.toBytes()),
        pendingAttestation.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(pendingAttestation.hash_tree_root()),
        pendingAttestation.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZPendingAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            PendingAttestation.class,
            Arrays.asList("test_cases", "17", "PendingAttestation", "value")));
    arguments.add(
        getParams(
            Bytes.class, Arrays.asList("test_cases", "17", "PendingAttestation", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "17", "PendingAttestation", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of ProposerSlashing")
  @MethodSource("readMessageSSZProposerSlashing")
  void sszPendingAttestationCheckSerializationRoot(
      ProposerSlashing proposerSlashing, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(proposerSlashing.toBytes()),
        proposerSlashing.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(proposerSlashing.hash_tree_root()),
        proposerSlashing.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZProposerSlashing() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            ProposerSlashing.class,
            Arrays.asList("test_cases", "18", "ProposerSlashing", "value")));
    arguments.add(
        getParams(
            Bytes.class, Arrays.asList("test_cases", "18", "ProposerSlashing", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "18", "ProposerSlashing", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Transfer")
  @MethodSource("readMessageSSZTransfer")
  void sszTransferCheckSerializationRoot(Transfer transfer, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(transfer.toBytes()),
        transfer.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(transfer.hash_tree_root()),
        transfer.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZTransfer() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(Transfer.class, Arrays.asList("test_cases", "19", "Transfer", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "19", "Transfer", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("test_cases", "19", "Transfer", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of Validator")
  @MethodSource("readMessageSSZValidator")
  void sszValidatorCheckSerializationRoot(Validator validator, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(validator.toBytes()),
        validator.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(validator.hash_tree_root()),
        validator.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZValidator() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(Validator.class, Arrays.asList("test_cases", "20", "Validator", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "20", "Validator", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("test_cases", "20", "Validator", "root")));

    return findTests(testFile, arguments);
  }

  @ParameterizedTest(name = "{index}. SSZ serialized, root of VoluntaryExit")
  @MethodSource("readMessageSSZVoluntaryExit")
  void sszVoluntaryExitCheckSerializationRoot(
      VoluntaryExit voluntaryExit, Bytes serialized, Bytes32 root) {
    assertTrue(
        serialized.equals(voluntaryExit.toBytes()),
        voluntaryExit.getClass().getName() + " failed the serialiaztion test");
    assertTrue(
        root.equals(voluntaryExit.hash_tree_root()),
        voluntaryExit.getClass().getName() + " failed the root test");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZVoluntaryExit() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(
        getParams(
            VoluntaryExit.class, Arrays.asList("test_cases", "21", "VoluntaryExit", "value")));
    arguments.add(
        getParams(Bytes.class, Arrays.asList("test_cases", "21", "VoluntaryExit", "serialized")));
    arguments.add(
        getParams(Bytes32.class, Arrays.asList("test_cases", "21", "VoluntaryExit", "root")));

    return findTests(testFile, arguments);
  }
}
