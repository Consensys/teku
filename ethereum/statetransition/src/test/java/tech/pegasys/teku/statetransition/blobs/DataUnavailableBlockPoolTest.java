/*
 * Copyright ConsenSys Software Inc., 2023
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.BlockManager;

public class DataUnavailableBlockPoolTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final BlockManager blockManager = mock(BlockManager.class);
  private final BlobSidecarPool blobSidecarPool = mock(BlobSidecarPool.class);

  private final DataUnavailableBlockPool dataUnavailableBlockPool =
      new DataUnavailableBlockPool(blockManager, blobSidecarPool, asyncRunner);

  private final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock();
  private final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock();

  private final BlockBlobSidecarsTracker block1Tracker = mock(BlockBlobSidecarsTracker.class);
  private final BlockBlobSidecarsTracker block2Tracker = mock(BlockBlobSidecarsTracker.class);

  private final SafeFuture<BlockImportResult> block1ImportResult = new SafeFuture<>();
  private final SafeFuture<BlockImportResult> block2ImportResult = new SafeFuture<>();

  @BeforeEach
  void setUp() {
    dataUnavailableBlockPool.onSyncingStatusChanged(true);
    when(blobSidecarPool.getBlockBlobSidecarsTracker(block1))
        .thenReturn(Optional.of(block1Tracker));
    when(blobSidecarPool.getBlockBlobSidecarsTracker(block2))
        .thenReturn(Optional.of(block2Tracker));

    when(blockManager.importBlock(block1)).thenReturn(block1ImportResult);
    when(blockManager.importBlock(block2)).thenReturn(block2ImportResult);
  }

  @Test
  void shouldNotImportWhenNotInSync() {
    dataUnavailableBlockPool.onSyncingStatusChanged(false);

    dataUnavailableBlockPool.addDataUnavailableBlock(block1);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    verifyNoInteractions(blobSidecarPool);
    verifyNoInteractions(blockManager);
  }

  @Test
  void shouldImportInSequenceWithoutDelay() {
    when(block1Tracker.isCompleted()).thenReturn(true);
    when(block2Tracker.isCompleted()).thenReturn(true);

    dataUnavailableBlockPool.addDataUnavailableBlock(block1);
    dataUnavailableBlockPool.addDataUnavailableBlock(block2);

    verify(blockManager).importBlock(block1);

    // should wait block1 to finish import
    verify(blockManager, never()).importBlock(block2);

    // block import finishes
    block1ImportResult.complete(BlockImportResult.successful(block1));

    // import immediately the second
    verify(blockManager).importBlock(block2);
    block2ImportResult.complete(BlockImportResult.successful(block2));

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldImportFirstCompletedAndDelayForNonCompleted() {
    // block1 is not completed, block2 is
    when(block1Tracker.isCompleted()).thenReturn(false);
    when(block2Tracker.isCompleted()).thenReturn(true);

    dataUnavailableBlockPool.addDataUnavailableBlock(block1);
    dataUnavailableBlockPool.addDataUnavailableBlock(block2);

    verify(blockManager, never()).importBlock(block1);
    verify(blockManager, never()).importBlock(block2);

    // there is a queued task because we added block1 first.
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    // let's run the delayed task
    asyncRunner.executeQueuedActions();

    // block2 is selected for import
    verify(blockManager, never()).importBlock(block1);
    verify(blockManager).importBlock(block2);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    // block2 import finishes
    block2ImportResult.complete(BlockImportResult.successful(block2));

    // we still have a queued task, because we have block1 still incomplete
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    verify(blockManager, never()).importBlock(block1);

    // lets complete block1 and run the delayed task
    when(block1Tracker.isCompleted()).thenReturn(true);
    asyncRunner.executeQueuedActions();

    // block1 is imported
    verify(blockManager).importBlock(block1);
    block1ImportResult.complete(BlockImportResult.successful(block1));

    // no other delayed task
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    verifyNoMoreInteractions(blockManager);
  }

  @Test
  void shouldCancelIfStartSyncing() {
    when(block1Tracker.isCompleted()).thenReturn(false);
    when(block2Tracker.isCompleted()).thenReturn(true);

    dataUnavailableBlockPool.addDataUnavailableBlock(block1);
    dataUnavailableBlockPool.addDataUnavailableBlock(block2);

    verify(blockManager, never()).importBlock(block1);
    verify(blockManager, never()).importBlock(block2);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    dataUnavailableBlockPool.onSyncingStatusChanged(false);

    asyncRunner.executeQueuedActions();

    verifyNoInteractions(blockManager);
  }
}
