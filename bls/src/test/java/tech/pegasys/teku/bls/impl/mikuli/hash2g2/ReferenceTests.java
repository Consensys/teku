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

package tech.pegasys.teku.bls.impl.mikuli.hash2g2;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.clearH2;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.expandMessage;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.hashToField;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.iso3;
import static tech.pegasys.teku.bls.impl.mikuli.hash2g2.Helper.mapToCurve;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test files are from https://github.com/algorand/bls_sigs_ref/tree/master/test-vectors/hash_g2
 *
 * <p>Each line is formatted as follows:
 *
 * <ul>
 *   <li>The message in hexadecimal
 *   <li>A space
 *   <li>The separator string "00"
 *   <li>A space
 *   <li>The G2 point in compressed form
 * </ul>
 */
class ReferenceTests {

  //
  // Reference tests for expand message from
  // https://github.com/cfrg/draft-irtf-cfrg-hash-to-curve/tree/master/poc/vectors
  //
  // These match the test vectors from spec v08:
  // https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-hash-to-curve-08#appendix-I.1
  //

  private static final Path pathToExpandMessageTests =
      Paths.get(
          System.getProperty("user.dir"),
          "src",
          "test",
          "resources",
          "expand_message_xmd_SHA256.json");

  @ParameterizedTest(name = "{index}. Filename={0} Test={1}")
  @MethodSource({
    "getExpandMessageTestCases",
  })
  void expandMessageReferenceTests(
      String filename, int testNumber, Bytes dst, Bytes message, int length, Bytes expected) {
    Bytes actual = expandMessage(message, dst, length);
    assertEquals(expected, actual);
  }

  @MustBeClosed
  static Stream<Arguments> getExpandMessageTestCases() {
    final JSONParser parser = new JSONParser();
    final ArrayList<Arguments> argumentsList = new ArrayList<>();

    try {
      final Reader reader = new FileReader(pathToExpandMessageTests.toFile(), US_ASCII);
      final JSONObject refTests = (JSONObject) parser.parse(reader);

      final Bytes dst = Bytes.wrap(((String) refTests.get("DST")).getBytes(US_ASCII));

      final JSONArray tests = (JSONArray) refTests.get("tests");
      int idx = 0;
      for (Object o : tests) {
        JSONObject test = (JSONObject) o;
        Bytes message = Bytes.wrap(((String) test.get("msg")).getBytes(US_ASCII));
        int length = Integer.parseInt(((String) test.get("len_in_bytes")).substring(2), 16);
        Bytes uniformBytes = Bytes.fromHexString((String) test.get("uniform_bytes"));
        argumentsList.add(
            Arguments.of(
                pathToExpandMessageTests.toString(), idx++, dst, message, length, uniformBytes));
      }
    } catch (IOException | ParseException e) {
      throw new RuntimeException(e);
    }

    return argumentsList.stream();
  }

  //
  // Reference tests for hash to G2 from
  // https://github.com/cfrg/draft-irtf-cfrg-hash-to-curve/tree/master/poc/vectors
  //
  // These match the test vectors from spec v08:
  // https://datatracker.ietf.org/doc/html/draft-irtf-cfrg-hash-to-curve-08#appendix-H.10.1
  //

  private static final Path pathToHashG2Tests =
      Paths.get(
          System.getProperty("user.dir"),
          "src",
          "test",
          "resources",
          "BLS12381G2_XMD-SHA-256_SSWU_RO_.json");

  @ParameterizedTest(name = "{index}. Filename={0} Test={1}")
  @MethodSource({
    "hashG2TestCases",
  })
  void hashG2ReferenceTests(
      String filename,
      int testNumber,
      Bytes dst,
      Bytes message,
      FP2Immutable[] u,
      JacobianPoint q0,
      JacobianPoint q1,
      JacobianPoint p) {
    FP2Immutable[] uActual = hashToField(message, 2, dst);
    assertEquals(u[0], uActual[0]);
    assertEquals(u[1], uActual[1]);

    JacobianPoint q0Actual = iso3(mapToCurve(uActual[0]));
    JacobianPoint q1Actual = iso3(mapToCurve(uActual[1]));
    assertEquals(q0, q0Actual.toAffine());
    assertEquals(q1, q1Actual.toAffine());

    JacobianPoint pActual = clearH2(q0Actual.add(q1Actual));
    assertEquals(p, pActual);
  }

  @MustBeClosed
  static Stream<Arguments> hashG2TestCases() {
    final JSONParser parser = new JSONParser();
    final ArrayList<Arguments> argumentsList = new ArrayList<>();

    try {
      final Reader reader = new FileReader(pathToHashG2Tests.toFile(), US_ASCII);
      final JSONObject refTests = (JSONObject) parser.parse(reader);

      final Bytes dst = Bytes.wrap(((String) refTests.get("dst")).getBytes(US_ASCII));

      final JSONArray tests = (JSONArray) refTests.get("vectors");
      int idx = 0;
      for (Object o : tests) {
        JSONObject test = (JSONObject) o;
        Bytes message = Bytes.wrap(((String) test.get("msg")).getBytes(US_ASCII));
        JacobianPoint p = getPoint((JSONObject) test.get("P"));
        JacobianPoint q0 = getPoint((JSONObject) test.get("Q0"));
        JacobianPoint q1 = getPoint((JSONObject) test.get("Q1"));
        JSONArray uArray = (JSONArray) test.get("u");
        FP2Immutable[] u = {
          getFieldPoint((String) uArray.get(0)), getFieldPoint((String) uArray.get(1))
        };
        argumentsList.add(
            Arguments.of(pathToExpandMessageTests.toString(), idx++, dst, message, u, q0, q1, p));
      }
    } catch (IOException | ParseException e) {
      throw new RuntimeException(e);
    }

    return argumentsList.stream();
  }

  private static JacobianPoint getPoint(JSONObject o) {
    return new JacobianPoint(
        getFieldPoint((String) o.get("x")),
        getFieldPoint((String) o.get("y")),
        new FP2Immutable(1));
  }

  private static FP2Immutable getFieldPoint(String s) {
    return new FP2Immutable(s.substring(0, 98), s.substring(99));
  }
}
