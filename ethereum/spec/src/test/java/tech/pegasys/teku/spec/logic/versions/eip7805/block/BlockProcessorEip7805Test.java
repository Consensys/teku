/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.eip7805.block;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDenebTest;

public class BlockProcessorEip7805Test extends BlockProcessorDenebTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetEip7805();
  }

  @Test
  void
      shouldFailProcessingIfInclusionListTransactionsInBlockAreMoreThanMaxTransactionPerInclusionList() {
    final BeaconState preState = createBeaconState();
    final UInt64 slot = preState.getSlot().increment();
    final int maxTransactionPerInclusionList =
        SpecConfigEip7805.required(spec.atSlot(slot).getConfig())
            .getMaxTransactionsPerInclusionList();
    final BeaconBlockBody blockBody = dataStructureUtil.randomBeaconBlockBody(slot);
    final List<InclusionList> inclusionLists =
        List.of(dataStructureUtil.randomInclusionList(maxTransactionPerInclusionList + 1));
    assertThatThrownBy(
            () ->
                spec.getBlockProcessor(slot)
                    .validateExecutionPayload(
                        preState, blockBody, Optional.empty(), Optional.of(inclusionLists)))
        .isInstanceOf(BlockProcessingException.class)
        .hasMessage(
            "Number of transaction in the inclusion list in block exceeds max transaction per inclusion list");
  }
}
