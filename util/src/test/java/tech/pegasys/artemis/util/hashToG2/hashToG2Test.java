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
import static tech.pegasys.artemis.util.mikuli.G2Point.hashToG2;

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
import org.junit.jupiter.api.Disabled;
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

  @Disabled
  void benchmark() {

    // Do lots of reps because
    //   (a) There is a few 100ms start-up cost to amortise
    //   (b) Time taken per iteration of oldHash is very variable
    int nReps = 1000;

    benchmarkTarget oldHash = new oldHashToG2();
    benchmarkTarget newHash = new newHashToG2();

    // Set up the input data
    ArrayList<Bytes> messages = new ArrayList<>();
    for (int j = 0; j < nReps; j++) {
      messages.add(Bytes.random(32));
    }
    Bytes suite = Bytes.fromHexString("0x02");

    // Run the benchmarks
    long timeOld = runBenchmark(oldHash, nReps, messages, suite);
    long timeNew = runBenchmark(newHash, nReps, messages, suite);

    System.out.println(
        "Old hashToG2: "
            + timeOld
            + "ms for "
            + nReps
            + " iterations. "
            + (double) timeOld / nReps
            + "ms average");
    System.out.println(
        "New hashToG2: "
            + timeNew
            + "ms for "
            + nReps
            + " iterations. "
            + (double) timeNew / nReps
            + "ms average");
  }

  long runBenchmark(benchmarkTarget benchmark, int nReps, ArrayList<Bytes> messages, Bytes suite) {
    long start = System.currentTimeMillis();
    for (int i = 0; i < nReps; i++) {
      benchmark.target(messages.get(i), suite);
    }
    return System.currentTimeMillis() - start;
  }

  interface benchmarkTarget {
    void target(Bytes message, Bytes suite);
  }

  static class oldHashToG2 implements benchmarkTarget {
    public void target(Bytes message, Bytes suite) {
      hashToG2(message, suite);
    }
  }

  static class newHashToG2 implements benchmarkTarget {
    public void target(Bytes message, Bytes suite) {
      hashToCurve(message, suite);
    }
  }
}
