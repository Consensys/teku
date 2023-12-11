/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.api.staterootselector;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class StateRootSelectorFactoryTest {

  private static final Spec SPEC = TestSpecFactory.createDefault();
  private static final DataStructureUtil DATA_STRUCTURE_UTIL = new DataStructureUtil(SPEC);
  private static final BeaconState STATE = DATA_STRUCTURE_UTIL.randomBeaconState(100);
  private final SpecMilestone milestone = SPEC.getGenesisSpec().getMilestone();
  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private StateRootSelectorFactory stateRootSelectorFactory =
      new StateRootSelectorFactory(SPEC, client);

  public static Stream<Arguments> finalizedBlockFallbackParams() {
    return Stream.of(
        Arguments.of(Optional.empty(), Optional.of(STATE.getSlot())),
        Arguments.of(Optional.of(STATE.hashTreeRoot()), Optional.empty()),
        Arguments.of(Optional.empty(), Optional.empty()));
  }

  @Test
  public void headSelector_shouldGetBestStateRoot()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = DATA_STRUCTURE_UTIL.randomSignedBlockAndState(100);
    final ChainHead chainHead = ChainHead.create(blockAndState);
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.isFinalized(blockAndState.getSlot())).thenReturn(true);
    final Optional<ObjectAndMetaData<Bytes32>> maybeStateRootAndMetaData =
        stateRootSelectorFactory.headSelector().getStateRoot().get();
    verify(client).getChainHead();
    verify(client).isFinalized(chainHead.getSlot());
    assertThat(maybeStateRootAndMetaData)
        .contains(withMetaData(chainHead.getStateRoot(), false, true, true));
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedStateRoot()
      throws ExecutionException, InterruptedException {
    when(client.getFinalizedStateRoot()).thenReturn(Optional.of(STATE.hashTreeRoot()));
    when(client.getFinalizedStateSlot()).thenReturn(Optional.of(STATE.getSlot()));
    when(client.isChainHeadOptimistic()).thenReturn(false);
    final Optional<ObjectAndMetaData<Bytes32>> maybeStateRootAndMetaData =
        stateRootSelectorFactory.finalizedSelector().getStateRoot().get();
    verify(client).getFinalizedStateRoot();
    verify(client).getFinalizedStateSlot();
    verify(client, never()).getFinalizedState();
    verify(client).isChainHeadOptimistic();
    assertThat(maybeStateRootAndMetaData)
        .contains(withMetaData(STATE.hashTreeRoot(), false, true, true));
  }

  @ParameterizedTest
  @MethodSource("finalizedBlockFallbackParams")
  public void finalizedSelector_shouldGetFinalizedStateRoot_fromFinalizedState(
      final Optional<Bytes32> maybeStateRoot, final Optional<UInt64> maybeSlot)
      throws ExecutionException, InterruptedException {
    when(client.getFinalizedStateRoot()).thenReturn(maybeStateRoot);
    when(client.getFinalizedBlockSlot()).thenReturn(maybeSlot);
    when(client.getFinalizedState()).thenReturn(Optional.of(STATE));
    when(client.isChainHeadOptimistic()).thenReturn(false);
    when(client.isFinalized(STATE.getSlot())).thenReturn(true);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        stateRootSelectorFactory.finalizedSelector().getStateRoot().get();
    verify(client).getFinalizedStateRoot();
    verify(client).getFinalizedStateSlot();
    verify(client).getFinalizedState();
    verify(client).isChainHeadOptimistic();
    assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(STATE.hashTreeRoot(), false, true, true));
  }

  @Test
  public void genesisSelector_shouldGetSlotZero() throws ExecutionException, InterruptedException {
    when(client.getStateAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(STATE)));
    when(client.isFinalized(STATE.getSlot())).thenReturn(true);
    final Optional<ObjectAndMetaData<Bytes32>> maybeStateRootAndMetaData =
        stateRootSelectorFactory.genesisSelector().getStateRoot().get();
    verify(client).getStateAtSlotExact(UInt64.ZERO);
    verify(client).isFinalized(STATE.getSlot());
    AssertionsForClassTypes.assertThat(maybeStateRootAndMetaData)
        .contains(withMetaData(STATE.hashTreeRoot(), false, true, true));
  }

  @Test
  public void stateRootSelector_shouldGetStateRootByRoot()
      throws ExecutionException, InterruptedException {
    when(client.getStateByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(STATE)));
    final SignedBlockAndState head =
        DATA_STRUCTURE_UTIL.randomSignedBlockAndState(
            STATE.getSlot().plus(3), STATE.hashTreeRoot());
    final ChainHead chainHead = ChainHead.create(head);
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.isCanonicalBlock(
            STATE.getSlot(), BeaconBlockHeader.fromState(STATE).getRoot(), chainHead.getRoot()))
        .thenReturn(true);
    when(client.isFinalized(STATE.getSlot())).thenReturn(false);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        stateRootSelectorFactory.stateRootSelector(STATE.hashTreeRoot()).getStateRoot().get();
    verify(client).getStateByBlockRoot(STATE.hashTreeRoot());
    verify(client).getChainHead();
    verify(client)
        .isCanonicalBlock(
            STATE.getSlot(), BeaconBlockHeader.fromState(STATE).getRoot(), chainHead.getRoot());
    verify(client).isFinalized(STATE.getSlot());
    AssertionsForClassTypes.assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(STATE.hashTreeRoot(), false, true, false));
  }

  @Test
  public void slotSelector_shouldGetBlockRootAtSlotExact()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState head = DATA_STRUCTURE_UTIL.randomSignedBlockAndState(100);
    ChainHead chainHead = ChainHead.create(head);
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.getStateAtSlotExact(STATE.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(STATE)));
    when(client.isFinalized(STATE.getSlot())).thenReturn(false);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        stateRootSelectorFactory.slotSelector(STATE.getSlot()).getStateRoot().get();
    verify(client).getChainHead();
    verify(client).getStateAtSlotExact(STATE.getSlot());
    AssertionsForClassTypes.assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(STATE.hashTreeRoot(), false, true, false));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class, () -> stateRootSelectorFactory.createSelectorForStateId("a"));
  }

  @Test
  public void blockRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> stateRootSelectorFactory.blockRootSelector(DATA_STRUCTURE_UTIL.randomBytes32()));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class,
        () -> stateRootSelectorFactory.createSelectorForStateId("justified"));
  }

  @Test
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, stateRootSelectorFactory::justifiedSelector);
  }

  private ObjectAndMetaData<Bytes32> withMetaData(
      final Bytes32 stateRoot,
      final boolean isOptimistic,
      final boolean isCanonical,
      final boolean isFinalized) {
    return new ObjectAndMetaData<>(stateRoot, milestone, isOptimistic, isCanonical, isFinalized);
  }
}
