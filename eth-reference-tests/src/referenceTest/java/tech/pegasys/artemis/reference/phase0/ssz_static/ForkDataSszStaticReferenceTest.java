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

package tech.pegasys.artemis.reference.phase0.ssz_static;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.state.ForkData;
import tech.pegasys.artemis.ethtests.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
public class ForkDataSszStaticReferenceTest extends TestSuite {

  @ParameterizedTest(
      name = "{index}. ssz_static/ForkData deserializedForkData={0}, root={1}, signingRoot={2}")
  @MethodSource({
    "processMinimal",
    "processMainnet",
  })
  void processSSZStaticBeaconBlock(ForkData deserializedForkData, Bytes32 root) throws Exception {
    assertEquals(deserializedForkData.hash_tree_root(), root);
  }

  @MustBeClosed
  static Stream<Arguments> processMinimal() throws Exception {
    return process("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> processMainnet() throws Exception {
    return process("mainnet");
  }

  @MustBeClosed
  static Stream<Arguments> process(String config) throws Exception {
    Path configPath = Paths.get(config);
    Path path = Paths.get(config, "phase0", "ssz_static", "ForkData");
    return sszStaticSetup(path, configPath, ForkData.class);
  }
}
