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

package tech.pegasys.teku.beacon.sync.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.RespondingEth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAvailabilityChecker;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.EarliestAvailableBlockSlot;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class HistoricalBatchFetcherTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
  private final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
  private ChainBuilder forkBuilder;

  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private final BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker =
      mock(BlobSidecarsAvailabilityChecker.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Collection<SignedBeaconBlock>> blockCaptor =
      ArgumentCaptor.forClass(Collection.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Map<SlotAndBlockRoot, List<BlobSidecar>>> blobSidecarCaptor =
      ArgumentCaptor.forClass(Map.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Optional<UInt64>> earliestBlobSidecarSlotCaptor =
      ArgumentCaptor.forClass(Optional.class);

  private CombinedChainDataClient chainDataClient;

  private final int maxRequests = 5;
  private List<SignedBeaconBlock> blockBatch;
  private Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBatch;
  private SignedBeaconBlock firstBlockInBatch;
  private SignedBeaconBlock lastBlockInBatch;
  private HistoricalBatchFetcher fetcher;
  private RespondingEth2Peer peer;

  @BeforeEach
  public void setup() {
    storageSystem.chainUpdater().initializeGenesis();
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(false);
    when(storageUpdateChannel.onFinalizedBlocks(any(), any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    // Set up main chain and fork chain
    chainBuilder.generateGenesis();
    forkBuilder = chainBuilder.fork();
    chainBuilder.generateBlocksUpToSlot(17);
    // 18, 19 will be with BlobSidecars
    chainBuilder.generateBlockAtSlot(
        18, ChainBuilder.BlockOptions.create().setGenerateRandomBlobs(true));
    chainBuilder.generateBlockAtSlot(
        19, ChainBuilder.BlockOptions.create().setGenerateRandomBlobs(true));
    chainBuilder.generateBlockAtSlot(20);
    // Fork skips one block then creates a chain of the same size
    forkBuilder.generateBlockAtSlot(2);
    forkBuilder.generateBlocksUpToSlot(20);

    blockBatch =
        chainBuilder
            .streamBlocksAndStates(10, 20)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    lastBlockInBatch = chainBuilder.getLatestBlockAndState().getBlock();
    firstBlockInBatch = blockBatch.get(0);
    blobSidecarsBatch =
        chainBuilder
            .streamBlobSidecars(10, 20)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
    final RecentChainData recentChainData = storageSystem.recentChainData();
    chainDataClient =
        new CombinedChainDataClient(
            recentChainData,
            historicalChainData,
            spec,
            new EarliestAvailableBlockSlot(historicalChainData, new SystemTimeProvider(), 0));

    peer = RespondingEth2Peer.create(spec, chainBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            maxRequests);

    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(blobSidecarManager.createAvailabilityChecker(any()))
        .thenReturn(blobSidecarsAvailabilityChecker);
    when(blobSidecarsAvailabilityChecker.validateImmediately(anyList()))
        .thenAnswer(i -> BlobSidecarsAndValidationResult.validResult(i.getArgument(0)));
  }

  @Test
  public void run_failsWhenInvalidSignatureFound() {
    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(false));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();
    peer.completePendingRequests();
    assertThat(future).isCompletedExceptionally();
  }

  @Test
  public void run_returnAllBlocksOnFirstRequest() {
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel)
        .onFinalizedBlocks(
            blockCaptor.capture(),
            blobSidecarCaptor.capture(),
            earliestBlobSidecarSlotCaptor.capture());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
    assertThat(blobSidecarCaptor.getValue()).isEmpty();
    assertThat(earliestBlobSidecarSlotCaptor.getValue()).isEmpty();
  }

  @Test
  public void run_returnAllBlocksAndBlobSidecarsOnFirstRequest() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(2);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel)
        .onFinalizedBlocks(
            blockCaptor.capture(),
            blobSidecarCaptor.capture(),
            earliestBlobSidecarSlotCaptor.capture());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
    assertThat(blobSidecarCaptor.getValue()).isEqualTo(blobSidecarsBatch);
    assertThat(earliestBlobSidecarSlotCaptor.getValue()).contains(blockBatch.get(0).getSlot());
  }

  @Test
  public void run_failsOnBlobSidecarsValidationFailure() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);
    when(blobSidecarsAvailabilityChecker.validateImmediately(anyList()))
        .thenAnswer(
            i ->
                BlobSidecarsAndValidationResult.invalidResult(
                    i.getArgument(0), new IllegalStateException("oopsy")));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();
    peer.completePendingRequests();

    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withMessageMatching(
            "java.lang.IllegalArgumentException: Blob sidecars validation for block .* failed: INVALID \\(oopsy\\)");
  }

  @Test
  public void run_returnAllBlocksAcrossMultipleRequests() {
    // Limit the number of blocks to return
    final int limit = (int) Math.ceil(blockBatch.size() / 2.0);
    peer.setBlockRequestFilter(
        allBlocks -> allBlocks.stream().limit(limit).collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), any(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
  }

  @Test
  public void run_requestBatchWithSkippedSlots() {
    final ChainBuilder chain = ChainBuilder.create(spec);
    final int batchSize = 20;
    final SignedBlockAndState genesis = chain.generateGenesis();
    chain.generateBlockAtSlot(5);
    chain.generateBlocksUpToSlot(10);
    chain.generateBlockAtSlot(15);
    chain.generateBlocksUpToSlot(30);

    final SignedBeaconBlock latestBlock = chain.getBlockAtSlot(19);
    final List<SignedBeaconBlock> targetBatch =
        chain
            .streamBlocksAndStates(0, latestBlock.getSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    peer = RespondingEth2Peer.create(spec, chain);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            latestBlock.getSlot(),
            latestBlock.getRoot(),
            UInt64.valueOf(batchSize),
            maxRequests);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(genesis.getBlock());

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), any(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(targetBatch);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void run_requestBatchForRangeOfEmptyBlocks(final boolean blobSidecarsRequired) {
    if (blobSidecarsRequired) {
      when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);
    }
    final int batchSize = 10;
    // Slot & batch size define an empty set of blocks
    final UInt64 maxSlot = lastBlockInBatch.getSlot().plus(batchSize * 2);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            maxSlot,
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(batchSize),
            maxRequests);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    // Request(s) by range will return nothing
    for (int i = 0; i < maxRequests; i++) {
      final int outstandingRequests = blobSidecarsRequired ? 2 : 1;
      assertThat(peer.getOutstandingRequests()).isEqualTo(outstandingRequests);
      peer.completePendingRequests();
    }
    // Request by root should return the final block
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(lastBlockInBatch);

    verify(storageUpdateChannel)
        .onFinalizedBlocks(
            blockCaptor.capture(),
            blobSidecarCaptor.capture(),
            earliestBlobSidecarSlotCaptor.capture());
    assertThat(blockCaptor.getValue()).containsExactly(lastBlockInBatch);
    Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars = blobSidecarCaptor.getValue();
    if (blobSidecarsRequired) {
      assertThat(blobSidecars).isEmpty();
      // start slot is in availability window, it's the earliest known blob sidecar slot
      assertThat(earliestBlobSidecarSlotCaptor.getValue()).contains(UInt64.valueOf(31));
    } else {
      assertThat(blobSidecars).isEmpty();
      assertThat(earliestBlobSidecarSlotCaptor.getValue()).isEmpty();
    }
  }

  @Test
  public void run_peerReturnBlockFromADifferentChain() {
    peer = RespondingEth2Peer.create(spec, forkBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            maxRequests);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Received invalid blocks from a different chain");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any());
  }

  @Test
  public void run_throttleExcessivelyAcrossMultipleRequest() {
    // Only return one block at a time, and fail to return the last block altogether
    final int limit = 1;
    peer.setBlockRequestFilter(
        allBlocks ->
            allBlocks.stream()
                .filter(b -> !b.equals(lastBlockInBatch))
                .limit(limit)
                .collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    // We should exhaust blocks-by-range requests and then fail
    for (int i = 0; i < maxRequests; i++) {
      assertThat(peer.getOutstandingRequests()).isEqualTo(1);
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Failed to deliver full batch");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any());
  }

  @Test
  public void run_peerReturnsError() {
    // Only return one block at a time, and fail to return the last block altogether
    final RuntimeException error = new RuntimeException("oops");
    peer.setBlockRequestFilter(
        allBlocks -> {
          throw error;
        });

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    // First request should return an error
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCause(error);
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any());
  }

  @Test
  public void run_peerWithholdsFinalBlock() {
    // Withhold final block
    peer.setBlockRequestFilter(
        allBlocks ->
            allBlocks.stream()
                .filter(b -> !b.equals(lastBlockInBatch))
                .collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    // We should exhaust blocks-by-range requests and then fail
    for (int i = 0; i < maxRequests; i++) {
      assertThat(peer.getOutstandingRequests()).isEqualTo(1);
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Failed to deliver full batch");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any());
  }

  @Test
  public void run_unresponsivePeer() {
    // Return nothing
    peer.setBlockRequestFilter(allBlocks -> Collections.emptyList());

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    // We should exhaust blocks-by-range requests
    for (int i = 0; i < maxRequests; i++) {
      assertThat(peer.getOutstandingRequests()).isEqualTo(1);
      peer.completePendingRequests();
    }
    // Then a final block-by-hash request before failing
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Failed to deliver full batch");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any());
  }

  @Test
  public void run_peerReturnsInvalidResponsesWithGaps() {
    // Skip blocks on the boundary between requests
    final int limit = (int) Math.ceil(blockBatch.size() / 2.0);
    peer.setBlockRequestFilter(
        allBlocks -> allBlocks.stream().limit(limit).skip(1).collect(Collectors.toList()));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    // We should fail on the second request
    for (int i = 0; i < 2; i++) {
      assertThat(peer.getOutstandingRequests()).isEqualTo(1);
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Expected first block to descend from last received block");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any(), any());
  }
}
