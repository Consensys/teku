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

package tech.pegasys.teku.bls.hashToG2;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.mikuli.G2Point;

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

  private static final Path pathToTests =
      Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "hashToG2TestVectors");

  @ParameterizedTest(name = "{index}. Filename={0} Test={1}")
  @MethodSource({
    "getTestCase",
  })
  void referenceTest(
      String fileName, int testNumber, Bytes message, Bytes suite, G2Point expected) {
    G2Point actual = new G2Point(HashToCurve.hashToG2(message, suite));
    assertEquals(expected, actual);
  }

  @MustBeClosed
  static Stream<Arguments> getTestCase() {
    Scanner sc;
    List<String> fileNames;
    try (Stream<Path> walk = Files.walk(pathToTests)) {

      fileNames =
          walk.filter(Files::isRegularFile).map(Path::toString).collect(Collectors.toList());

      Iterator<String> fileNamesIterator = fileNames.iterator();
      ArrayList<Arguments> argumentsList = new ArrayList<>();
      while (fileNamesIterator.hasNext()) {
        File file = new File(fileNamesIterator.next());
        try {
          sc = new Scanner(file, UTF_8.name());
          int i = 0;
          while (sc.hasNextLine()) {

            // Test line format is "MessageHex 00 CompressedG2Point"
            List<String> params = Splitter.on(" ").omitEmptyStrings().splitToList(sc.nextLine());
            checkArgument(params.size() == 3, "Wrong number of fields in input data.");

            Bytes message = Bytes.fromHexString(params.get(0));
            G2Point expected = G2Point.fromBytesCompressed(Bytes.fromHexString(params.get(2)));

            // Cipher suite is fixed at 0x02 in the test data
            Bytes suite = Bytes.fromHexString("0x02");

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
