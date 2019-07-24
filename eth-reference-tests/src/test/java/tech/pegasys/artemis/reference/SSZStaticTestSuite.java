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
import java.util.ArrayList;
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

import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

@ExtendWith(BouncyCastleExtension.class)
class SSZStaticTestSuite {

  private static String testFile = "**/ssz_minimal_nil_tmp.yaml";

  @ParameterizedTest(name = "{index}. Eth1Data Hash Tree Root Test")
  @MethodSource("readEth1Data")
  void testEth1DataHashTreeRoot(LinkedHashMap<String, Object> value, String serialized, String root) {
    Bytes32 depositRoot = Bytes32.fromHexString((String) value.get("deposit_root"));
    UnsignedLong depositCount = UnsignedLong.valueOf((Long) value.get("deposit_count"));
    Bytes32 blockHash = Bytes32.fromHexString((String) value.get("block_hash"));

    Eth1Data eth1Data = new Eth1Data(depositRoot, depositCount, blockHash);
    
    //assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(eth1Data));
    assertEquals(Bytes32.fromHexString(root), eth1Data.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. Eth1Data Serialization Test")
  @MethodSource("readEth1Data")
  void testEth1DataSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Bytes32 depositRoot = Bytes32.fromHexString((String) value.get("deposit_root"));
    UnsignedLong depositCount = UnsignedLong.valueOf((Long) value.get("deposit_count"));
    Bytes32 blockHash = Bytes32.fromHexString((String) value.get("block_hash"));

    Eth1Data eth1Data = new Eth1Data(depositRoot, depositCount, blockHash);
    
    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(eth1Data));
  }

  @ParameterizedTest(name = "{index}. DepositData Hash Tree Root Test")
  @MethodSource("readDepositData")
  void testDepositDataHashTreeRoot(LinkedHashMap<String, Object> value, String serialized, String root) {
    Bytes pubkeyBytes = Bytes.fromHexString((String) value.get("pubkey"));
    BLSPublicKey pubkeyMock = Mockito.mock(BLSPublicKey.class);
    Mockito.when(pubkeyMock.toBytes()).thenReturn(pubkeyBytes);
    Mockito.when(pubkeyMock.get_fixed_parts()).thenReturn(List.of(pubkeyBytes));

    Bytes32 withdrawalCredentials = Bytes32.fromHexString((String) value.get("withdrawal_credentials"));
    UnsignedLong amount = UnsignedLong.valueOf((Long) value.get("amount"));

    Bytes signatureBytes = Bytes.fromHexString((String) value.get("signature"));
    BLSSignature signatureMock = Mockito.mock(BLSSignature.class);
    Mockito.when(signatureMock.toBytes()).thenReturn(signatureBytes);
    Mockito.when(signatureMock.get_fixed_parts()).thenReturn(List.of(signatureBytes));

    DepositData depositData = new DepositData(pubkeyMock, withdrawalCredentials, amount, signatureMock);

    assertEquals(Bytes32.fromHexString(root), depositData.hash_tree_root());
  }

  @ParameterizedTest(name = "{index}. DepositData Serialization Test")
  @MethodSource("readDepositData")
  void testDepositDataSerialize(LinkedHashMap<String, Object> value, String serialized, String root) {
    Bytes pubkeyBytes = Bytes.fromHexString((String) value.get("pubkey"));
    BLSPublicKey pubkeyMock = Mockito.mock(BLSPublicKey.class);
    Mockito.when(pubkeyMock.toBytes()).thenReturn(pubkeyBytes);
    Mockito.when(pubkeyMock.get_fixed_parts()).thenReturn(List.of(pubkeyBytes));

    Bytes32 withdrawalCredentials = Bytes32.fromHexString((String) value.get("withdrawal_credentials"));
    UnsignedLong amount = UnsignedLong.valueOf((Long) value.get("amount"));

    Bytes signatureBytes = Bytes.fromHexString((String) value.get("signature"));
    BLSSignature signatureMock = Mockito.mock(BLSSignature.class);
    Mockito.when(signatureMock.toBytes()).thenReturn(signatureBytes);
    Mockito.when(signatureMock.get_fixed_parts()).thenReturn(List.of(signatureBytes));

    DepositData depositData = new DepositData(pubkeyMock, withdrawalCredentials, amount, signatureMock);
    
    assertEquals(Bytes.fromHexString(serialized), SimpleOffsetSerializer.serialize(depositData));
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Stream<Arguments> prepareTests(InputStream in, String tcase) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map allTests = mapper.readerFor(Map.class).readValue(in);
    List<Map> testCaseList = ((List<Map>) allTests.get("test_cases")).stream().filter(testCase -> testCase.containsKey(tcase)).collect(Collectors.toList());

    return testCaseList.stream().map(testCase -> Arguments.of(((Map) testCase.get(tcase)).get("value"), ((Map) testCase.get(tcase)).get("serialized"), ((Map) testCase.get(tcase)).get("root")));
  }
}
