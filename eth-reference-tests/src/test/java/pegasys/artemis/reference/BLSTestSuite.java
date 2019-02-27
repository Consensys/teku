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
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP;
import org.apache.milagro.amcl.BLS381.FP2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * The "official" BLS reference test data is from https://github.com/ethereum/eth2.0-tests/
 *
 * TODO: As of 2019-02-26 there are some issues with the reference data
 *  > The point compression used is broken. See https://github.com/ethereum/eth2.0-tests/issues/20
 *    This leads to extensive and tedious work-arounds in the below. Test 06 is too hard to fix
 *  > Test case 07 input data is malformed.
 *  Also note that the spec and test data will likely be changing.
 */

class BLSTestSuite {

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

    /*
    // TODO the test should look like this when the test data have been fixed

    Bytes expectedBytes = Bytes.concatenate(xReExpected, xImExpected);
    Bytes actualBytes = actual.toBytesCompressed();

    assertEquals(expectedBytes, actualBytes);
    */

    // TODO Remove the following when the test data are fixed

    byte[] xReBytes = new byte[48];
    byte[] xImBytes = new byte[48];
    actual.ecp2Point().getX().getA().toBytes(xReBytes);
    actual.ecp2Point().getX().getB().toBytes(xImBytes);
    Bytes48 xReActual = Bytes48.leftPad(Bytes48.wrap(xReBytes));
    Bytes48 xImActual = Bytes48.leftPad(Bytes48.wrap(xImBytes));

    // Mask the faulty flag bits
    xReExpected =
        xReExpected.and(
            Bytes48.fromHexString(
                "0x"
                    + "1fffffffffffffffffffffffffffffff"
                    + "ffffffffffffffffffffffffffffffff"
                    + "ffffffffffffffffffffffffffffffff"));

    assertEquals(xReExpected, xReActual);
    assertEquals(xImExpected, xImActual);
  }

  @ParameterizedTest(name = "{index}. private to public key {0} -> {1}")
  @MethodSource("readPrivateToPublicKey")
  void testPrivateToPublicKey(String input, String output) {
    SecretKey privateKey = SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(input)));

    PublicKey publicKeyActual = new PublicKey(privateKey);

    // TODO: The flags are broken on the test data, so we need to manually create the point.
    // The correct approach is something like:
    //   PublicKey publicKeyExpected = new PublicKey(fromBytes(Bytes.fromHexString(output)));
    //   assertEquals(publicKeyExpected, publicKeyActual);

    // TODO Remove the following when the test data are fixed

    G1Point point = new G1Point(new ECP(BIG.fromBytes(Bytes.fromHexString(output).toArray())));

    // If necessary, fix up the Y coords so the A flag matches
    if (point.getA() != publicKeyActual.g1Point().getA()) {
      FP yNeg = new FP(point.ecpPoint().getY());
      yNeg.neg();
      point = new G1Point(new ECP(point.ecpPoint().getX(), yNeg.redc()));
    }
    assertEquals(publicKeyActual, new PublicKey(point));
  }

  @ParameterizedTest(name = "{index}. sign messages {0} -> {1}")
  @MethodSource("readSignMessages")
  void testSignMessages(LinkedHashMap<String, String> input, String output) {

    long domain = Bytes32.fromHexString(input.get("domain")).getLong(24);
    Bytes message = Bytes.fromHexString(input.get("message"));
    SecretKey privateKey =
        SecretKey.fromBytes(Bytes48.leftPad(Bytes.fromHexString(input.get("privkey"))));

    Signature signatureActual =
        BLS12381.sign(new KeyPair(privateKey), message.toArray(), domain).signature();

    String expected = output;
    // TODO: Once again, we need to fix things up with the flags
    // Signature signatureExpected = new
    // Signature(G2Point.fromBytesCompressed(Bytes.fromHexString(output)));

    // TODO Remove the following when the test data are fixed

    byte[] tmp = Bytes.fromHexString(output).toArray();
    tmp[0] &= (byte) 0x1f;
    BIG xRe = BIG.frombytearray(tmp, 0);
    BIG xIm = BIG.frombytearray(tmp, 48);
    G2Point point = new G2Point(new ECP2(new FP2(xRe, xIm)));
    // If necessary, fix up the Y coords so the A flag matches
    if (point.getA1() != signatureActual.g2Point().getA1()) {
      FP2 yNeg = new FP2(point.ecp2Point().getY());
      yNeg.neg();
      point = new G2Point(new ECP2(point.ecp2Point().getX(), yNeg));
    }
    Signature signatureExpected = new Signature(point);

    assertEquals(signatureExpected, signatureActual);
  }

  @ParameterizedTest(name = "{index}. aggregate sig {0} -> {1}")
  @MethodSource("readAggregateSig")
  void testAggregateSig(ArrayList<String> input, String output) {

    // TODO: Too arduous to tackle until the flags are cleared up in the input data: basically, it's
    // not clear in the input data which Y branch is being selected give the compressed input (it's
    // not as per the spec), and fixing this would be horrible: basically 8 possibilities to
    // generate
    // and test for each input set.
    ;
  }

  /* The input data yml is malformed for this case - it needs a "- " before "input"
    @ParameterizedTest(name = "{index}. aggregate pub keys {0} -> {1}")
    @MethodSource("readAggregatePubKeys")
    void testAggregatePubKeys(ArrayList<String> input, String output) {

      // TODO: See above
      ;

    }
  */

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
    return findTests("**/bls/test_bls.yml", "case01_message_hash_G2_uncompressed");
  }

  @MustBeClosed
  private static Stream<Arguments> readMessageHashG2Compressed() throws IOException {
    return findTests("**/bls/test_bls.yml", "case02_message_hash_G2_compressed");
  }

  @MustBeClosed
  private static Stream<Arguments> readPrivateToPublicKey() throws IOException {
    return findTests("**/bls/test_bls.yml", "case03_private_to_public_key");
  }

  @MustBeClosed
  private static Stream<Arguments> readSignMessages() throws IOException {
    return findTests("**/bls/test_bls.yml", "case04_sign_messages");
  }

  @MustBeClosed
  private static Stream<Arguments> readAggregateSig() throws IOException {
    return findTests("**/bls/test_bls.yml", "case06_aggregate_sigs");
  }

  @MustBeClosed
  private static Stream<Arguments> readAggregatePubKeys() throws IOException {
    return findTests("**/bls/test_bls.yml", "case07_aggregate_pubkeys");
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
