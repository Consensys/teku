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
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

@ExtendWith(BouncyCastleExtension.class)
class SSZStaticTestSuite {

  private static String testFile = "**/ssz_minimal_nil_tmp.yaml";

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

  @ParameterizedTest(name = "{index}. BeaconState Hash Tree Root Test")
  @MethodSource("readBeaconState")
  void testBeaconStateHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconState beaconState = parseBeaconState(value);

    assertEquals(Bytes32.fromHexString(root), beaconState.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. BeaconState Serialization Test")
  @MethodSource("readBeaconState")
  void testBeaconStateSerialize(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    BeaconState beaconState = parseBeaconState(value);

    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(beaconState));
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
    List<Bytes32> proof =
        ((List<String>) value.get("proof"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());
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
    List<UnsignedLong> custodyBit0Indices =
        ((List<BigInteger>) value.get("custody_bit_0_indices"))
            .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList());
    List<UnsignedLong> custodyBit1Indices =
        ((List<BigInteger>) value.get("custody_bit_1_indices"))
            .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList());
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
    Bytes serializedAggregationBits = Bytes.fromHexString((String) value.get("aggregation_bits"));
    // Bytes aggregationBitsMask = Bytes.minimalBytes((int) Math.pow(2.0,
    // serializedAggregationBits.bitLength() - 1) - 1);
    // Bytes aggregationBits = serializedAggregationBits.and(aggregationBitsMask);
    AttestationData data = parseAttestationData((LinkedHashMap<String, Object>) value.get("data"));
    Bytes serializedCustodyBits = Bytes.fromHexString((String) value.get("custody_bits"));
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
    Bytes previousVersion = Bytes.fromHexString((String) value.get("previous_version"));
    Bytes currentVersion = Bytes.fromHexString((String) value.get("current_version"));
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
    Bytes serializedAggregationBits = Bytes.fromHexString((String) value.get("aggregation_bits"));
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
    List<ProposerSlashing> proposerSlashings =
        ((List<LinkedHashMap<String, Object>>) value.get("proposer_slashings"))
            .stream().map(map -> parseProposerSlashing(map)).collect(Collectors.toList());
    List<AttesterSlashing> attesterSlashings =
        ((List<LinkedHashMap<String, Object>>) value.get("attester_slashings"))
            .stream().map(map -> parseAttesterSlashing(map)).collect(Collectors.toList());
    List<Attestation> attestations =
        ((List<LinkedHashMap<String, Object>>) value.get("attestations"))
            .stream().map(map -> parseAttestation(map)).collect(Collectors.toList());
    List<Deposit> deposits =
        ((List<LinkedHashMap<String, Object>>) value.get("deposits"))
            .stream().map(map -> parseDeposit(map)).collect(Collectors.toList());
    List<VoluntaryExit> voluntaryExits =
        ((List<LinkedHashMap<String, Object>>) value.get("voluntary_exits"))
            .stream().map(map -> parseVoluntaryExit(map)).collect(Collectors.toList());
    List<Transfer> transfers =
        ((List<LinkedHashMap<String, Object>>) value.get("transfers"))
            .stream().map(map -> parseTransfer(map)).collect(Collectors.toList());

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
  private BeaconState parseBeaconState(LinkedHashMap<String, Object> value) {
    UnsignedLong genesisTime = UnsignedLong.valueOf((BigInteger) value.get("genesis_time"));
    UnsignedLong slot = UnsignedLong.valueOf((BigInteger) value.get("slot"));
    Fork fork = parseFork((LinkedHashMap<String, Object>) value.get("fork"));

    BeaconBlockHeader latestBlockHeader =
        parseBeaconBlockHeader((LinkedHashMap<String, Object>) value.get("latest_block_header"));
    List<Bytes32> blockRoots =
        ((List<String>) value.get("block_roots"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());
    List<Bytes32> stateRoots =
        ((List<String>) value.get("state_roots"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());
    List<Bytes32> historicalRoots =
        ((List<String>) value.get("historical_roots"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());

    Eth1Data eth1Data = parseEth1Data((LinkedHashMap<String, Object>) value.get("eth1_data"));
    List<Eth1Data> eth1DataVotes =
        ((List<LinkedHashMap<String, Object>>) value.get("eth1_data_votes"))
            .stream().map(map -> parseEth1Data(map)).collect(Collectors.toList());
    UnsignedLong eth1DepositIndex =
        UnsignedLong.valueOf((BigInteger) value.get("eth1_deposit_index"));

    List<Validator> validators =
        ((List<LinkedHashMap<String, Object>>) value.get("validators"))
            .stream().map(map -> parseValidator(map)).collect(Collectors.toList());
    List<UnsignedLong> balances =
        ((List<BigInteger>) value.get("balances"))
            .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList());

    UnsignedLong startShard = UnsignedLong.valueOf((BigInteger) value.get("start_shard"));
    List<Bytes32> randaoMixes =
        ((List<String>) value.get("randao_mixes"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());
    List<Bytes32> activeIndexRoots =
        ((List<String>) value.get("active_index_roots"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());
    List<Bytes32> compactCommitteesRoots =
        ((List<String>) value.get("compact_committees_roots"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());

    List<UnsignedLong> slashings =
        ((List<BigInteger>) value.get("slashings"))
            .stream().map(index -> UnsignedLong.valueOf(index)).collect(Collectors.toList());

    List<PendingAttestation> previousEpochAttestations =
        ((List<LinkedHashMap<String, Object>>) value.get("previous_epoch_attestations"))
            .stream().map(map -> parsePendingAttestation(map)).collect(Collectors.toList());
    List<PendingAttestation> currentEpochAttestations =
        ((List<LinkedHashMap<String, Object>>) value.get("current_epoch_attestations"))
            .stream().map(map -> parsePendingAttestation(map)).collect(Collectors.toList());

    List<Crosslink> previousCrosslinks =
        ((List<LinkedHashMap<String, Object>>) value.get("previous_crosslinks"))
            .stream().map(map -> parseCrosslink(map)).collect(Collectors.toList());
    List<Crosslink> currentCrosslinks =
        ((List<LinkedHashMap<String, Object>>) value.get("current_crosslinks"))
            .stream().map(map -> parseCrosslink(map)).collect(Collectors.toList());

    Bytes serializedJustificationBits =
        Bytes.fromHexString((String) value.get("justification_bits"));
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

  private BLSPublicKey mockBLSPublicKey(LinkedHashMap<String, Object> value) {
    Bytes pubkeyBytes = Bytes.fromHexString((String) value.get("pubkey"));
    BLSPublicKey pubkeyMock = Mockito.mock(BLSPublicKey.class);
    Mockito.when(pubkeyMock.toBytes()).thenReturn(pubkeyBytes);
    Mockito.when(pubkeyMock.get_fixed_parts()).thenReturn(List.of(pubkeyBytes));
    Mockito.when(pubkeyMock.getSSZFieldCount()).thenReturn(1);
    return pubkeyMock;
  }

  private BLSSignature mockBLSSignature(LinkedHashMap<String, Object> value) {
    Bytes signatureBytes = Bytes.fromHexString((String) value.get("signature"));
    return mockBLSHelper(signatureBytes);
  }

  private BLSSignature mockBLSSignature(LinkedHashMap<String, Object> value, String paramName) {
    Bytes signatureBytes = Bytes.fromHexString((String) value.get(paramName));
    return mockBLSHelper(signatureBytes);
  }

  private BLSSignature mockBLSHelper(Bytes signatureBytes) {
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
  private static Stream<Arguments> readBeaconState() throws IOException {
    return findTests(testFile, "BeaconState");
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
