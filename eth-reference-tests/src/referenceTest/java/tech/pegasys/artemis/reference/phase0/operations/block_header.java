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
import static tech.pegasys.artemis.core.BlockProcessorUtil.process_block_header;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.core.exceptions.BlockProcessingException;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ethtests.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
public class block_header extends TestSuite {

  @ParameterizedTest(name = "{index}. minimal process block header success")
  @MethodSource({"mainnetBeaconBlockHeaderSuccessSetup", "minimalBeaconBlockHeaderSuccessSetup"})
  void mainnetProcessBeaconBlockHeaderSuccess(
      BeaconBlock block, BeaconState pre, BeaconState post) {
    BeaconState wState =
        assertDoesNotThrow(() -> pre.updated(state -> process_block_header(state, block)));
    assertEquals(post, wState);
  }

  @ParameterizedTest(name = "{index}. process block header")
  @MethodSource({"mainnetBeaconBlockHeaderSetup", "minimalBeaconBlockHeaderSetup"})
  void mainnetProcessBeaconBlockHeader(BeaconBlock block, BeaconState pre) {
    assertThrows(
        BlockProcessingException.class,
        () -> pre.updated(state -> process_block_header(state, block)));
  }

  @MustBeClosed
  static Stream<Arguments> blockSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "block_header", "pyspec_tests");
    return operationSetup(path, Paths.get(config), "block.ssz", BeaconBlock.class);
  }

  @MustBeClosed
  static Stream<Arguments> blockSuccessSetup(String config) throws Exception {
    Path path = Paths.get(config, "phase0", "operations", "block_header", "pyspec_tests");
    return operationSuccessSetup(path, Paths.get(config), "block.ssz", BeaconBlock.class);
  }

  @MustBeClosed
  static Stream<Arguments> minimalBeaconBlockHeaderSetup() throws Exception {
    return blockSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetBeaconBlockHeaderSetup() throws Exception {
    return blockSetup("mainnet");
  }

  @MustBeClosed
  static Stream<Arguments> minimalBeaconBlockHeaderSuccessSetup() throws Exception {
    return blockSuccessSetup("minimal");
  }

  @MustBeClosed
  static Stream<Arguments> mainnetBeaconBlockHeaderSuccessSetup() throws Exception {
    return blockSuccessSetup("mainnet");
  }
}
