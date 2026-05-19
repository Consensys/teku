/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceUpdateDataTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalBellatrix());

  @Test
  void withPayloadBuildingAttributes_shouldBridgePayloadContextToPreviousInstance() {
    final ForkChoiceState forkChoiceState = forkChoiceState();
    final PayloadBuildingAttributes payloadBuildingAttributes =
        payloadBuildingAttributes(forkChoiceState);
    final ForkChoiceUpdateData original =
        new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), Optional.empty());

    final ForkChoiceUpdateData replacement =
        original.withPayloadBuildingAttributes(Optional.of(payloadBuildingAttributes));

    assertThat(replacement).isNotSameAs(original);
    assertThatSafeFuture(original.getExecutionPayloadContext()).isNotCompleted();

    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(
            dataStructureUtil.randomBytes8(), forkChoiceState, payloadBuildingAttributes);
    replacement.getExecutionPayloadContext().complete(Optional.of(executionPayloadContext));

    assertThatSafeFuture(original.getExecutionPayloadContext())
        .isCompletedWithOptionalContaining(executionPayloadContext);
  }

  private ForkChoiceState forkChoiceState() {
    return new ForkChoiceState(
        ForkChoiceNode.createBase(dataStructureUtil.randomBytes32()),
        UInt64.ONE,
        UInt64.ONE,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        false);
  }

  private PayloadBuildingAttributes payloadBuildingAttributes(
      final ForkChoiceState forkChoiceState) {
    return new PayloadBuildingAttributes(
        UInt64.ONE,
        UInt64.valueOf(2),
        UInt64.valueOf(3),
        dataStructureUtil.randomBytes32(),
        Eth1Address.ZERO,
        Optional.empty(),
        Optional.empty(),
        forkChoiceState.headBlock());
  }
}
