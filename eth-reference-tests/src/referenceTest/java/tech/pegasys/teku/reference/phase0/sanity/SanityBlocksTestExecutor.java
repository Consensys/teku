/*
 * Copyright 2020 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.reference.phase0.BlsSetting.IGNORED;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.BlsSetting;
import tech.pegasys.teku.reference.phase0.TestExecutor;

public class SanityBlocksTestExecutor implements TestExecutor {

  private static final String EXPECTED_STATE_FILENAME = "post.ssz";

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final SanityBlocksMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", SanityBlocksMetaData.class);
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz");
    final List<SignedBeaconBlock> blocks =
        IntStream.range(0, metaData.getBlocksCount())
            .mapToObj(
                index ->
                    loadSsz(testDefinition, "blocks_" + index + ".ssz", SignedBeaconBlock.class))
            .collect(Collectors.toList());

    if (testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILENAME).toFile().exists()) {
      final BeaconState expectedState = loadStateFromSsz(testDefinition, EXPECTED_STATE_FILENAME);
      assertThat(applyBlocks(metaData, preState, blocks)).isEqualTo(expectedState);
    } else {
      assertThatThrownBy(() -> applyBlocks(metaData, preState, blocks))
          .isInstanceOf(StateTransitionException.class);
    }
  }

  private BeaconState applyBlocks(
      final SanityBlocksMetaData metaData,
      final BeaconState preState,
      final List<SignedBeaconBlock> blocks)
      throws StateTransitionException {
    final StateTransition stateTransition = new StateTransition();
    BeaconState result = preState;
    for (SignedBeaconBlock block : blocks) {
      result = stateTransition.initiate(result, block, metaData.getBlsSetting() != IGNORED);
    }
    return result;
  }

  private static class SanityBlocksMetaData {
    @JsonProperty(value = "blocks_count", required = true)
    private int blocksCount;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    @JsonProperty(value = "reveal_deadlines_setting", required = false, defaultValue = "0")
    private int revealDeadlinesSetting;

    public int getBlocksCount() {
      return blocksCount;
    }

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }

    public int getRevealDeadlinesSetting() {
      return revealDeadlinesSetting;
    }
  }
}
