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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.BlsSetting;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.statetransition.exceptions.StateTransitionException;

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
                    loadSsz(
                        testDefinition,
                        "blocks_" + index + ".ssz",
                        SignedBeaconBlock.getSszSchema()))
            .collect(Collectors.toList());

    final Optional<BeaconState> expectedState;
    if (testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILENAME).toFile().exists()) {
      expectedState = Optional.of(loadStateFromSsz(testDefinition, EXPECTED_STATE_FILENAME));
    } else {
      expectedState = Optional.empty();
    }

    runBlockProcessor(this::applyBlocks, testDefinition, metaData, preState, blocks, expectedState);
  }

  private void runBlockProcessor(
      final BlocksProcessor processor,
      final TestDefinition testDefinition,
      final SanityBlocksMetaData metaData,
      final BeaconState preState,
      final List<SignedBeaconBlock> blocks,
      final Optional<BeaconState> expectedState) {
    final Spec spec = testDefinition.getSpec();
    expectedState.ifPresentOrElse(
        (state) ->
            assertThat(processor.processBlocks(spec, metaData, preState, blocks)).isEqualTo(state),
        () ->
            assertThatThrownBy(() -> processor.processBlocks(spec, metaData, preState, blocks))
                .hasCauseInstanceOf(StateTransitionException.class));
  }

  private BeaconState applyBlocks(
      final Spec spec,
      final SanityBlocksMetaData metaData,
      final BeaconState preState,
      final List<SignedBeaconBlock> blocks) {
    try {
      BeaconState result = preState;
      for (SignedBeaconBlock block : blocks) {
        result = spec.initiateStateTransition(result, block, metaData.getBlsSetting() != IGNORED);
      }
      return result;
    } catch (StateTransitionException e) {
      throw new RuntimeException(e);
    }
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

  private interface BlocksProcessor {
    BeaconState processBlocks(
        final Spec spec,
        final SanityBlocksMetaData metaData,
        final BeaconState preState,
        final List<SignedBeaconBlock> blocks);
  }
}
