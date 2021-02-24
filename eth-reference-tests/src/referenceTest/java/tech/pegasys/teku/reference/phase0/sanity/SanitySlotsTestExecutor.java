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
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.phase0.TestDataUtils.loadYaml;

import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.statetransition.exceptions.SlotProcessingException;

public class SanitySlotsTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final int numberOfSlots = loadYaml(testDefinition, "slots.yaml", Integer.class);
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz");
    final BeaconState expectedState = loadStateFromSsz(testDefinition, "post.ssz");

    final UInt64 endSlot = preState.getSlot().plus(numberOfSlots);

    final BeaconState result = processSlots(testDefinition.getSpecProvider(), preState, endSlot);
    assertThat(result).isEqualTo(expectedState);
  }

  private BeaconState processSlots(
      final SpecProvider specProvider, final BeaconState preState, final UInt64 endSlot)
      throws EpochProcessingException, SlotProcessingException {
    return specProvider.processSlots(preState, endSlot);
  }
}
