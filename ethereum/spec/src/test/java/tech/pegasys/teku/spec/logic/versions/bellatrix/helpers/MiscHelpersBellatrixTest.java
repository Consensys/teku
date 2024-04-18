/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.bellatrix.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MiscHelpersBellatrixTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final MiscHelpersBellatrix miscHelpersBellatrix =
      new MiscHelpersBellatrix(spec.getGenesisSpecConfig().toVersionBellatrix().orElseThrow());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void shouldHaveCorrectTransitionCompleteWithTransitionedState() throws Exception {
    final ExecutionPayloadHeader defaultPayloadHeader =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSchemaDefinitions())
            .getExecutionPayloadHeaderSchema()
            .getDefault();
    final BeaconStateBellatrix preMerge =
        dataStructureUtil
            .stateBuilderBellatrix(32, 5)
            .latestExecutionPayloadHeader(defaultPayloadHeader)
            .build();
    final BeaconStateBellatrix postMerge =
        dataStructureUtil
            .stateBuilderBellatrix(32, 5)
            .latestExecutionPayloadHeader(dataStructureUtil.randomExecutionPayloadHeader())
            .build();
    final BeaconState preMergeTransitioned =
        spec.processSlots(preMerge, preMerge.getSlot().plus(2));
    final BeaconState postMergeTransitioned =
        spec.processSlots(postMerge, postMerge.getSlot().plus(2));

    assertThat(miscHelpersBellatrix.isMergeTransitionComplete(preMerge)).isFalse();
    assertThat(miscHelpersBellatrix.isMergeTransitionComplete(preMergeTransitioned)).isFalse();
    assertThat(miscHelpersBellatrix.isMergeTransitionComplete(postMerge)).isTrue();
    assertThat(miscHelpersBellatrix.isMergeTransitionComplete(postMergeTransitioned)).isTrue();
  }
}
