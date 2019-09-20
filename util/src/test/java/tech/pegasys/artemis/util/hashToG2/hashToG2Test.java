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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.artemis.util.hashToG2.Util.bigFromHex;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.util.mikuli.G2Point;

/**
 * Test files have been generated from the reference tests at
 * https://github.com/kwantam/bls_sigs_ref/tree/master/test-vectors/hash_g2 processed for easier
 * ingestion.
 *
 * <p>Each test is six lines
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
    G2Point actual = hashToCurve.hashToCurve(message, suite);
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
            Bytes suite = Bytes.fromHexString(sc.nextLine());
            Bytes message = Bytes.fromHexString(sc.nextLine());
            String x0 = sc.nextLine();
            String x1 = sc.nextLine();
            String y0 = sc.nextLine();
            String y1 = sc.nextLine();
            G2Point expected =
                new G2Point(
                    new ECP2(
                        new FP2(bigFromHex(x0), bigFromHex(x1)),
                        new FP2(bigFromHex(y0), bigFromHex(y1))));
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
}
