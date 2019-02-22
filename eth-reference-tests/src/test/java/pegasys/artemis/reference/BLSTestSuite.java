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

package tech.pegasys.artemis.util.bls;

import java.lang.Exception;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.consensys.cava.io.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BLSTestSuite {

  @ParameterizedTest(name = "{index}. aggregate pub keys {0} -> {1}")
  @MethodSource("readAggregatePubKeys")
  void testAggregatePubKeys(String input, String output) {
    ;
  }

  @ParameterizedTest(name = "{index}. aggregate sig {0} -> {1}")
  @MethodSource("readAggregateSig")
  void testAggregateSig(String input, String output) {
    ;
  }

  @ParameterizedTest(name = "{index}. sign messages {0} -> {1}")
  @MethodSource("readSignMessages")
  void testSignMessages(String input, String output) {
    ;
  }

  @ParameterizedTest(name = "{index}. private to public key {0} -> {1}")
  @MethodSource("readPrivateToPublicKey")
  void testPrivateToPublicKey(String input, String output) {
    ;
  }

  @ParameterizedTest(name = "{index}. message hash to G2 compressed {0} -> {1}")
  @MethodSource("readMessageHashG2Compressed")
  void testMessageHashToG2Compressed(String input, String output) {
    ;
  }

  @ParameterizedTest(name = "{index}. message hash to G2 uncompressed {0} -> {1}")
  @MethodSource("readMessageHashG2Uncompressed")
  void testMessageHashToG2Uncompressed(String input, String output) throws Exception {
    throw new Exception();
  }

  @MustBeClosed
  private static Stream<Arguments> findTests(String glob, String tcase) throws IOException {
    return Resources.find(glob)
        .flatMap(
            url -> {
              try (InputStream in = url.openConnection().getInputStream()) {
                System.out.println(url);
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
}
