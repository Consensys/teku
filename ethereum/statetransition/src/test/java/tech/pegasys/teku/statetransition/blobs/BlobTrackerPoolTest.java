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

package tech.pegasys.teku.statetransition.blobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobTrackerPoolTest {
  final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPoolImpl.class);
  final DataAvailabilitySampler prunedDataAvailabilitySampler = mock(DataAvailabilitySampler.class);
  final RecentChainData recentChainData = mock(RecentChainData.class);
  final Spec spec =
      TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
          UInt64.ZERO, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3));
  final BlobTrackerPool blobTrackerPool =
      new BlobTrackerPool(
          blockBlobSidecarsTrackersPool,
          () -> prunedDataAvailabilitySampler,
          recentChainData,
          spec);
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final UInt64 denebSlot = UInt64.ONE.times(spec.slotsPerEpoch(UInt64.ZERO));
  final UInt64 fuluSlot = UInt64.valueOf(3).times(spec.slotsPerEpoch(UInt64.ZERO));

  @BeforeEach
  public void setup() {
    when(recentChainData.containsBlock(any())).thenReturn(false);
  }

  @Test
  public void onNewBlock_shouldIgnorePreDenebBlocks() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    blobTrackerPool.onNewBlock(block, Optional.empty());
    verify(recentChainData).containsBlock(block.getRoot());
    verifyNoInteractions(blockBlobSidecarsTrackersPool);
    verifyNoInteractions(prunedDataAvailabilitySampler);
  }

  @Test
  public void onNewBlock_shouldIgnoreImportedBlocks() {
    when(recentChainData.containsBlock(any())).thenReturn(true);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    blobTrackerPool.onNewBlock(block, Optional.empty());
    verify(recentChainData).containsBlock(block.getRoot());
    verifyNoInteractions(blockBlobSidecarsTrackersPool);
    verifyNoInteractions(prunedDataAvailabilitySampler);
  }

  @Test
  public void onNewBlock_shouldHandleDenebBlocks() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(denebSlot);
    blobTrackerPool.onNewBlock(block, Optional.of(RemoteOrigin.GOSSIP));
    verify(recentChainData).containsBlock(block.getRoot());
    verify(blockBlobSidecarsTrackersPool).onNewBlock(block, Optional.of(RemoteOrigin.GOSSIP));
    verifyNoInteractions(prunedDataAvailabilitySampler);
  }

  @Test
  public void onNewBlock_shouldHandleFuluBlocks() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(fuluSlot);
    blobTrackerPool.onNewBlock(block, Optional.of(RemoteOrigin.GOSSIP));
    verify(recentChainData).containsBlock(block.getRoot());
    verifyNoInteractions(blockBlobSidecarsTrackersPool);
    verify(prunedDataAvailabilitySampler).onNewBlock(block, Optional.of(RemoteOrigin.GOSSIP));
  }
}
