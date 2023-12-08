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

package tech.pegasys.teku.api.blockrootselector;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlockRootSelectorFactoryTest {

  private static final Spec SPEC = TestSpecFactory.createDefault();
  private static final DataStructureUtil DATA_STRUCTURE_UTIL = new DataStructureUtil(SPEC);
  private static final SignedBeaconBlock BLOCK = DATA_STRUCTURE_UTIL.randomSignedBeaconBlock(100);
  private final SpecMilestone milestone = SPEC.getGenesisSpec().getMilestone();
  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private BlockRootSelectorFactory blockRootSelectorFactory =
      new BlockRootSelectorFactory(SPEC, client);

  public static Stream<Arguments> finalizedBlockFallbackParams() {
    return Stream.of(
        Arguments.of(Optional.empty(), Optional.of(BLOCK.getSlot())),
        Arguments.of(Optional.of(BLOCK.getRoot()), Optional.empty()),
        Arguments.of(Optional.empty(), Optional.empty()));
  }

  @Test
  public void headSelector_shouldGetBestBlockRoot()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = DATA_STRUCTURE_UTIL.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.isFinalized(blockAndState.getSlot())).thenReturn(true);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        blockRootSelectorFactory.headSelector().getBlockRoot().get();
    verify(client).getChainHead();
    verify(client).isFinalized(blockAndState.getSlot());
    assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(blockAndState.getBlock().getRoot(), false, true, true));
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedBlockRoot()
      throws ExecutionException, InterruptedException {
    when(client.getFinalizedBlockRoot()).thenReturn(Optional.of(BLOCK.getRoot()));
    when(client.getFinalizedBlockSlot()).thenReturn(Optional.of(BLOCK.getSlot()));
    when(client.isChainHeadOptimistic()).thenReturn(false);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        blockRootSelectorFactory.finalizedSelector().getBlockRoot().get();
    verify(client).getFinalizedBlockRoot();
    verify(client).getFinalizedBlockSlot();
    verify(client, never()).getFinalizedBlock();
    verify(client).isChainHeadOptimistic();
    assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(BLOCK.getRoot(), false, true, true));
  }

  @ParameterizedTest
  @MethodSource("finalizedBlockFallbackParams")
  public void finalizedSelector_shouldGetFinalizedBlockRoot_fromFinalizedBlock(
      final Optional<Bytes32> maybeBlockRoot, final Optional<UInt64> maybeSlot)
      throws ExecutionException, InterruptedException {
    when(client.getFinalizedBlockRoot()).thenReturn(maybeBlockRoot);
    when(client.getFinalizedBlockSlot()).thenReturn(maybeSlot);
    when(client.getFinalizedBlock()).thenReturn(Optional.of(BLOCK));
    when(client.isChainHeadOptimistic()).thenReturn(false);
    when(client.isFinalized(BLOCK.getSlot())).thenReturn(true);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        blockRootSelectorFactory.finalizedSelector().getBlockRoot().get();
    verify(client).getFinalizedBlockRoot();
    verify(client).getFinalizedBlockSlot();
    verify(client).getFinalizedBlock();
    verify(client).isChainHeadOptimistic();
    assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(BLOCK.getRoot(), false, true, true));
  }

  @Test
  public void genesisSelector_shouldGetSlotZero() throws ExecutionException, InterruptedException {
    when(client.getBlockRootAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(BLOCK.getRoot())));
    when(client.isFinalized(UInt64.ZERO)).thenReturn(true);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        blockRootSelectorFactory.genesisSelector().getBlockRoot().get();
    verify(client).getBlockRootAtSlotExact(UInt64.ZERO);
    verify(client).isFinalized(UInt64.ZERO);
    AssertionsForClassTypes.assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(BLOCK.getRoot(), false, true, true));
  }

  @Test
  public void blockRootSelector_shouldGetBlockRootByRoot()
      throws ExecutionException, InterruptedException {
    when(client.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(BLOCK)));
    final SignedBlockAndState head =
        DATA_STRUCTURE_UTIL.randomSignedBlockAndState(BLOCK.getSlot().plus(3), BLOCK.getRoot());
    final ChainHead chainHead = ChainHead.create(head);
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.isCanonicalBlock(BLOCK.getSlot(), BLOCK.getRoot(), chainHead.getRoot()))
        .thenReturn(true);
    when(client.isFinalized(BLOCK.getSlot())).thenReturn(false);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        blockRootSelectorFactory.blockRootSelector(BLOCK.getRoot()).getBlockRoot().get();
    verify(client).getBlockByBlockRoot(BLOCK.getRoot());
    verify(client).getChainHead();
    verify(client).isCanonicalBlock(BLOCK.getSlot(), BLOCK.getRoot(), chainHead.getRoot());
    verify(client).isFinalized(BLOCK.getSlot());
    AssertionsForClassTypes.assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(BLOCK.getRoot(), false, true, false));
  }

  @Test
  public void slotSelector_shouldGetBlockRootAtSlotExact()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState head = DATA_STRUCTURE_UTIL.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(head)));
    when(client.getBlockRootAtSlotExact(BLOCK.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(BLOCK.getRoot())));
    when(client.isFinalized(BLOCK.getSlot())).thenReturn(false);
    final Optional<ObjectAndMetaData<Bytes32>> maybeBlockRootAndMetaData =
        blockRootSelectorFactory.slotSelector(BLOCK.getSlot()).getBlockRoot().get();
    verify(client).getChainHead();
    verify(client).getBlockRootAtSlotExact(BLOCK.getSlot());
    verify(client, never()).getBlockAtSlotExact(BLOCK.getSlot());
    AssertionsForClassTypes.assertThat(maybeBlockRootAndMetaData)
        .contains(withMetaData(BLOCK.getRoot(), false, true, false));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class, () -> blockRootSelectorFactory.createSelectorForBlockId("a"));
  }

  @Test
  public void stateRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> blockRootSelectorFactory.stateRootSelector(DATA_STRUCTURE_UTIL.randomBytes32()));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class,
        () -> blockRootSelectorFactory.createSelectorForBlockId("justified"));
  }

  @Test
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, blockRootSelectorFactory::justifiedSelector);
  }

  private ObjectAndMetaData<Bytes32> withMetaData(
      final Bytes32 blockRoot,
      final boolean isOptimistic,
      final boolean isCanonical,
      final boolean isFinalized) {
    return new ObjectAndMetaData<>(blockRoot, milestone, isOptimistic, isCanonical, isFinalized);
  }
}
