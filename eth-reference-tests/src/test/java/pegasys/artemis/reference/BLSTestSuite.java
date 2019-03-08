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

package tech.pegasys.artemis.util.mikuli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.io.Resources;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * The "official" BLS reference test data is from https://github.com/ethereum/eth2.0-tests/
 */

class BLSTestSuite {

  // TODO: reinstate the official tests once they have been updated
  // private static String testFile = "**/bls/test_bls.yml";
  private static String testFile = "**/test_bls_tmp.yml";

  @ParameterizedTest(name = "{index}. message hash to G2 uncompressed {0} -> {1}")
  @MethodSource("readMessageHashG2Uncompressed")
  void testMessageHashToG2Uncompressed(
      LinkedHashMap<String, String> input, ArrayList<ArrayList<String>> output) {

    long domain = Bytes32.fromHexString(input.get("domain")).getLong(24);
    Bytes message = Bytes.fromHexString(input.get("message"));

    G2Point referencePoint = makePoint(output);

    assertEquals(referencePoint, G2Point.hashToG2(message, domain));
  }

  @ParameterizedTest(name = "{index}. message hash to G2 compressed {0} -> {1}")
  @MethodSource("readMessageHashG2Compressed")
  void testMessageHashToG2Compressed(
      LinkedHashMap<String, String> input, ArrayList<String> output) {

    long domain = Bytes32.fromHexString(input.get("domain")).getLong(24);
    Bytes message = Bytes.fromHexString(input.get("message"));

    G2Point actual = G2Point.hashToG2(message, domain);
    Bytes48 xReExpected = Bytes48.leftPad(Bytes.fromHexString(output.get(0)));
    Bytes48 xImExpected = Bytes48.leftPad(Bytes.fromHexString(output.get(1)));
    Bytes expectedBytes = Bytes.concatenate(xReExpected, xImExpected);
    Bytes actualBytes = actual.toBytesCompressed();

    assertEquals(expectedBytes, actualBytes);
  }

  @ParameterizedTest(name = "{index}. private to public key {0} -> {1}")
  @MethodSource("readPrivateToPublicKey")
  void testPrivateToPublicKey(String input, String output) {
    SecretKey privateKey = SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(input)));

    PublicKey publicKeyActual = new PublicKey(privateKey);
    PublicKey publicKeyExpected = PublicKey.fromBytesCompressed(Bytes.fromHexString(output));

    assertEquals(publicKeyExpected, publicKeyActual);
  }

  @ParameterizedTest(name = "{index}. sign messages {0} -> {1}")
  @MethodSource("readSignMessages")
  void testSignMessages(LinkedHashMap<String, String> input, String output) {

    long domain = Bytes32.fromHexString(input.get("domain")).getLong(24);
    Bytes message = Bytes.fromHexString(input.get("message"));
    SecretKey privateKey =
        SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(input.get("privkey"))));

    Bytes signatureActualBytes =
        BLS12381
            .sign(new KeyPair(privateKey), message.toArray(), domain)
            .signature()
            .g2Point()
            .toBytesCompressed();
    Bytes signatureExpectedBytes = Bytes.fromHexString(output);

    assertEquals(signatureExpectedBytes, signatureActualBytes);
  }

  @ParameterizedTest(name = "{index}. aggregate sig {0} -> {1}")
  @MethodSource("readAggregateSig")
  void testAggregateSig(ArrayList<String> input, String output) {

    ArrayList<Signature> signatures = new ArrayList<>();
    for (String sig : input) {
      signatures.add(Signature.fromBytesCompressed(Bytes.fromHexString(sig)));
    }

    Bytes aggregateSignatureActualBytes =
        Signature.aggregate(signatures).g2Point().toBytesCompressed();
    Bytes aggregateSignatureExpectedBytes = Bytes.fromHexString(output);

    assertEquals(aggregateSignatureExpectedBytes, aggregateSignatureActualBytes);
  }

  @ParameterizedTest(name = "{index}. aggregate pub keys {0} -> {1}")
  @MethodSource("readAggregatePubKeys")
  void testAggregatePubKeys(ArrayList<String> input, String output) {

    ArrayList<PublicKey> publicKeys = new ArrayList<>();
    for (String pubkey : input) {
      publicKeys.add(PublicKey.fromBytesCompressed(Bytes.fromHexString(pubkey)));
    }

    Bytes aggregatePublicKeyActualBytes =
        PublicKey.aggregate(publicKeys).g1Point().toBytesCompressed();

    Bytes aggregatePublicKeyExpectedBytes = Bytes.fromHexString(output);
    assertEquals(aggregatePublicKeyExpectedBytes, aggregatePublicKeyActualBytes);
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
  private static Stream<Arguments> readMessageHashG2Uncompressed() throws IOException {
    return findTests(testFile, "case01_message_hash_G2_uncompressed");
  }

  @MustBeClosed
  private static Stream<Arguments> readMessageHashG2Compressed() throws IOException {
    return findTests(testFile, "case02_message_hash_G2_compressed");
  }

  @MustBeClosed
  private static Stream<Arguments> readPrivateToPublicKey() throws IOException {
    return findTests(testFile, "case03_private_to_public_key");
  }

  @MustBeClosed
  private static Stream<Arguments> readSignMessages() throws IOException {
    return findTests(testFile, "case04_sign_messages");
  }

  @MustBeClosed
  private static Stream<Arguments> readAggregateSig() throws IOException {
    return findTests(testFile, "case06_aggregate_sigs");
  }

  @MustBeClosed
  private static Stream<Arguments> readAggregatePubKeys() throws IOException {
    return findTests(testFile, "case07_aggregate_pubkeys");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Stream<Arguments> prepareTests(InputStream in, String tcase) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map allTests = mapper.readerFor(Map.class).readValue(in);

    return ((List<Map>) allTests.get(tcase))
        .stream().map(testCase -> Arguments.of(testCase.get("input"), testCase.get("output")));
  }

  /**
   * Utility for converting uncompressed test case data to a point
   *
   * <p>The test case data is not in standard form (Z = 1). This routine converts the input to a
   * point and applies the affine transformation. This routine is for uncompressed input.
   *
   * @param coords an array of strings {xRe, xIm, yRe, yIm, zRe, zIm}
   * @return the point corresponding to the input
   */
  private static G2Point makePoint(ArrayList<ArrayList<String>> coords) {
    BIG xRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(0).get(0))).toArray());
    BIG xIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(0).get(1))).toArray());
    BIG yRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(1).get(0))).toArray());
    BIG yIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(1).get(1))).toArray());
    BIG zRe = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(2).get(0))).toArray());
    BIG zIm = BIG.fromBytes(Bytes48.leftPad(Bytes.fromHexString(coords.get(2).get(1))).toArray());

    FP2 x = new FP2(xRe, xIm);
    FP2 y = new FP2(yRe, yIm);
    FP2 z = new FP2(zRe, zIm);

    // Normalise the point (affine transformation) so that Z = 1
    z.inverse();
    x.mul(z);
    x.reduce();
    y.mul(z);
    y.reduce();

    return new G2Point(new ECP2(x, y));
  }
}
