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

package tech.pegasys.artemis.reference.phase0.operations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_block_header;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;

@ExtendWith(BouncyCastleExtension.class)
public class block_header extends TestSuite {

  @ParameterizedTest(name = "{index}. process BeaconBlock beaconBlock={0} -> pre={1}")
  @MethodSource({
    "processBeaconBlockInvalidParentRootSetup",
    "processBeaconBlockInvalidSlotBlockHeaderSetup",
    "processBeaconBlockProposerSlashedSetup"
  })
  void processBeaconBlock(BeaconBlock beaconBlock, BeaconState pre) {

    assertThrows(
        BlockProcessingException.class,
        () -> {
          process_block_header(pre, beaconBlock);
        });
  }

  @MustBeClosed
  static Stream<Arguments> processBeaconBlockInvalidParentRootSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "mainnet",
            "phase0",
            "operations",
            "block_header",
            "pyspec_tests",
            "invalid_parent_root");
    return genericBlockHeaderSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processBeaconBlockInvalidSlotBlockHeaderSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "mainnet",
            "phase0",
            "operations",
            "block_header",
            "pyspec_tests",
            "invalid_slot_block_header");
    return genericBlockHeaderSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processBeaconBlockProposerSlashedSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "mainnet", "phase0", "operations", "block_header", "pyspec_tests", "proposer_slashed");
    return genericBlockHeaderSetup(path, configPath);
  }

  @ParameterizedTest(
      name = "{index}. process BeaconBlock beaconBlock={0} bls_setting={1} -> pre={2}")
  @MethodSource("processInvalidSignatureBlockHeaderSetup")
  void processBeaconBlock(BeaconBlock beaconBlock, Integer bls_setting, BeaconState pre)
      throws Exception {

    assertThrows(
        BlockProcessingException.class,
        () -> {
          process_block_header(pre, beaconBlock);
        });
  }

  @MustBeClosed
  static Stream<Arguments> processInvalidSignatureBlockHeaderSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "mainnet",
            "phase0",
            "operations",
            "block_header",
            "pyspec_tests",
            "invalid_sig_block_header");
    return invalidSignatureBlockHeaderSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process BeaconBlock beaconBlock={0} pre={1} -> post={2}")
  @MethodSource({"processBeaconBlockSuccessSetup"})
  void processBeaconBlock(BeaconBlock beaconBlock, BeaconState pre, BeaconState post) {

    assertDoesNotThrow(
        () -> {
          process_block_header(pre, beaconBlock);
        });
    assertEquals(pre, post);
  }

  @MustBeClosed
  static Stream<Arguments> processBeaconBlockSuccessSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get(
            "mainnet",
            "phase0",
            "operations",
            "block_header",
            "pyspec_tests",
            "success_block_header");
    return blockHeaderSuccessSetup(path, configPath);
  }
}
