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

package tech.pegasys.artemis.datastructures.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.io.Resources;
import net.consensys.cava.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(BouncyCastleExtension.class)
public class PermutedIndexTestSuite {

  // TODO: point this to the official test file repo when it is available and correct
  private static String testFile = "**/test_vector_permutated_index_tmp.yml";

  @ParameterizedTest(name = "{index}. Test permuted index {0}")
  @MethodSource("readPermutedIndexTestVectors")
  void testPermutedIndex(int index, int listSize, int indexExpected, Bytes32 seed) {

    int indexActual = BeaconStateUtil.get_permuted_index(index, listSize, seed);

    assertEquals(indexExpected, indexActual);
  }

  @MustBeClosed
  private static Stream<Arguments> findTests(String glob) throws IOException {
    return Resources.find(glob)
        .flatMap(
            url -> {
              try (InputStream in = url.openConnection().getInputStream()) {
                return readTestCase(in);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  @MustBeClosed
  private static Stream<Arguments> readPermutedIndexTestVectors() throws IOException {
    return findTests(testFile);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Stream<Arguments> readTestCase(InputStream in) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Map allTests = mapper.readerFor(Map.class).readValue(in);

    return ((List<Map>) allTests.get("test_cases"))
        .stream()
            .map(
                testCase ->
                    Arguments.of(
                        testCase.get("index"),
                        testCase.get("list_size"),
                        testCase.get("permutated_index"),
                        Bytes32.fromHexString(testCase.get("seed").toString())));
  }
}
