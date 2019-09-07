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

package tech.pegasys.artemis.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.io.Resources;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import tech.pegasys.artemis.datastructures.Constants;
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
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

@ExtendWith(BouncyCastleExtension.class)
class SSZStaticTestSuite {

  private static String testFile = "**/ssz_minimal_random.yaml";

  @ParameterizedTest(name = "{index}. Eth1Data Hash Tree Root Test")
  @MethodSource("readEth1Data")
  void testEth1DataHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Eth1Data eth1Data = parseEth1Data(value);

    assertEquals(Bytes32.fromHexString(root), eth1Data.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Eth1Data Serialization Test")
  @MethodSource("readEth1Data")
  void testEth1DataSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Eth1Data eth1Data = parseEth1Data(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(eth1Data));
  }

  @ParameterizedTest(name = "{index}. DepositData Hash Tree Root Test")
  @MethodSource("readDepositData")
  void testDepositDataHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    DepositData depositData = parseDepositData(value);

    assertEquals(Bytes32.fromHexString(root), depositData.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. DepositData Serialization Test")
  @MethodSource("readDepositData")
  void testDepositDataSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    DepositData depositData = parseDepositData(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(depositData));
  }

  @ParameterizedTest(name = "{index}. Deposit Hash Tree Root Test")
  @MethodSource("readDeposit")
  void testDepositHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Deposit deposit = parseDeposit(value);

    assertEquals(Bytes32.fromHexString(root), deposit.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Deposit Serialization Test")
  @MethodSource("readDeposit")
  void testDepositSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Deposit deposit = parseDeposit(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(deposit));
  }

  @ParameterizedTest(name = "{index}. BeaconBlockHeader Hash Tree Root Test")
  @MethodSource("readBeaconBlockHeader")
  void testBeaconBlockHeaderHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconBlockHeader beaconBlockHeader = parseBeaconBlockHeader(value);

    assertEquals(Bytes32.fromHexString(root), beaconBlockHeader.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. BeaconBlockHeader Serialization Test")
  @MethodSource("readBeaconBlockHeader")
  void testBeaconBlockHeaderSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconBlockHeader beaconBlockHeader = parseBeaconBlockHeader(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(beaconBlockHeader));
  }

  @ParameterizedTest(name = "{index}. ProposerSlashing Hash Tree Root Test")
  @MethodSource("readProposerSlashing")
  void testProposerSlashingHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    ProposerSlashing proposerSlashing = parseProposerSlashing(value);

    assertEquals(Bytes32.fromHexString(root), proposerSlashing.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. ProposerSlashing Serialization Test")
  @MethodSource("readProposerSlashing")
  void testProposerSlashingSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    ProposerSlashing proposerSlashing = parseProposerSlashing(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(proposerSlashing));
  }

  @ParameterizedTest(name = "{index}. Checkpoint Hash Tree Root Test")
  @MethodSource("readCheckpoint")
  void testCheckpointHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Checkpoint checkpoint = parseCheckpoint(value);

    assertEquals(Bytes32.fromHexString(root), checkpoint.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Checkpoint Serialization Test")
  @MethodSource("readCheckpoint")
  void testCheckpointSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Checkpoint checkpoint = parseCheckpoint(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(checkpoint));
  }

  @ParameterizedTest(name = "{index}. Crosslink Hash Tree Root Test")
  @MethodSource("readCrosslink")
  void testCrosslinkHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Crosslink crosslink = parseCrosslink(value);

    assertEquals(Bytes32.fromHexString(root), crosslink.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Crosslink Serialization Test")
  @MethodSource("readCrosslink")
  void testCrosslinkSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Crosslink crosslink = parseCrosslink(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(crosslink));
  }

  @ParameterizedTest(name = "{index}. AttestationData Hash Tree Root Test")
  @MethodSource("readAttestationData")
  void testAttestationDataHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    AttestationData attestationData = parseAttestationData(value);

    assertEquals(Bytes32.fromHexString(root), attestationData.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. AttestationData Serialization Test")
  @MethodSource("readAttestationData")
  void testAttestationDataSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    AttestationData attestationData = parseAttestationData(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(attestationData));
  }

  @ParameterizedTest(name = "{index}. IndexedAttestation Hash Tree Root Test")
  @MethodSource("readIndexedAttestation")
  void testIndexedAttestationHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    IndexedAttestation indexedAttestation = parseIndexedAttestation(value);

    assertEquals(Bytes32.fromHexString(root), indexedAttestation.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. IndexedAttestation Serialization Test")
  @MethodSource("readIndexedAttestation")
  void testIndexedAttestationSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    IndexedAttestation indexedAttestation = parseIndexedAttestation(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(indexedAttestation));
  }

  @ParameterizedTest(name = "{index}. AttesterSlashing Hash Tree Root Test")
  @MethodSource("readAttesterSlashing")
  void testAttesterSlashingHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    AttesterSlashing attesterSlashing = parseAttesterSlashing(value);

