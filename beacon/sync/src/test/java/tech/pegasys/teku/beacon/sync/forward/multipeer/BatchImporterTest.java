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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.forward.multipeer.BatchImporter.BatchImportResult;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobsSidecarManager;
import tech.pegasys.teku.statetransition.block.BlockImporter;

class BatchImporterTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlockImporter blockImporter = mock(BlockImporter.class);
  private final BlobsSidecarManager blobsSidecarManager = mock(BlobsSidecarManager.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final Batch batch = mock(Batch.class);
  final SyncSource syncSource = mock(SyncSource.class);

  private final BatchImporter importer =
      new BatchImporter(blockImporter, blobsSidecarManager, asyncRunner);

  @BeforeEach
  public void setup() {
    when(batch.getSource()).thenReturn(Optional.of(syncSource));
    when(batch.getBlobSidecarsByBlockRoot()).thenReturn(Map.of());
    when(blobsSidecarManager.importBlobSidecar(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void shouldImportBlocksInOrder() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(3);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult3 = new SafeFuture<>();
    final List<SignedBeaconBlock> blocks = new ArrayList<>(List.of(block1, block2, block3));
    when(batch.getBlocks()).thenReturn(blocks);
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);
    when(blockImporter.importBlock(block3)).thenReturn(importResult3);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);
    verifyNoInteractions(blobsSidecarManager);

    // We should have copied the blocks and blob sidecars to avoid accessing the Batch data from
    // other threads
    verify(batch).getBlocks();
    verify(batch).getBlobSidecarsByBlockRoot();
    verify(batch).getSource();

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();
    blockImportedSuccessfully(block2, importResult2);
    assertThat(result).isNotDone();
    blockImportedSuccessfully(block3, importResult3);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORTED_ALL_BLOCKS);

    // And check we didn't touch the batch from a different thread
    verifyNoMoreInteractions(batch);
  }

  @Test
  void shouldImportBlobSidecarsAndBlocksInOrder() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    final List<BlobSidecar> blobSidecars1 = dataStructureUtil.randomBlobSidecarsForBlock(block1);
    final List<BlobSidecar> blobSidecars2 = dataStructureUtil.randomBlobSidecarsForBlock(block2);

    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();

    final List<SignedBeaconBlock> blocks = new ArrayList<>(List.of(block1, block2));
    final Map<Bytes32, List<BlobSidecar>> blobSidecars =
        Map.of(block1.getRoot(), blobSidecars1, block2.getRoot(), blobSidecars2);

    when(batch.getBlocks()).thenReturn(blocks);
    when(batch.getBlobSidecarsByBlockRoot()).thenReturn(blobSidecars);

    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);
    verifyNoInteractions(blobsSidecarManager);

    // We should have copied the blocks and blob sidecars  to avoid accessing the Batch data from
    // other threads
    verify(batch).getBlocks();
    verify(batch).getBlobSidecarsByBlockRoot();
    verify(batch).getSource();

    asyncRunner.executeQueuedActions();

    blobSidecarsImportedSuccessfully(blobSidecars1);
    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();
    blobSidecarsImportedSuccessfully(blobSidecars2);
    blockImportedSuccessfully(block2, importResult2);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORTED_ALL_BLOCKS);

    // And check we didn't touch the batch from a different thread
    verifyNoMoreInteractions(batch);
  }

  @Test
  void shouldStopImportingAfterFailure() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(3);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult3 = new SafeFuture<>();
    when(batch.getBlocks()).thenReturn(List.of(block1, block2, block3));
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);
    when(blockImporter.importBlock(block3)).thenReturn(importResult3);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();

    ignoreFuture(verify(blockImporter).importBlock(block2));
    verifyNoMoreInteractions(blockImporter);

    importResult2.complete(
        BlockImportResult.failedStateTransition(new Exception("Naughty block!")));
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORT_FAILED);
    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  void shouldDisconnectPeersForWeakSubjectivityViolation() {
    when(syncSource.disconnectCleanly(any())).thenReturn(SafeFuture.COMPLETE);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    when(batch.getBlocks()).thenReturn(List.of(block1, block2));
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();

    ignoreFuture(verify(blockImporter).importBlock(block2));
    verifyNoMoreInteractions(blockImporter);

    // Import bad block
    importResult2.complete(BlockImportResult.FAILED_WEAK_SUBJECTIVITY_CHECKS);
    assertThat(result).isCompletedWithValue(BatchImportResult.IMPORT_FAILED);
    verify(batch).getSource();
    verify(syncSource).disconnectCleanly(DisconnectReason.REMOTE_FAULT);

    verifyNoMoreInteractions(blockImporter);
  }

  @Test
  void shouldNotDisconnectPeersWhenServiceOffline() {
    when(syncSource.disconnectCleanly(any())).thenReturn(SafeFuture.COMPLETE);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final SafeFuture<BlockImportResult> importResult1 = new SafeFuture<>();
    final SafeFuture<BlockImportResult> importResult2 = new SafeFuture<>();
    when(batch.getBlocks()).thenReturn(List.of(block1, block2));
    when(blockImporter.importBlock(block1)).thenReturn(importResult1);
    when(blockImporter.importBlock(block2)).thenReturn(importResult2);

    final SafeFuture<BatchImportResult> result = importer.importBatch(batch);

    // Should not be started on the calling thread
    verifyNoInteractions(blockImporter);

    asyncRunner.executeQueuedActions();

    blockImportedSuccessfully(block1, importResult1);
    assertThat(result).isNotDone();

    ignoreFuture(verify(blockImporter).importBlock(block2));
    verifyNoMoreInteractions(blockImporter);

    // Import failed due to service being offline
    importResult2.complete(BlockImportResult.failedExecutionPayloadExecution(new Error()));
    assertThat(result).isCompletedWithValue(BatchImportResult.SERVICE_OFFLINE);
    verify(batch).getSource();
    verify(syncSource, never()).disconnectCleanly(any());

    verifyNoMoreInteractions(blockImporter);
  }

  private void blobSidecarsImportedSuccessfully(final List<BlobSidecar> blobSidecars) {
    blobSidecars.forEach(
        blobSidecar -> ignoreFuture(verify(blobsSidecarManager).importBlobSidecar(blobSidecar)));
    verifyNoMoreInteractions(blobsSidecarManager);
  }

  private void blockImportedSuccessfully(
      final SignedBeaconBlock block, final SafeFuture<BlockImportResult> importResult) {
    ignoreFuture(verify(blockImporter).importBlock(block));
    verifyNoMoreInteractions(blockImporter);
    importResult.complete(BlockImportResult.successful(block));
  }
}
