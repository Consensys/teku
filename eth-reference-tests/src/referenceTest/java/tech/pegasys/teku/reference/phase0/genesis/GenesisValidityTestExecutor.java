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

package tech.pegasys.teku.reference.phase0.genesis;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.is_valid_genesis_state;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.util.config.Constants;

public class GenesisValidityTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final BeaconState state = loadStateFromSsz(testDefinition, "genesis.ssz");
    final boolean expectedValidity = loadYaml(testDefinition, "is_valid.yaml", Boolean.class);
    final int activeValidatorCount =
        ValidatorsUtil.get_active_validator_indices(state, UInt64.valueOf(Constants.GENESIS_EPOCH))
            .size();
    final boolean result = is_valid_genesis_state(state.getGenesis_time(), activeValidatorCount);
    assertThat(result).isEqualTo(expectedValidity);
  }
}
