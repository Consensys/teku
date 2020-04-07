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

package tech.pegasys.artemis.reference.phase0.sanity;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.StateTransitionException;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ethtests.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
public class blocksMainnetInvalid extends TestSuite {

  @ParameterizedTest(name = "{index}.{1} Sanity blocks invalid")
  @MethodSource({
    "sanityInvalidStateRootSetup",
    "sanityExpectedDepositInBlockSetup",
    "sanityPrevSlotBlockTransitionSetup"
  })
  void sanityProcessBlockInvalid(BeaconState pre, String testName, List<SignedBeaconBlock> blocks) {
    StateTransition stateTransition = new StateTransition();
    blocks.forEach(
        block ->
            assertThrows(
                StateTransitionException.class, () -> stateTransition.initiate(pre, block, true)));
  }

  @MustBeClosed
  static Stream<Arguments> sanityInvalidStateRootSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/invalid_state_root");
    return sanityMultiBlockSetupInvalid(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityExpectedDepositInBlockSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/expected_deposit_in_block");
    return sanityMultiBlockSetupInvalid(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityPrevSlotBlockTransitionSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/prev_slot_block_transition");
    return sanityMultiBlockSetupInvalid(path, configPath);
  }
}
