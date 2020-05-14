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

package tech.pegasys.teku.reference.phase0.sanity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ethtests.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
public class blocksMainnetValid2 extends TestSuite {

  @ParameterizedTest(name = "{index}.{2} Sanity blocks valid (Mainnet)")
  @MethodSource({
    "sanityEmptyEpochTransitionSetup",
    "sanityHistoricalBatchSetup",
    "sanityProposerSlashingSetup",
    "sanitySkippedSlotsSetup",
    "sanityVoluntaryExitSetup",
  })
  void sanityProcessBlock(
      BeaconState pre, BeaconState post, String testName, List<SignedBeaconBlock> blocks) {
    StateTransition stateTransition = new StateTransition();
    BeaconState result =
        blocks.stream()
            .reduce(
                pre,
                (preState, block) ->
                    assertDoesNotThrow(() -> stateTransition.initiate(preState, block, true)),
                (preState, postState) -> postState);
    assertEquals(post, result);
  }

  @MustBeClosed
  static Stream<Arguments> sanityEmptyEpochTransitionSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/empty_epoch_transition");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityHistoricalBatchSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/historical_batch");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityProposerSlashingSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/proposer_slashing");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanitySkippedSlotsSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/skipped_slots");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityVoluntaryExitSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/voluntary_exit");
    return sanityMultiBlockSetup(path, configPath);
  }
}
