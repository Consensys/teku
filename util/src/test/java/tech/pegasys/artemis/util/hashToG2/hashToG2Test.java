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

package tech.pegasys.artemis.util.hashToG2;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.artemis.util.hashToG2.Util.bigFromHex;
import static tech.pegasys.artemis.util.hashToG2.hashToCurve.hashToCurve;

import com.google.common.base.Splitter;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP2;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.util.mikuli.G2Point;

/**
 * Test files have been generated from the reference tests at
 * https://github.com/kwantam/bls_sigs_ref/tree/master/test-vectors/hash_g2 processed for easier
 * ingestion.
 *
 * <p>Each test is s line containing the following parameters separated by spaces:
 *
 * <ul>
 *   <li>The cipher suite (currently 0x02 for all tests)
 *   <li>The message to be hashed as a hex string
 *   <li>The x0 coordinate of the expected G2 result
 *   <li>The x1 coordinate of the expected G2 result
 *   <li>The y0 coordinate of the expected G2 result
 *   <li>The y1 coordinate of the expected G2 result
 * </ul>
 */
public class hashToG2Test {

  private static final Path pathToTests =
      Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "hashToG2TestVectors");

  @ParameterizedTest(name = "{index}. Filename={0} Test={1}")
  @MethodSource({
    "getTestCase",
  })
  void referenceTest(
      String fileName, int testNumber, Bytes message, Bytes suite, G2Point expected) {
    G2Point actual = hashToCurve(message, suite);
    assertEquals(expected, actual);
  }

  @MustBeClosed
  static Stream<Arguments> getTestCase() {
    Scanner sc;
    List<String> fileNames;
    try (Stream<Path> walk = Files.walk(pathToTests)) {

      fileNames =
          walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());

      Iterator<String> fileNamesIterator = fileNames.iterator();
      ArrayList<Arguments> argumentsList = new ArrayList<Arguments>();
      while (fileNamesIterator.hasNext()) {
        File file = new File(fileNamesIterator.next().toString());
        try {
          sc = new Scanner(file, UTF_8.name());
          int i = 0;
          while (sc.hasNextLine()) {
            List<String> params = Splitter.on(" ").omitEmptyStrings().splitToList(sc.nextLine());
            checkArgument(params.size() == 6, "Wrong number of fields in input data.");
            Bytes suite = Bytes.fromHexString(params.get(0));
            Bytes message = Bytes.fromHexString(params.get(1));
            G2Point expected =
                new G2Point(
                    new ECP2(
                        new FP2(bigFromHex(params.get(2)), bigFromHex(params.get(3))),
                        new FP2(bigFromHex(params.get(4)), bigFromHex(params.get(5)))));
            argumentsList.add(Arguments.of(file.toString(), i, message, suite, expected));
            i++;
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      return argumentsList.stream();

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  // This case failed during fuzz testing. Keep it to protect against regression.
  void fuzzFailed0() {
    FP2 x =
        new FP2(
            bigFromHex(
                "0x17bc2d27dec39087446864fb3b22ad93afa9c039bca3623c0cdfb5b09729cc236eb6bdbf92dc0f7db0426742e0d50305"),
            bigFromHex(
                "0x14acf346178f7b3777cc40ce49fc9d77a5c564c2f296577ca2f14505d4cc7da9ddb52506a5dd3f3f65ba741762908f00"));
    FP2 y =
        new FP2(
            bigFromHex(
                "0x14d19067caf1221e19d2a1774ca1fd2f060063e9025048dbc6c1eb8ab518e1d71406dee73e50f5e310baaa84d171efd1"),
            bigFromHex(
                "0x040002dd3cb170f6b25d417d3bc1eb9af1e8c98a32c91048435970fd8c96b8f48a693a213ee79a37d1a572f223883f27"));
    G2Point expected = new G2Point(new ECP2(x, y));

    // Cipher suite is 0x02 in the current test data
    Bytes msg =
        Bytes.fromHexString(
            "0xc05b63412c8152023e1b8cefee76cc99d738a21e6eb132d313a782f6125fc9339ac1ca342bbc546fc35f8f125939e5f69d2d7533841e3fd11e015f96458bf25278ef2c95f8c9f94d9fb41941b2438077e34c6ae553e4c03d79dc191bc44573504829cbfc6e6b570a82b400606a5d6c0ad3a680a20ff8eb0e4f487c31d1468a6791ae21d85b28fd14d7725c85e973f0cf27b70538ff2794a62f4b6cb9825bfe7bfba5868314f5b3fd464ed5667fc2bdb10710be09912561b22dcf22283d419397ff2059211a5975f4a75d3d5850f8abac83a2837bcf9d73c971556e9d653e881c5de73792efd37cbd43080bad65d5edd868196efb28561a41e8ab5074f6af7748870fa4ab2b8bd7422dd920858bba663f447c545675cf14be5a2434451c8e65814d26ad8741942ce5b72a4435b33a92256040ce5e72247ddcd319471010b6da435181d73bb0ba1cecd4a2dc39d16368620770ff1b949316aa05a92d8eeebf480b5eca7f98e6d7f2c747365ba0e19748d6b4c8a65f3ee0c6584aa257d69408eea390aa47ed6c0e0c12b6418cf5bcf6ecba227c6fb67d704833b6726fdf51fcfcfac1c89db6196cd50524c1d5735a31001b6ae27145619ebabf6ff8c49ac9df3b68d0ac37b703b139d966a3fe2a4f4783b6af533f4ba49dcde994bbaadb3c0ca57d01a1ea432195627e50e902c15c7a2eb9dc45f85dcb72acdf2f0f30d081bea43a8aa9efe90388735dd53ea634b4fe6e63422b32fa5d21d14d46e216029c217ca43240b3c29e5f97bf43c3b138e38bdd92d8480928bc5609f0cbd9decbe8b158f9eb950088c189baf2158fb367bd808e9a87aa00fb6a24ed4338fad6ae161c221808f7b69f96d2a3b1d7cfeebaac9894fc591f12db37f9885d10e3861acb91ad7a16bf2e674c06328cc16d01a08e03b0ecdbc269314b848da3276966d98a22");
    G2Point actual = hashToCurve.hashToCurve(msg, Bytes.fromHexString("0x02"));

    assertEquals(expected, actual);
  }
}
