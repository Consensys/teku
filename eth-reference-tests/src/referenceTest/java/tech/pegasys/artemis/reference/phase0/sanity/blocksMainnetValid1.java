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
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.ethtests.TestSuite;
import tech.pegasys.artemis.statetransition.StateTransition;

@ExtendWith(BouncyCastleExtension.class)
public class blocksMainnetValid1 extends TestSuite {

  @ParameterizedTest(name = "{index}.{2} Sanity blocks valid (Mainnet)")
  @MethodSource({
    "sanityAttestationSetup",
    "sanityAttesterSlashingSetup",
    "sanityBalanceDrivenTransitionsSetup",
    "sanityDepositInBlockSetup",
    "sanityDepositTopUpSetup",
    "sanityEmptyBlockTransitionSetup",
  })
  void sanityProcessBlock(
      BeaconStateImpl pre, BeaconStateImpl post, String testName, List<SignedBeaconBlock> blocks) {
    StateTransition stateTransition = new StateTransition(false);
    blocks.forEach(block -> assertDoesNotThrow(() -> stateTransition.initiate(pre, block, true)));
    assertEquals(pre, post);
  }

  @MustBeClosed
  static Stream<Arguments> sanityAttestationSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/attestation");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityAttesterSlashingSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/attester_slashing");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityBalanceDrivenTransitionsSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path =
        Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/balance_driven_status_transitions");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityDepositInBlockSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/deposit_in_block");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityDepositTopUpSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/deposit_top_up");
    return sanityMultiBlockSetup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> sanityEmptyBlockTransitionSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/blocks/pyspec_tests/empty_block_transition");
    return sanityMultiBlockSetup(path, configPath);
  }
}