    assertEquals(Bytes32.fromHexString(root), attesterSlashing.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. AttesterSlashing Serialization Test")
  @MethodSource("readAttesterSlashing")
  void testAttesterSlashingSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    AttesterSlashing attesterSlashing = parseAttesterSlashing(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(attesterSlashing));
  }

  @ParameterizedTest(name = "{index}. Attestation Hash Tree Root Test")
  @MethodSource("readAttestation")
  void testAttestationHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Attestation attestation = parseAttestation(value);

    assertEquals(Bytes32.fromHexString(root), attestation.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Attestation Serialization Test")
  @MethodSource("readAttestation")
  void testAttestationSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Attestation attestation = parseAttestation(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(attestation));
  }

  @ParameterizedTest(name = "{index}. VoluntaryExit Hash Tree Root Test")
  @MethodSource("readVoluntaryExit")
  void testVoluntaryExitHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    VoluntaryExit voluntaryExit = parseVoluntaryExit(value);

    assertEquals(Bytes32.fromHexString(root), voluntaryExit.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. VoluntaryExit Serialization Test")
  @MethodSource("readVoluntaryExit")
  void testVoluntaryExitSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    VoluntaryExit voluntaryExit = parseVoluntaryExit(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(voluntaryExit));
  }

  @ParameterizedTest(name = "{index}. Transfer Hash Tree Root Test")
  @MethodSource("readTransfer")
  void testTransferHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Transfer transfer = parseTransfer(value);

    assertEquals(Bytes32.fromHexString(root), transfer.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Transfer Serialization Test")
  @MethodSource("readTransfer")
  void testTransferSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Transfer transfer = parseTransfer(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(transfer));
  }

