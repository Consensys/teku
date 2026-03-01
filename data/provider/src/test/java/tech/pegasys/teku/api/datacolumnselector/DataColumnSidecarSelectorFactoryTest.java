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

package tech.pegasys.teku.api.datacolumnselector;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.DataColumnSidecarsAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

@TestSpecContext(allMilestones = true)
public class DataColumnSidecarSelectorFactoryTest {

  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final List<UInt64> indices = List.of(UInt64.ZERO, UInt64.ONE);
  private final SignedBeaconBlock block = data.randomSignedBeaconBlock();
  private final List<DataColumnSidecar> dataColumnSidecars =
      IntStream.range(0, 5)
          .mapToObj(index -> data.randomDataColumnSidecar(block.asHeader(), UInt64.valueOf(index)))
          .toList();
  private final DataColumnSidecarSelectorFactory dataColumnSidecarSelectorFactory =
      new DataColumnSidecarSelectorFactory(spec, client);

  @Test
  public void headSelector_shouldGetHeadDataColumnSidecars()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.getDataColumnSidecars(blockAndState.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.headSelector().getDataColumnSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedDataColumnSidecars()
      throws ExecutionException, InterruptedException {
    final AnchorPoint anchorPoint = data.randomAnchorPoint(UInt64.ONE);

    when(client.getLatestFinalized()).thenReturn(Optional.of(anchorPoint));
    when(client.getDataColumnSidecars(anchorPoint.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.finalizedSelector().getDataColumnSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @Test
  public void genesisSelector_shouldGetGenesisDataColumnSidecars()
      throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.genesisSelector().getDataColumnSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @Test
  public void blockRootSelector_shouldGetDataColumnSidecarsForFinalizedSlot()
      throws ExecutionException, InterruptedException {
    final UInt64 finalizedSlot = UInt64.valueOf(42);
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    when(client.getFinalizedSlotByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(finalizedSlot)));
    when(client.getDataColumnSidecars(finalizedSlot, indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .blockRootSelector(block.getRoot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @Test
  public void blockRootSelector_shouldGetDataColumnSidecarsByRetrievingBlock()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    when(client.getFinalizedSlotByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(client.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .blockRootSelector(block.getRoot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @Test
  public void slotSelector_shouldGetDataColumnSidecarsFromFinalizedSlot()
      throws ExecutionException, InterruptedException {
    when(client.isFinalized(block.getSlot())).thenReturn(true);
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @Test
  public void slotSelector_shouldGetDataColumnSidecarsByRetrievingBlockWhenSlotNotFinalized()
      throws ExecutionException, InterruptedException {

    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class,
        () -> dataColumnSidecarSelectorFactory.createSelectorForBlockId("a"));
  }

  @Test
  public void stateRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> dataColumnSidecarSelectorFactory.stateRootSelector(data.randomBytes32()));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class,
        () -> dataColumnSidecarSelectorFactory.createSelectorForBlockId("justified"));
  }

  @Test
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class, dataColumnSidecarSelectorFactory::justifiedSelector);
  }

  @Test
  public void shouldNotLookForDataColumnSidecarsWhenNoKzgCommitments()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    final SignedBeaconBlock blockWithEmptyCommitments =
        data.randomSignedBeaconBlockWithEmptyCommitments();
    when(client.isFinalized(blockWithEmptyCommitments.getSlot())).thenReturn(false);
    when(client.getFinalizedSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(client.getBlockByBlockRoot(blockWithEmptyCommitments.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithEmptyCommitments)));
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    Optional<DataColumnSidecarsAndMetaData> maybeDataColumnSidecars =
        dataColumnSidecarSelectorFactory
            .blockRootSelector(blockWithEmptyCommitments.getRoot())
            .getDataColumnSidecars(indices)
            .get();
    verify(client, never()).getDataColumnSidecars(any(UInt64.class), anyList());
    assertThat(maybeDataColumnSidecars).isPresent();
    assertThat(maybeDataColumnSidecars.get().getData()).isEmpty();
  }

  @TestTemplate
  public void shouldLookForDataColumnSidecarsBySlotOnlyAfterFulu(
      final TestSpecInvocationContextProvider.SpecContext ctx)
      throws ExecutionException, InterruptedException {
    final DataColumnSidecarSelectorFactory dataColumnSidecarSelectorFactory =
        new DataColumnSidecarSelectorFactory(ctx.getSpec(), client);
    final SignedBeaconBlock block = new DataStructureUtil(ctx.getSpec()).randomSignedBeaconBlock();
    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of(data.randomDataColumnSidecar())));
    dataColumnSidecarSelectorFactory
        .slotSelector(block.getSlot())
        .getDataColumnSidecars(indices)
        .get();
    if (ctx.getSpec().isMilestoneSupported(SpecMilestone.FULU)) {
      verify(client).getDataColumnSidecars(any(UInt64.class), anyList());
    } else {
      verify(client, never()).getDataColumnSidecars(any(UInt64.class), anyList());
    }
  }

  @TestTemplate
  public void shouldLookForDataColumnSidecarsByHeadOnlyAfterFulu(
      final TestSpecInvocationContextProvider.SpecContext ctx)
      throws ExecutionException, InterruptedException {
    final DataColumnSidecarSelectorFactory dataColumnSidecarSelectorFactory =
        new DataColumnSidecarSelectorFactory(ctx.getSpec(), client);

    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.getDataColumnSidecars(blockAndState.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    dataColumnSidecarSelectorFactory.headSelector().getDataColumnSidecars(indices).get();

    if (ctx.getSpec().isMilestoneSupported(SpecMilestone.FULU)) {
      verify(client).getDataColumnSidecars(any(UInt64.class), anyList());
    } else {
      verify(client, never()).getDataColumnSidecars(any(UInt64.class), anyList());
    }
  }

  @Test
  public void genesisSelector_shouldAlwaysReturnOptimisticMetadataFieldFalse()
      throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.genesisSelector().getDataColumnSidecars(indices).get();
    assertThat(result).isNotEmpty();
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData = result.get();
    assertThat(dataColumnSidecarsAndMetaData.isExecutionOptimistic()).isFalse();
  }

  @Test
  public void slotSelector_whenSelectingFinalizedBlockMetadataReturnsFinalizedTrue()
      throws ExecutionException, InterruptedException {
    when(client.isFinalized(block.getSlot())).thenReturn(true);
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();

    assertThat(result).isNotEmpty();
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData = result.get();
    assertThat(dataColumnSidecarsAndMetaData.isFinalized()).isTrue();
  }

  @Test
  public void slotSelector_whenSelectingNonFinalizedBlockMetadataReturnsFinalizedFalse()
      throws ExecutionException, InterruptedException {
    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();

    assertThat(result).isNotEmpty();
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData = result.get();
    assertThat(dataColumnSidecarsAndMetaData.isFinalized()).isFalse();
  }
}
