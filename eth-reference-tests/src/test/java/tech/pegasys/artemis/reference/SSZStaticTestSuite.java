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
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Crosslink;
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
  @SuppressWarnings({"unchecked"})
  void testDepositHashTreeRoot(
      LinkedHashMap<String, Object> value, String serialized, String root) {
    List<Bytes32> proof =
        ((List<String>) value.get("proof"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());
    DepositData data = parseDepositData((LinkedHashMap<String, Object>) value.get("data"));

    Deposit deposit = new Deposit(proof, data);

    assertEquals(Bytes32.fromHexString(root), deposit.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Deposit Serialization Test")
  @MethodSource("readDeposit")
  @SuppressWarnings({"unchecked"})
  void testDepositSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    List<Bytes32> proof =
        ((List<String>) value.get("proof"))
            .stream()
                .map(proofString -> Bytes32.fromHexString(proofString))
                .collect(Collectors.toList());
    DepositData data = parseDepositData((LinkedHashMap<String, Object>) value.get("data"));

    Deposit deposit = new Deposit(proof, data);

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

  // Attestation Goes Here

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

  private static Stream<Arguments> readIndexedAttestation() throws IOException {
    return findTests(testFile, "IndexedAttestation");
  }

  private static Stream<Arguments> readAttesterSlashing() throws IOException {
    return findTests(testFile, "AttesterSlashing");
  }

  // Attestation Goes Here

  private static Stream<Arguments> readVoluntaryExit() throws IOException {
    return findTests(testFile, "VoluntaryExit");
  }

  private static Stream<Arguments> readTransfer() throws IOException {
    return findTests(testFile, "Transfer");
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
