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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;

/** Note: Most tests should be added to the integration-test directory */
class CombinedChainDataClientTest {
  private final Spec spec = TestSpecFactory.createMinimalEip4844();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private final CombinedChainDataClient client =
      new CombinedChainDataClient(recentChainData, historicalChainData, spec);
  private final ChainHead chainHead = mock(ChainHead.class);

  final List<SignedBeaconBlock> nonCanonicalBlocks = new ArrayList<>();
  final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final SignedBeaconBlock secondBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final BlobsSidecar sidecar = dataStructureUtil.randomBlobsSidecar();

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
  void getsEarliestAvailableBlobsSidecarEpoch() {
    when(historicalChainData.getEarliestAvailableBlobsSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(UInt64.ONE)));

    final Optional<UInt64> result =
        SafeFutureAssert.safeJoin(client.getEarliestAvailableBlobsSidecarEpoch());

    assertThat(result).hasValue(UInt64.ZERO);
  }

  @Test
  void getsBlobsSidecarBySlotAndBlockRoot() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(historicalChainData.getBlobsSidecar(new SlotAndBlockRoot(UInt64.ONE, blockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(sidecar)));

    final Optional<BlobsSidecar> result =
        SafeFutureAssert.safeJoin(client.getBlobsSidecarBySlotAndBlockRoot(UInt64.ONE, blockRoot));

    assertThat(result).hasValue(sidecar);
  }
}
