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

import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;

public class ForkUpgradeTestExecutor implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> FORK_UPGRADE_TEST_TYPES =
      ImmutableMap.of("fork/fork", new ForkUpgradeTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final MetaData metadata = TestDataUtils.loadYaml(testDefinition, "meta.yaml", MetaData.class);

    if (metadata.fork.equals("altair")) {
      processAltairUpgrade(testDefinition);
    } else {
      throw new TestAbortedException(
          "Unhandled fork upgrade for test "
              + testDefinition.getDisplayName()
              + ": "
              + metadata.fork);
    }
  }

  private void processAltairUpgrade(final TestDefinition testDefinition) {
    final SpecVersion spec = testDefinition.getSpec().getGenesisSpec();
    final BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> phase0Schema =
        BeaconStateSchemaPhase0.create(spec.getConfig());
    final BeaconState preState =
        TestDataUtils.loadSsz(testDefinition, "pre.ssz_snappy", phase0Schema);
    final BeaconState postState = TestDataUtils.loadStateFromSsz(testDefinition, "post.ssz_snappy");

    final StateUpgrade<?> stateUpgrade =
        testDefinition.getSpec().getGenesisSpec().getStateUpgrade().orElseThrow();

    final BeaconState updated = stateUpgrade.upgrade(preState);

    assertThatSszData(updated).isEqualByGettersTo(postState);
  }

  private static class MetaData {
    @JsonProperty(value = "fork", required = true)
    private String fork;
  }
}
