/*
 * Copyright ConsenSys Software Inc., 2022
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
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;

public class ForkUpgradeTestExecutor implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> FORK_UPGRADE_TEST_TYPES =
      ImmutableMap.of("fork/fork", new ForkUpgradeTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final MetaData metadata = TestDataUtils.loadYaml(testDefinition, "meta.yaml", MetaData.class);
    final SpecMilestone specMilestone = SpecMilestone.forName(metadata.fork);
    processUpgrade(testDefinition, specMilestone);
  }

  private void processUpgrade(final TestDefinition testDefinition, final SpecMilestone milestone) {
    final SpecVersion spec = testDefinition.getSpec().getGenesisSpec();
    final BeaconStateSchema<?, ?> fromMilestoneSchema;
    switch (milestone) {
      case ALTAIR:
        fromMilestoneSchema = BeaconStateSchemaPhase0.create(spec.getConfig());
        break;
      case BELLATRIX:
        fromMilestoneSchema = BeaconStateSchemaAltair.create(spec.getConfig());
        break;
      case CAPELLA:
        fromMilestoneSchema = BeaconStateSchemaBellatrix.create(spec.getConfig());
        break;
      case DENEB:
        fromMilestoneSchema = BeaconStateSchemaCapella.create(spec.getConfig());
        break;
      default:
        throw new IllegalStateException(
            "Unhandled fork upgrade for test "
                + testDefinition.getDisplayName()
                + ": "
                + milestone);
    }
    final BeaconState preState =
        TestDataUtils.loadSsz(testDefinition, "pre.ssz_snappy", fromMilestoneSchema);
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
