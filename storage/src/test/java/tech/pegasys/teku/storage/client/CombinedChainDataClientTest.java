/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;

/** Note: Most tests should be added to the integration-test directory */
class CombinedChainDataClientTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private final CombinedChainDataClient client =
      new CombinedChainDataClient(
          recentChainData,
          historicalChainData,
          spec,
          new EarliestAvailableBlockSlot(historicalChainData, new SystemTimeProvider(), 0));
  private final ChainHead chainHead = mock(ChainHead.class);

  final List<SignedBeaconBlock> nonCanonicalBlocks = new ArrayList<>();
  final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final SignedBeaconBlock secondBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final BlobSidecar sidecar = dataStructureUtil.randomBlobSidecar();

  @BeforeEach
  void setUp() {
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(forkChoiceStrategy));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(forkChoiceStrategy.isOptimistic(any())).thenReturn(Optional.of(true));
    when(chainHead.isOptimistic()).thenReturn(false);
    when(chainHead.getSlot()).thenReturn(UInt64.valueOf(8428924L));
  }

  @Test
  public void getCommitteesFromStateWithCache_shouldReturnCommitteeAssignments() {
    BeaconState state = dataStructureUtil.randomBeaconState();
    List<CommitteeAssignment> data =
        client.getCommitteesFromState(state, spec.getCurrentEpoch(state));
    assertThat(data.size()).isEqualTo(spec.getSlotsPerEpoch(state.getSlot()));
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldAddCanonicalBlockIfPresent() {
    nonCanonicalBlocks.add(firstBlock);
    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.of(secondBlock))
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnNonCanonicalOnly() {
    nonCanonicalBlocks.add(firstBlock);
    nonCanonicalBlocks.add(secondBlock);

    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.empty())
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnCanonicalOnly() {
    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.of(secondBlock))
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(secondBlock);
  }

  @Test
  void isOptimistic_shouldReturnOptimisticStatusOfKnownBlocks() {
    when(forkChoiceStrategy.isOptimistic(firstBlock.getRoot())).thenReturn(Optional.of(true));
    when(forkChoiceStrategy.isOptimistic(secondBlock.getRoot())).thenReturn(Optional.of(false));

    assertThat(client.isOptimisticBlock(firstBlock.getRoot())).isTrue();
    assertThat(client.isOptimisticBlock(secondBlock.getRoot())).isFalse();
  }

  @Test
  void isOptimistic_shouldReturnOptimisticStatusOfFinalizedBlockForUnknownBlocks() {
    final Checkpoint finalized = dataStructureUtil.randomCheckpoint();
    when(recentChainData.getFinalizedCheckpoint()).thenReturn(Optional.of(finalized));
    when(forkChoiceStrategy.isOptimistic(firstBlock.getRoot())).thenReturn(Optional.empty());
    when(forkChoiceStrategy.isOptimistic(finalized.getRoot())).thenReturn(Optional.of(true));

    assertThat(client.isOptimisticBlock(firstBlock.getRoot())).isTrue();
  }

  @Test
  void getsEarliestAvailableBlobSidecarEpoch() {
    when(historicalChainData.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(UInt64.ONE)));

    final Optional<UInt64> result =
        SafeFutureAssert.safeJoin(client.getEarliestAvailableBlobSidecarEpoch());

    assertThat(result).hasValue(UInt64.ZERO);
  }

  @Test
  void getsBlobSidecarBySlotAndBlockRootAndBlobIndex() {
    final SlotAndBlockRootAndBlobIndex correctKey =
        new SlotAndBlockRootAndBlobIndex(
            sidecar.getSlot(), sidecar.getBlockRoot(), sidecar.getIndex());
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRootAndBlobIndex incorrectKey =
        new SlotAndBlockRootAndBlobIndex(sidecar.getSlot(), blockRoot, sidecar.getIndex());
    setupGetBlobSidecar(correctKey, sidecar);

    final Optional<BlobSidecar> correctResult =
        SafeFutureAssert.safeJoin(client.getBlobSidecarByKey(correctKey));
    assertThat(correctResult).hasValue(sidecar);

    final Optional<BlobSidecar> incorrectResult =
        SafeFutureAssert.safeJoin(client.getBlobSidecarByKey(incorrectKey));
    assertThat(incorrectResult).isEmpty();
  }

  @Test
  void getsBlobSidecarBySlotAndBlobIndex() {
    final SlotAndBlockRootAndBlobIndex key =
        new SlotAndBlockRootAndBlobIndex(
            sidecar.getSlot(), sidecar.getBlockRoot(), sidecar.getIndex());
    setupGetBlobSidecar(key, sidecar);
    setupGetSlotForBlockRoot(sidecar.getBlockRoot(), sidecar.getSlot());

    final Optional<BlobSidecar> result =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(sidecar.getBlockRoot(), sidecar.getIndex()));
    assertThat(result).hasValue(sidecar);

    final Bytes32 blockRoot = setupGetBlockByBlockRoot(sidecar.getSlot().plus(1));
    final Optional<BlobSidecar> incorrectResult =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(blockRoot, sidecar.getIndex()));
    assertThat(incorrectResult).isEmpty();
  }

  private void setupGetBlobSidecar(
      final SlotAndBlockRootAndBlobIndex key, final BlobSidecar result) {
    when(historicalChainData.getBlobSidecar(any()))
        .thenAnswer(
            args -> {
              final SlotAndBlockRootAndBlobIndex argKey = args.getArgument(0);
              if (argKey.equals(key)) {
                return SafeFuture.completedFuture(Optional.of(result));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });
  }

  private void setupGetSlotForBlockRoot(final Bytes32 blockRoot, final UInt64 slot) {
    when(recentChainData.getSlotForBlockRoot(any()))
        .thenAnswer(
            args -> {
              final Bytes32 argRoot = args.getArgument(0);
              if (argRoot.equals(blockRoot)) {
                return Optional.of(slot);
              } else {
                return Optional.empty();
              }
            });
  }

  private Bytes32 setupGetBlockByBlockRoot(final UInt64 slot) {
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock(slot);
    when(historicalChainData.getBlockByBlockRoot(any()))
        .thenAnswer(
            args -> {
              final Bytes32 argRoot = args.getArgument(0);
              if (argRoot.equals(signedBeaconBlock.getRoot())) {
                return SafeFuture.completedFuture(Optional.of(signedBeaconBlock));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });
    return signedBeaconBlock.getRoot();
  }
}
