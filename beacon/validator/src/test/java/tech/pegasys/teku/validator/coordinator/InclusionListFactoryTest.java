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

package tech.pegasys.teku.validator.coordinator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class InclusionListFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final InclusionListFactory inclusionListFactory =
      new InclusionListFactory(executionLayerChannel, combinedChainDataClient, spec);

  @Test
  void getInclusionListShouldUseLatestExecutionPayloadBidParentBlockHash() {
    final UInt64 slot = UInt64.valueOf(12);
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final ExecutionPayloadBid latestExecutionPayloadBid =
        dataStructureUtil.randomExecutionPayloadBid(
            parentBlockHash, slot, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3));
    final BeaconState state =
        dataStructureUtil
            .stateBuilderGloas(64, 16, 4)
            .slot(slot)
            .latestExecutionPayloadBid(latestExecutionPayloadBid)
            .build();
    final List<Transaction> transactions =
        dataStructureUtil.randomInclusionList(2).getTransactions();

    when(combinedChainDataClient.getBestState())
        .thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(executionLayerChannel.engineGetInclusionList(parentBlockHash, slot))
        .thenReturn(SafeFuture.completedFuture(transactions));

    assertThatSafeFuture(inclusionListFactory.getInclusionList(slot))
        .isCompletedWithValue(Optional.of(transactions));

    verify(executionLayerChannel).engineGetInclusionList(parentBlockHash, slot);
  }
}
