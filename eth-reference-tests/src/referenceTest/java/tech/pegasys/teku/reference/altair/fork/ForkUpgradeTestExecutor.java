/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.reference.altair.fork;

import static tech.pegasys.teku.ssz.SszDataAssert.assertThatSszData;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.TestConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ForkUpgradeTestExecutor implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> FORK_UPGRADE_TEST_TYPES =
      ImmutableMap.of("transition/core", new ForkUpgradeTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final MetaData metadata = TestDataUtils.loadYaml(testDefinition, "meta.yaml", MetaData.class);

    if (metadata.postFork.equals("altair")) {
      processAltairUpgrade(testDefinition, metadata);
    } else {
      throw new TestAbortedException(
          "Unhandled fork upgrade for test "
              + testDefinition.getDisplayName()
              + ": "
              + metadata.postFork);
    }
  }

  private void processAltairUpgrade(final TestDefinition testDefinition, final MetaData metadata)
      throws Exception {

    final UInt64 forkEpoch = UInt64.valueOf(metadata.forkEpoch);
    final SpecConfig config =
        TestConfigLoader.loadConfig(
            testDefinition.getConfigName(),
            c -> c.altairBuilder(a -> a.altairForkEpoch(forkEpoch)));
    final Spec spec = SpecFactory.create(config, Optional.of(forkEpoch));
    final BeaconState preState =
        TestDataUtils.loadSsz(testDefinition, "pre.ssz_snappy", spec::deserializeBeaconState);
    final BeaconState postState =
        TestDataUtils.loadSsz(testDefinition, "post.ssz_snappy", spec::deserializeBeaconState);

    BeaconState result = preState;
    for (int i = 0; i < metadata.blocksCount; i++) {
      final SignedBeaconBlock block =
          TestDataUtils.loadSsz(
              testDefinition, "blocks_" + i + ".ssz_snappy", spec::deserializeSignedBeaconBlock);

      result = spec.initiateStateTransition(result, block, true);
    }
    assertThatSszData(result).isEqualByGettersTo(postState);
  }

  @SuppressWarnings({"unused", "UnusedVariable"})
  private static class MetaData {
    @JsonProperty(value = "post_fork", required = true)
    private String postFork;

    @JsonProperty(value = "fork_epoch", required = true)
    private int forkEpoch;

    @JsonProperty(value = "blocks_count", required = true)
    private int blocksCount;

    @JsonProperty(value = "fork_block", required = true)
    private int forkBlock;
  }
}