  @ParameterizedTest(name = "{index}. Fork Hash Tree Root Test")
  @MethodSource("readFork")
  void testForkHashTreeRoot(LinkedHashMap<String, Object> value, String serialized, String root) {
    Fork fork = parseFork(value);

    assertEquals(Bytes32.fromHexString(root), fork.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Fork Serialization Test")
  @MethodSource("readFork")
  void testForkSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Fork fork = parseFork(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(fork));
  }

  @ParameterizedTest(name = "{index}. Validator Hash Tree Root Test")
  @MethodSource("readValidator")
  void testValidatorHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    Validator validator = parseValidator(value);

    assertEquals(Bytes32.fromHexString(root), validator.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Validator Serialization Test")
  @MethodSource("readValidator")
  void testValidatorSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Validator validator = parseValidator(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(validator));
  }

  @ParameterizedTest(name = "{index}. PendingAttestation Hash Tree Root Test")
  @MethodSource("readPendingAttestation")
  void testPendingAttestationHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    PendingAttestation pendingAttestation = parsePendingAttestation(value);

    assertEquals(Bytes32.fromHexString(root), pendingAttestation.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. PendingAttestation Serialization Test")
  @MethodSource("readPendingAttestation")
  void testPendingAttestationSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    PendingAttestation pendingAttestation = parsePendingAttestation(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(pendingAttestation));
  }

  @ParameterizedTest(name = "{index}. BeaconBlockBody Hash Tree Root Test")
  @MethodSource("readBeaconBlockBody")
  void testBeaconBlockBodyHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconBlockBody beaconBlockBody = parseBeaconBlockBody(value);

    assertEquals(Bytes32.fromHexString(root), beaconBlockBody.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. BeaconBlockBody Serialization Test")
  @MethodSource("readBeaconBlockBody")
  void testBeaconBlockBodySerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconBlockBody beaconBlockBody = parseBeaconBlockBody(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(beaconBlockBody));
  }

  @ParameterizedTest(name = "{index}. BeaconBlock Hash Tree Root Test")
  @MethodSource("readBeaconBlock")
  void testBeaconBlockHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconBlock beaconBlock = parseBeaconBlock(value);

    assertEquals(Bytes32.fromHexString(root), beaconBlock.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. BeaconBlock Serialization Test")
  @MethodSource("readBeaconBlock")
  void testBeaconBlockSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconBlock beaconBlock = parseBeaconBlock(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(beaconBlock));
  }

  @ParameterizedTest(name = "{index}. CompactCommittee Hash Tree Root Test")
  @MethodSource("readCompactCommittee")
  void testCompactCommitteeHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    CompactCommittee compactCommittee = parseCompactCommittee(value);

    assertEquals(Bytes32.fromHexString(root), compactCommittee.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. CompactCommittee Serialization Test")
  @MethodSource("readCompactCommittee")
  void testCompactCommitteeSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    CompactCommittee compactCommittee = parseCompactCommittee(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(compactCommittee));
  }

  @ParameterizedTest(name = "{index}. BeaconState Hash Tree Root Test")
  @MethodSource("readBeaconState")
  void testBeaconStateHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconState beaconState = parseBeaconState(value);

    Bytes32 a = Bytes32.fromHexString(root);
    Bytes32 b = beaconState.hash_tree_root();
    assertEquals(Bytes32.fromHexString(root), beaconState.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. BeaconState Serialization Test")
  @MethodSource("readBeaconState")
  void testBeaconStateSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconState beaconState = parseBeaconState(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(beaconState));
  }

  @ParameterizedTest(name = "{index}. AttestationDataAndCustodyBit Hash Tree Root Test")
  @MethodSource("readAttestationDataAndCustodyBit")
  void testAttestationDataAndCustodyBitHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    AttestationDataAndCustodyBit attestationDataAndCustodyBit =
        parseAttestationDataAndCustodyBit(value);

    assertEquals(Bytes32.fromHexString(root), attestationDataAndCustodyBit.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. AttestationDataAndCustodyBit Serialization Test")
  @MethodSource("readAttestationDataAndCustodyBit")
  void testAttestationDataAndCustodyBitSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    AttestationDataAndCustodyBit attestationDataAndCustodyBit =
        parseAttestationDataAndCustodyBit(value);

    assertEquals(
        Bytes.fromHexString(serialized),
        SimpleOffsetSerializer.serialize(attestationDataAndCustodyBit));
  }

  @ParameterizedTest(name = "{index}. HistoricalBatch Hash Tree Root Test")
  @MethodSource("readHistoricalBatch")
  void testHistoricalBatchHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    HistoricalBatch historicalBatch = parseHistoricalBatch(value);

    assertEquals(Bytes32.fromHexString(root), historicalBatch.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. HistoricalBatch Serialization Test")
  @MethodSource("readHistoricalBatch")
  void testHistoricalBatchSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    HistoricalBatch historicalBatch = parseHistoricalBatch(value);

    assertEquals(
        Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(historicalBatch));
  }

  private Eth1Data parseEth1Data(LinkedHashMap<String, Object> value) {
    Bytes32 depositRoot = Bytes32.fromHexString((String) value.get("deposit_root"));
    UnsignedLong depositCount = UnsignedLong.valueOf((BigInteger) value.get("deposit_count"));
    Bytes32 blockHash = Bytes32.fromHexString((String) value.get("block_hash"));

    Eth1Data eth1Data = new Eth1Data(depositRoot, depositCount, blockHash);
    return eth1Data;
  }

  private DepositData parseDepositData(LinkedHashMap<String, Object> value) {
    BLSPublicKey pubkeyMock = mockBLSPublicKey(value);
    Bytes32 withdrawalCredentials =
        Bytes32.fromHexString((String) value.get("withdrawal_credentials"));
    UnsignedLong amount = UnsignedLong.valueOf((BigInteger) value.get("amount"));
    BLSSignature signatureMock = mockBLSSignature(value);

    DepositData depositData =
        new DepositData(pubkeyMock, withdrawalCredentials, amount, signatureMock);
    return depositData;
  }

  @SuppressWarnings({"unchecked"})
  private Deposit parseDeposit(LinkedHashMap<String, Object> value) {
    SSZVector<Bytes32> proof =
        new SSZVector<>(
            ((List<String>) value.get("proof"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);
    DepositData data = parseDepositData((LinkedHashMap<String, Object>) value.get("data"));

    Deposit deposit = new Deposit(proof, data);
    return deposit;
  }

  private BeaconBlockHeader parseBeaconBlockHeader(LinkedHashMap<String, Object> value) {
    UnsignedLong slot = UnsignedLong.valueOf((BigInteger) value.get("slot"));
    Bytes32 parentRoot = Bytes32.fromHexString((String) value.get("parent_root"));
    Bytes32 stateRoot = Bytes32.fromHexString((String) value.get("state_root"));
    Bytes32 bodyRoot = Bytes32.fromHexString((String) value.get("body_root"));
    BLSSignature signatureMock = mockBLSSignature(value);

    BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(slot, parentRoot, stateRoot, bodyRoot, signatureMock);
    return beaconBlockHeader;
  }

  @SuppressWarnings({"unchecked"})
  private ProposerSlashing parseProposerSlashing(LinkedHashMap<String, Object> value) {
    UnsignedLong proposerIndex = UnsignedLong.valueOf((BigInteger) value.get("proposer_index"));
    BeaconBlockHeader header1 =
        parseBeaconBlockHeader((LinkedHashMap<String, Object>) value.get("header_1"));
    BeaconBlockHeader header2 =
        parseBeaconBlockHeader((LinkedHashMap<String, Object>) value.get("header_2"));

    ProposerSlashing proposerSlashing = new ProposerSlashing(proposerIndex, header1, header2);
    return proposerSlashing;
  }

  private Checkpoint parseCheckpoint(LinkedHashMap<String, Object> value) {
    UnsignedLong epoch = UnsignedLong.valueOf((BigInteger) value.get("epoch"));
    Bytes32 checkpointRoot = Bytes32.fromHexString((String) value.get("root"));

    Checkpoint checkpoint = new Checkpoint(epoch, checkpointRoot);
    return checkpoint;
  }

  private Crosslink parseCrosslink(LinkedHashMap<String, Object> value) {
    UnsignedLong shard = UnsignedLong.valueOf((BigInteger) value.get("shard"));
    Bytes32 parentRoot = Bytes32.fromHexString((String) value.get("parent_root"));
    UnsignedLong startEpoch = UnsignedLong.valueOf((BigInteger) value.get("start_epoch"));
    UnsignedLong endEpoch = UnsignedLong.valueOf((BigInteger) value.get("end_epoch"));
    Bytes32 dataRoot = Bytes32.fromHexString((String) value.get("data_root"));

    Crosslink crosslink = new Crosslink(shard, parentRoot, startEpoch, endEpoch, dataRoot);
    return crosslink;
  }

  @SuppressWarnings({"unchecked"})
  private AttestationData parseAttestationData(LinkedHashMap<String, Object> value) {
    Bytes32 beaconBlockRoot = Bytes32.fromHexString((String) value.get("beacon_block_root"));
    Checkpoint source = parseCheckpoint((LinkedHashMap<String, Object>) value.get("source"));
    Checkpoint target = parseCheckpoint((LinkedHashMap<String, Object>) value.get("target"));
    Crosslink crosslink = parseCrosslink((LinkedHashMap<String, Object>) value.get("crosslink"));

    AttestationData attestationData =
        new AttestationData(beaconBlockRoot, source, target, crosslink);
    return attestationData;
  }

  @SuppressWarnings({"unchecked"})
  private IndexedAttestation parseIndexedAttestation(LinkedHashMap<String, Object> value) {
    SSZList<UnsignedLong> custodyBit0Indices =
        new SSZList<>(
            ((List<BigInteger>) value.get("custody_bit_0_indices"))
                .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList()),
            Constants.MAX_VALIDATORS_PER_COMMITTEE,
            UnsignedLong.class);
    SSZList<UnsignedLong> custodyBit1Indices =
        new SSZList<>(
            ((List<BigInteger>) value.get("custody_bit_1_indices"))
                .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList()),
            Constants.MAX_VALIDATORS_PER_COMMITTEE,
            UnsignedLong.class);
    AttestationData data = parseAttestationData((LinkedHashMap<String, Object>) value.get("data"));
    BLSSignature signatureMock = mockBLSSignature(value);

    IndexedAttestation indexedAttestation =
        new IndexedAttestation(custodyBit0Indices, custodyBit1Indices, data, signatureMock);
    return indexedAttestation;
  }

  @SuppressWarnings({"unchecked"})
  private AttesterSlashing parseAttesterSlashing(LinkedHashMap<String, Object> value) {
    IndexedAttestation attestation1 =
        parseIndexedAttestation((LinkedHashMap<String, Object>) value.get("attestation_1"));
    IndexedAttestation attestation2 =
        parseIndexedAttestation((LinkedHashMap<String, Object>) value.get("attestation_2"));

    AttesterSlashing attesterSlashing = new AttesterSlashing(attestation1, attestation2);
    return attesterSlashing;
  }

  @SuppressWarnings({"unchecked"})
  private Attestation parseAttestation(LinkedHashMap<String, Object> value) {
    // TODO Commented code below will be enabled once we shift from using Bytes to a real Bitlist
    // type. As currently implemented, we need to keep the leading 1 bit in memory to determine
    // length.
    Bitlist serializedAggregationBits =
        Bitlist.fromBytes(
            Bytes.fromHexString((String) value.get("aggregation_bits")),
            Constants.MAX_VALIDATORS_PER_COMMITTEE);
    // Bytes aggregationBitsMask = Bytes.minimalBytes((int) Math.pow(2.0,
    // serializedAggregationBits.bitLength() - 1) - 1);
    // Bytes aggregationBits = serializedAggregationBits.and(aggregationBitsMask);
    AttestationData data = parseAttestationData((LinkedHashMap<String, Object>) value.get("data"));
    Bitlist serializedCustodyBits =
        Bitlist.fromBytes(
            Bytes.fromHexString((String) value.get("custody_bits")),
            Constants.MAX_VALIDATORS_PER_COMMITTEE);
    // Bytes custodyBitsMask = Bytes.minimalBytes((int) Math.pow(2.0,
    // serializedCustodyBits.bitLength() - 1) - 1);
    // Bytes custodyBits = serializedAggregationBits.and(custodyBitsMask);
    BLSSignature signatureMock = mockBLSSignature(value);

    // Attestation attestation = new Attestation(aggregationBits, data, custodyBits, signatureMock);
    Attestation attestation =
        new Attestation(serializedAggregationBits, data, serializedCustodyBits, signatureMock);
    return attestation;
  }

  private VoluntaryExit parseVoluntaryExit(LinkedHashMap<String, Object> value) {
    UnsignedLong epoch = UnsignedLong.valueOf((BigInteger) value.get("epoch"));
    UnsignedLong validatorIndex = UnsignedLong.valueOf((BigInteger) value.get("validator_index"));
    BLSSignature signatureMock = mockBLSSignature(value);

    VoluntaryExit voluntaryExit = new VoluntaryExit(epoch, validatorIndex, signatureMock);
    return voluntaryExit;
  }

  private Transfer parseTransfer(LinkedHashMap<String, Object> value) {
    UnsignedLong sender = UnsignedLong.valueOf((BigInteger) value.get("sender"));
    UnsignedLong recipient = UnsignedLong.valueOf((BigInteger) value.get("recipient"));
    UnsignedLong amount = UnsignedLong.valueOf((BigInteger) value.get("amount"));
    UnsignedLong fee = UnsignedLong.valueOf((BigInteger) value.get("fee"));
    UnsignedLong slot = UnsignedLong.valueOf((BigInteger) value.get("slot"));
    BLSPublicKey pubkeyMock = mockBLSPublicKey(value);
    BLSSignature signatureMock = mockBLSSignature(value);

    Transfer transfer =
        new Transfer(sender, recipient, amount, fee, slot, pubkeyMock, signatureMock);
    return transfer;
  }

  private Fork parseFork(LinkedHashMap<String, Object> value) {
    Bytes4 previousVersion =
        new Bytes4(Bytes.fromHexString((String) value.get("previous_version")));
    Bytes4 currentVersion = new Bytes4(Bytes.fromHexString((String) value.get("current_version")));
    UnsignedLong epoch = UnsignedLong.valueOf((BigInteger) value.get("epoch"));

    Fork fork = new Fork(previousVersion, currentVersion, epoch);
    return fork;
  }

  private Validator parseValidator(LinkedHashMap<String, Object> value) {
    BLSPublicKey pubkeyMock = mockBLSPublicKey(value);
    Bytes32 withdrawalCredentials =
        Bytes32.fromHexString((String) value.get("withdrawal_credentials"));
    UnsignedLong effectiveBalance =
        UnsignedLong.valueOf((BigInteger) value.get("effective_balance"));
    boolean slashed = (boolean) value.get("slashed");
    UnsignedLong activationEligibilityEpoch =
        UnsignedLong.valueOf((BigInteger) value.get("activation_eligibility_epoch"));
    UnsignedLong activationEpoch = UnsignedLong.valueOf((BigInteger) value.get("activation_epoch"));
    UnsignedLong exitEpoch = UnsignedLong.valueOf((BigInteger) value.get("exit_epoch"));
    UnsignedLong withdrawableEpoch =
        UnsignedLong.valueOf((BigInteger) value.get("withdrawable_epoch"));

    Validator validator =
        new Validator(
            pubkeyMock,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawableEpoch);
    return validator;
  }

  @SuppressWarnings({"unchecked"})
  private PendingAttestation parsePendingAttestation(LinkedHashMap<String, Object> value) {
    // TODO Commented code below will be enabled once we shift from using Bytes to a real Bitlist
    // type. As currently implemented, we need to keep the leading 1 bit in memory to determine
    // length.
    Bitlist serializedAggregationBits =
        Bitlist.fromBytes(
            Bytes.fromHexString((String) value.get("aggregation_bits")),
            Constants.MAX_VALIDATORS_PER_COMMITTEE);
    // Bytes aggregationBitsMask = Bytes.minimalBytes((int) Math.pow(2.0,
    // serializedAggregationBits.bitLength() - 1) - 1);
    // Bytes aggregationBits = serializedAggregationBits.and(aggregationBitsMask);
    AttestationData data = parseAttestationData((LinkedHashMap<String, Object>) value.get("data"));
    UnsignedLong inclusionDelay = UnsignedLong.valueOf((BigInteger) value.get("inclusion_delay"));
    UnsignedLong proposerIndex = UnsignedLong.valueOf((BigInteger) value.get("proposer_index"));

    PendingAttestation pendingAttestation =
        new PendingAttestation(serializedAggregationBits, data, inclusionDelay, proposerIndex);
    return pendingAttestation;
  }

  @SuppressWarnings({"unchecked"})
  private BeaconBlockBody parseBeaconBlockBody(LinkedHashMap<String, Object> value) {
    BLSSignature randaoRevealMock = mockBLSSignature(value, "randao_reveal");
    Eth1Data eth1Data = parseEth1Data((LinkedHashMap<String, Object>) value.get("eth1_data"));
    Bytes32 graffiti = Bytes32.fromHexString((String) value.get("graffiti"));
    SSZList<ProposerSlashing> proposerSlashings =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("proposer_slashings"))
                .stream().map(map -> parseProposerSlashing(map)).collect(Collectors.toList()),
            Constants.MAX_PROPOSER_SLASHINGS,
            ProposerSlashing.class);
    SSZList<AttesterSlashing> attesterSlashings =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("attester_slashings"))
                .stream().map(map -> parseAttesterSlashing(map)).collect(Collectors.toList()),
            Constants.MAX_ATTESTER_SLASHINGS,
            AttesterSlashing.class);
    SSZList<Attestation> attestations =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("attestations"))
                .stream().map(map -> parseAttestation(map)).collect(Collectors.toList()),
            Constants.MAX_ATTESTATIONS,
            Attestation.class);
    SSZList<Deposit> deposits =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("deposits"))
                .stream().map(map -> parseDeposit(map)).collect(Collectors.toList()),
            Constants.MAX_DEPOSITS,
            Deposit.class);
    SSZList<VoluntaryExit> voluntaryExits =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("voluntary_exits"))
                .stream().map(map -> parseVoluntaryExit(map)).collect(Collectors.toList()),
            Constants.MAX_VOLUNTARY_EXITS,
            VoluntaryExit.class);
    SSZList<Transfer> transfers =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("transfers"))
                .stream().map(map -> parseTransfer(map)).collect(Collectors.toList()),
            Constants.MAX_TRANSFERS,
            Transfer.class);

    BeaconBlockBody beaconBlockBody =
        new BeaconBlockBody(
            randaoRevealMock,
            eth1Data,
            graffiti,
            proposerSlashings,
            attesterSlashings,
            attestations,
            deposits,
            voluntaryExits,
            transfers);
    return beaconBlockBody;
  }

  @SuppressWarnings({"unchecked"})
  private BeaconBlock parseBeaconBlock(LinkedHashMap<String, Object> value) {
    UnsignedLong slot = UnsignedLong.valueOf((BigInteger) value.get("slot"));
    Bytes32 parentRoot = Bytes32.fromHexString((String) value.get("parent_root"));
    Bytes32 stateRoot = Bytes32.fromHexString((String) value.get("state_root"));
    BeaconBlockBody blockBody =
        parseBeaconBlockBody((LinkedHashMap<String, Object>) value.get("body"));
    BLSSignature signatureMock = mockBLSSignature(value);

    BeaconBlock block = new BeaconBlock(slot, parentRoot, stateRoot, blockBody, signatureMock);
    return block;
  }

  @SuppressWarnings({"unchecked"})
  private CompactCommittee parseCompactCommittee(LinkedHashMap<String, Object> value) {
    SSZList<BLSPublicKey> pubkeys =
        new SSZList<>(
            ((List<String>) value.get("pubkeys"))
                .stream()
                    .map(pubkey -> mockBLSPublicKey(Bytes.fromHexString(pubkey)))
                    .collect(Collectors.toList()),
            Constants.MAX_VALIDATORS_PER_COMMITTEE,
            BLSPublicKey.class);
    SSZList<UnsignedLong> compactValidators =
        new SSZList<>(
            ((List<BigInteger>) value.get("compact_validators"))
                .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList()),
            Constants.MAX_VALIDATORS_PER_COMMITTEE,
            UnsignedLong.class);
    CompactCommittee compactCommittee = new CompactCommittee(pubkeys, compactValidators);
    return compactCommittee;
  }

  @SuppressWarnings({"unchecked"})
  private BeaconState parseBeaconState(LinkedHashMap<String, Object> value) {
    UnsignedLong genesisTime = UnsignedLong.valueOf((BigInteger) value.get("genesis_time"));
    UnsignedLong slot = UnsignedLong.valueOf((BigInteger) value.get("slot"));
    Fork fork = parseFork((LinkedHashMap<String, Object>) value.get("fork"));

    BeaconBlockHeader latestBlockHeader =
        parseBeaconBlockHeader((LinkedHashMap<String, Object>) value.get("latest_block_header"));
    SSZVector<Bytes32> blockRoots =
        new SSZVector<>(
            ((List<String>) value.get("block_roots"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);
    SSZVector<Bytes32> stateRoots =
        new SSZVector<>(
            ((List<String>) value.get("state_roots"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);
    SSZList<Bytes32> historicalRoots =
        new SSZList<>(
            ((List<String>) value.get("historical_roots"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Constants.HISTORICAL_ROOTS_LIMIT,
            Bytes32.class);

    Eth1Data eth1Data = parseEth1Data((LinkedHashMap<String, Object>) value.get("eth1_data"));
    SSZList<Eth1Data> eth1DataVotes =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("eth1_data_votes"))
                .stream().map(map -> parseEth1Data(map)).collect(Collectors.toList()),
            Constants.SLOTS_PER_ETH1_VOTING_PERIOD,
            Eth1Data.class);
    UnsignedLong eth1DepositIndex =
        UnsignedLong.valueOf((BigInteger) value.get("eth1_deposit_index"));

    SSZList<Validator> validators =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("validators"))
                .stream().map(map -> parseValidator(map)).collect(Collectors.toList()),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            Validator.class);
    SSZList<UnsignedLong> balances =
        new SSZList<>(
            ((List<BigInteger>) value.get("balances"))
                .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList()),
            Constants.VALIDATOR_REGISTRY_LIMIT,
            UnsignedLong.class);

    UnsignedLong startShard = UnsignedLong.valueOf((BigInteger) value.get("start_shard"));
    SSZVector<Bytes32> randaoMixes =
        new SSZVector<>(
            ((List<String>) value.get("randao_mixes"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);
    SSZVector<Bytes32> activeIndexRoots =
        new SSZVector<>(
            ((List<String>) value.get("active_index_roots"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);
    SSZVector<Bytes32> compactCommitteesRoots =
        new SSZVector<>(
            ((List<String>) value.get("compact_committees_roots"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);

    SSZVector<UnsignedLong> slashings =
        new SSZVector<>(
            ((List<BigInteger>) value.get("slashings"))
                .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList()),
            UnsignedLong.class);

    SSZList<PendingAttestation> previousEpochAttestations =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("previous_epoch_attestations"))
                .stream().map(map -> parsePendingAttestation(map)).collect(Collectors.toList()),
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            PendingAttestation.class);
    SSZList<PendingAttestation> currentEpochAttestations =
        new SSZList<>(
            ((List<LinkedHashMap<String, Object>>) value.get("current_epoch_attestations"))
                .stream().map(map -> parsePendingAttestation(map)).collect(Collectors.toList()),
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            PendingAttestation.class);

    SSZVector<Crosslink> previousCrosslinks =
        new SSZVector<>(
            ((List<LinkedHashMap<String, Object>>) value.get("previous_crosslinks"))
                .stream().map(map -> parseCrosslink(map)).collect(Collectors.toList()),
            Crosslink.class);
    SSZVector<Crosslink> currentCrosslinks =
        new SSZVector<>(
            ((List<LinkedHashMap<String, Object>>) value.get("current_crosslinks"))
                .stream().map(map -> parseCrosslink(map)).collect(Collectors.toList()),
            Crosslink.class);

    Bitvector serializedJustificationBits =
        Bitvector.fromBytes(
            Bytes.fromHexString((String) value.get("justification_bits")),
            Constants.JUSTIFICATION_BITS_LENGTH);
    Checkpoint previousJustifiedCheckpoint =
        parseCheckpoint((LinkedHashMap<String, Object>) value.get("previous_justified_checkpoint"));
    Checkpoint currentJustifiedCheckpoint =
        parseCheckpoint((LinkedHashMap<String, Object>) value.get("current_justified_checkpoint"));
    Checkpoint finalizedCheckpoint =
        parseCheckpoint((LinkedHashMap<String, Object>) value.get("finalized_checkpoint"));

    BeaconState beaconState =
        new BeaconState(
            genesisTime,
            slot,
            fork,
            latestBlockHeader,
            blockRoots,
            stateRoots,
            historicalRoots,
            eth1Data,
            eth1DataVotes,
            eth1DepositIndex,
            validators,
            balances,
            startShard,
            randaoMixes,
            activeIndexRoots,
            compactCommitteesRoots,
            slashings,
            previousEpochAttestations,
            currentEpochAttestations,
            previousCrosslinks,
            currentCrosslinks,
            serializedJustificationBits,
            previousJustifiedCheckpoint,
            currentJustifiedCheckpoint,
            finalizedCheckpoint);
    return beaconState;
  }

  @SuppressWarnings({"unchecked"})
  private AttestationDataAndCustodyBit parseAttestationDataAndCustodyBit(
      LinkedHashMap<String, Object> value) {
    AttestationData data = parseAttestationData((LinkedHashMap<String, Object>) value.get("data"));
    boolean custodyBit = (boolean) value.get("custody_bit");
    AttestationDataAndCustodyBit attestationDataAndCustodyBit =
        new AttestationDataAndCustodyBit(data, custodyBit);
    return attestationDataAndCustodyBit;
  }

  @SuppressWarnings({"unchecked"})
  private HistoricalBatch parseHistoricalBatch(LinkedHashMap<String, Object> value) {
    SSZVector<Bytes32> blockRoots =
        new SSZVector<>(
            ((List<String>) value.get("block_roots"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);
    SSZVector<Bytes32> stateRoots =
        new SSZVector<>(
            ((List<String>) value.get("state_roots"))
                .stream()
                    .map(proofString -> Bytes32.fromHexString(proofString))
                    .collect(Collectors.toList()),
            Bytes32.class);

    HistoricalBatch historicalBatch = new HistoricalBatch(blockRoots, stateRoots);
    return historicalBatch;
  }

  private BLSPublicKey mockBLSPublicKey(Bytes pubkeyBytes) {
    return mockBLSPublicKeyHelper(pubkeyBytes);
  }

  private BLSPublicKey mockBLSPublicKey(LinkedHashMap<String, Object> value) {
    Bytes pubkeyBytes = Bytes.fromHexString((String) value.get("pubkey"));
    return mockBLSPublicKeyHelper(pubkeyBytes);
  }

  private BLSPublicKey mockBLSPublicKeyHelper(Bytes pubkeyBytes) {
    BLSPublicKey pubkeyMock = Mockito.mock(BLSPublicKey.class);
    Mockito.when(pubkeyMock.toBytes()).thenReturn(pubkeyBytes);
    Mockito.when(pubkeyMock.get_fixed_parts()).thenReturn(List.of(pubkeyBytes));
    Mockito.when(pubkeyMock.getSSZFieldCount()).thenReturn(1);
    return pubkeyMock;
  }

  private BLSSignature mockBLSSignature(LinkedHashMap<String, Object> value) {
    Bytes signatureBytes = Bytes.fromHexString((String) value.get("signature"));
    return mockBLSSignatureHelper(signatureBytes);
  }

  private BLSSignature mockBLSSignature(LinkedHashMap<String, Object> value, String paramName) {
    Bytes signatureBytes = Bytes.fromHexString((String) value.get(paramName));
    return mockBLSSignatureHelper(signatureBytes);
  }

  private BLSSignature mockBLSSignatureHelper(Bytes signatureBytes) {
    BLSSignature signatureMock = Mockito.mock(BLSSignature.class);
    Mockito.when(signatureMock.toBytes()).thenReturn(signatureBytes);
    Mockito.when(signatureMock.get_fixed_parts()).thenReturn(List.of(signatureBytes));
    Mockito.when(signatureMock.getSSZFieldCount()).thenReturn(1);
    return signatureMock;
  }

  @MustBeClosed
  private static Stream<Arguments> findTests(String glob, String tcase) throws IOException {
    return Resources.find(glob)
        .flatMap(
            url -> {
              try (InputStream in = url.openConnection().getInputStream()) {
                return prepareTests(in, tcase);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  @MustBeClosed
  private static Stream<Arguments> readEth1Data() throws IOException {
    return findTests(testFile, "Eth1Data");
  }

  @MustBeClosed
  private static Stream<Arguments> readDepositData() throws IOException {
    return findTests(testFile, "DepositData");
  }

  @MustBeClosed
  private static Stream<Arguments> readDeposit() throws IOException {
    return findTests(testFile, "Deposit");
  }

  @MustBeClosed
  private static Stream<Arguments> readBeaconBlockHeader() throws IOException {
    return findTests(testFile, "BeaconBlockHeader");
  }

  @MustBeClosed
  private static Stream<Arguments> readProposerSlashing() throws IOException {
    return findTests(testFile, "ProposerSlashing");
  }

  @MustBeClosed
  private static Stream<Arguments> readCheckpoint() throws IOException {
    return findTests(testFile, "Checkpoint");
  }

  @MustBeClosed
  private static Stream<Arguments> readCrosslink() throws IOException {
    return findTests(testFile, "Crosslink");
  }

  @MustBeClosed
  private static Stream<Arguments> readAttestationData() throws IOException {
    return findTests(testFile, "AttestationData");
  }

  @MustBeClosed
  private static Stream<Arguments> readIndexedAttestation() throws IOException {
    return findTests(testFile, "IndexedAttestation");
  }

  @MustBeClosed
  private static Stream<Arguments> readAttesterSlashing() throws IOException {
    return findTests(testFile, "AttesterSlashing");
  }

  @MustBeClosed
  private static Stream<Arguments> readAttestation() throws IOException {
    return findTests(testFile, "Attestation");
  }

  @MustBeClosed
  private static Stream<Arguments> readVoluntaryExit() throws IOException {
    return findTests(testFile, "VoluntaryExit");
  }

  @MustBeClosed
  private static Stream<Arguments> readTransfer() throws IOException {
    return findTests(testFile, "Transfer");
  }

  @MustBeClosed
  private static Stream<Arguments> readFork() throws IOException {
    return findTests(testFile, "Fork");
  }

  @MustBeClosed
  private static Stream<Arguments> readValidator() throws IOException {
    return findTests(testFile, "Validator");
  }

  @MustBeClosed
  private static Stream<Arguments> readPendingAttestation() throws IOException {
    return findTests(testFile, "PendingAttestation");
  }

  @MustBeClosed
  private static Stream<Arguments> readBeaconBlockBody() throws IOException {
    return findTests(testFile, "BeaconBlockBody");
  }

  @MustBeClosed
  private static Stream<Arguments> readBeaconBlock() throws IOException {
    return findTests(testFile, "BeaconBlock");
  }

  @MustBeClosed
  private static Stream<Arguments> readCompactCommittee() throws IOException {
    return findTests(testFile, "CompactCommittee");
  }

  @MustBeClosed
  private static Stream<Arguments> readBeaconState() throws IOException {
    return findTests(testFile, "BeaconState");
  }

  @MustBeClosed
  private static Stream<Arguments> readAttestationDataAndCustodyBit() throws IOException {
    return findTests(testFile, "AttestationDataAndCustodyBit");
  }

  @MustBeClosed
  private static Stream<Arguments> readHistoricalBatch() throws IOException {
    return findTests(testFile, "HistoricalBatch");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Stream<Arguments> prepareTests(InputStream in, String tcase) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map allTests =
        mapper
            .readerFor(Map.class)
            .with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .readValue(in);
    List<Map> testCaseList =
        ((List<Map>) allTests.get("test_cases"))
            .stream().filter(testCase -> testCase.containsKey(tcase)).collect(Collectors.toList());

    return testCaseList.stream()
        .map(
            testCase ->
                Arguments.of(
                    ((Map) testCase.get(tcase)).get("value"),
                    ((Map) testCase.get(tcase)).get("serialized"),
                    ((Map) testCase.get(tcase)).get("root")));
  }
}
