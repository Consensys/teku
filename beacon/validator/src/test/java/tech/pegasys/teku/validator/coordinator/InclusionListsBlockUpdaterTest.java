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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.UpdatableStore;

class InclusionListsBlockUpdaterTest {

  private final Spec spec = mock(Spec.class);
  private final Spec hezeSpec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(hezeSpec);
  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final BeaconState state = mock(BeaconState.class);
  private final InclusionListsBlockUpdater inclusionListsBlockUpdater =
      new InclusionListsBlockUpdater(
          forkChoiceNotifier, proposersDataManager, combinedChainDataClient, spec);

  @Test
  void onUpdateBlockWithInclusionListsDue_shouldNotRequestPayloadIdWhenNoTransactions() {
    final UInt64 inclusionListSlot = UInt64.valueOf(10);
    final UInt64 proposerSlot = inclusionListSlot.increment();
    final InclusionList emptyInclusionList = dataStructureUtil.randomInclusionList(0);

    when(combinedChainDataClient.getStateAtSlotExact(proposerSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(proposersDataManager.isProposerForSlot(proposerSlot, state)).thenReturn(true);
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(store.getInclusionLists(inclusionListSlot))
        .thenReturn(Optional.of(List.of(emptyInclusionList)));

    assertThatSafeFuture(
            inclusionListsBlockUpdater.onUpdateBlockWithInclusionListsDue(inclusionListSlot))
        .isCompletedWithEmptyOptional();

    verifyNoInteractions(forkChoiceNotifier);
  }

  @Test
  void onUpdateBlockWithInclusionListsDue_shouldRefreshPayloadIdWithInclusionListTransactions() {
    final UInt64 inclusionListSlot = UInt64.valueOf(10);
    final UInt64 proposerSlot = inclusionListSlot.increment();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes8 payloadId = Bytes8.fromHexString("0x0102030405060708");
    final InclusionList inclusionList = dataStructureUtil.randomInclusionList(2);
    final List<Bytes> transactions =
        inclusionList.getTransactions().stream()
            .map(transaction -> transaction.getBytes())
            .toList();
    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(
            payloadId, mock(ForkChoiceState.class), mock(PayloadBuildingAttributes.class));

    when(combinedChainDataClient.getStateAtSlotExact(proposerSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(proposersDataManager.isProposerForSlot(proposerSlot, state)).thenReturn(true);
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(store.getInclusionLists(inclusionListSlot))
        .thenReturn(Optional.of(List.of(inclusionList)));
    when(spec.getBlockRootAtSlot(state, inclusionListSlot)).thenReturn(parentRoot);
    when(forkChoiceNotifier.getPayloadId(parentRoot, proposerSlot, transactions))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));

    assertThatSafeFuture(
            inclusionListsBlockUpdater.onUpdateBlockWithInclusionListsDue(inclusionListSlot))
        .isCompletedWithOptionalContaining(payloadId);

    verify(forkChoiceNotifier).getPayloadId(parentRoot, proposerSlot, transactions);
  }
}
