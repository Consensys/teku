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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockAndBlobsSidecarTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlockTaskFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RespondingEth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker;
import tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarAndValidationResult;
import tech.pegasys.teku.statetransition.blobs.BlobsSidecarManager;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class HistoricalBatchFetcherTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
  private final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
  private final FetchBlockTaskFactory fetchBlockTaskFactory = mock(FetchBlockTaskFactory.class);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Eth2Peer> eth2Network = mock(P2PNetwork.class);

  private final BlobsSidecarAvailabilityChecker blobsSidecarAvailabilityChecker =
      mock(BlobsSidecarAvailabilityChecker.class);

  private final BlobsSidecarManager blobsSidecarManager = mock(BlobsSidecarManager.class);
  private ChainBuilder forkBuilder;

  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Collection<SignedBeaconBlock>> blockCaptor =
      ArgumentCaptor.forClass(Collection.class);

  private CombinedChainDataClient chainDataClient;

  private final int maxRequests = 5;
  private List<SignedBeaconBlock> blockBatch;
  private List<BlobsSidecar> blobsSidecarBatch;
  private SignedBeaconBlock firstBlockInBatch;
  private SignedBeaconBlock lastBlockInBatch;
  private HistoricalBatchFetcher fetcher;
  private RespondingEth2Peer peer;

  @BeforeEach
  public void setup() {
    storageSystem.chainUpdater().initializeGenesis();

    // Set up main chain and fork chain
    chainBuilder.generateGenesis();
    forkBuilder = chainBuilder.fork();
    // blocks up to slot 19 will have empty sidecars
    chainBuilder.generateBlocksUpToSlot(20);
    // Fork skips one block then creates a chain of the same size
    forkBuilder.generateBlockAtSlot(2);
    forkBuilder.generateBlocksUpToSlot(20);

    blockBatch =
        chainBuilder
            .streamBlocksAndStates(10, 20)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    blobsSidecarBatch = chainBuilder.streamBlobsSidecars(10, 20).collect(Collectors.toList());
    lastBlockInBatch = chainBuilder.getLatestBlockAndState().getBlock();
    firstBlockInBatch = blockBatch.get(0);
    final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
    final RecentChainData recentChainData = storageSystem.recentChainData();
    chainDataClient = new CombinedChainDataClient(recentChainData, historicalChainData, spec);

    peer = RespondingEth2Peer.create(spec, chainBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            maxRequests,
            fetchBlockTaskFactory,
            blobsSidecarManager);

    mockBlockAndBlobsSidecarsRelatedMethods(false);

    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(true));
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
    // finalizedBlobsSidecar assertion is done in mockBlockAndBlobsSidecarsRelatedMethods()
    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), anyMap());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
    // verify no availability checker is triggered
    verify(blobsSidecarManager, never()).createAvailabilityChecker(any());
  }

  @Test
  public void run_returnAllBlocksAndBlobsSidecarsOnFirstRequest() {
    mockBlockAndBlobsSidecarsRelatedMethods(true);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(2);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
  }

  @Test
  public void run_failingOnBlobsSidecarValidation() {
    mockBlockAndBlobsSidecarsRelatedMethods(true);

    // return INVALID result for the blobs sidecar in slot 18
    doAnswer(
            i -> {
              final Optional<BlobsSidecar> blobsSidecar = i.getArgument(0);
              return SafeFuture.completedFuture(
                  BlobsSidecarAndValidationResult.invalidResult(
                      blobsSidecar.orElseThrow(), new IllegalStateException("oopsy")));
            })
        .when(blobsSidecarAvailabilityChecker)
        .validate(argThat(blobsSidecarForSlot(UInt64.valueOf(18))));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(2);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    SafeFutureAssert.assertThatSafeFuture(future)
        .isCompletedExceptionallyWithMessage(
            String.format(
                "Blobs sidecar for slot 18 received from peer %s is not valid", peer.getId()));

    verifyNoInteractions(storageUpdateChannel);
  }

  @Test
  public void run_failingOnBlobsSidecarNotReceived() {
    mockBlockAndBlobsSidecarsRelatedMethods(true);

    // return NOT_AVAILABLE for the not received blobs sidecars
    doAnswer(__ -> SafeFuture.completedFuture(BlobsSidecarAndValidationResult.NOT_AVAILABLE))
        .when(blobsSidecarAvailabilityChecker)
        .validate(argThat(Optional::isEmpty));

    // discard sidecar for slot 18
    chainBuilder.discardBlobsSidecar(UInt64.valueOf(18));

    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    peer.completePendingRequests();

    SafeFutureAssert.assertThatSafeFuture(future)
        .isCompletedExceptionallyWithMessage(
            "Blobs sidecar for slot 18 was not received from peer " + peer.getId());

    verifyNoInteractions(storageUpdateChannel);
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

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), anyMap());
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
            peer,
            latestBlock.getSlot(),
            latestBlock.getRoot(),
            UInt64.valueOf(batchSize),
            maxRequests,
            fetchBlockTaskFactory,
            blobsSidecarManager);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(genesis.getBlock());

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), anyMap());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(targetBatch);
  }

  @Test
  public void run_requestBatchForRangeOfEmptyBlocks() {
    final int batchSize = 10;
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            peer,
            // Slot & batch size define an empty set of blocks
            lastBlockInBatch.getSlot().plus(batchSize * 2),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(batchSize),
            maxRequests,
            fetchBlockTaskFactory,
            blobsSidecarManager);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    // Request by range will return nothing
    for (int i = 0; i < maxRequests; i++) {
      assertThat(peer.getOutstandingRequests()).isEqualTo(1);
      peer.completePendingRequests();
    }
    // Request by hash should return the final block
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(lastBlockInBatch);

    verify(storageUpdateChannel).onFinalizedBlocks(blockCaptor.capture(), anyMap());
    assertThat(blockCaptor.getValue()).containsExactly(lastBlockInBatch);
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
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            maxRequests,
            fetchBlockTaskFactory,
            blobsSidecarManager);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get)
        .hasCauseInstanceOf(InvalidResponseException.class)
        .hasMessageContaining("Received invalid blocks from a different chain");
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any());
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
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any());
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
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any());
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
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any());
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
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any());
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
    verify(storageUpdateChannel, never()).onFinalizedBlocks(any(), any());
  }

  private void mockBlockAndBlobsSidecarsRelatedMethods(final boolean deneb) {
    mockBlockAndBlobsSidecarsRelatedMethods(deneb, Optional.empty());
  }

  @SuppressWarnings("unchecked")
  private void mockBlockAndBlobsSidecarsRelatedMethods(
      final boolean deneb,
      final Optional<Consumer<Map<UInt64, BlobsSidecar>>> customFinalizedBlobsSidecarsAssertion) {
    when(blobsSidecarManager.isStorageOfBlobsSidecarRequired(any())).thenReturn(deneb);
    if (deneb) {
      when(fetchBlockTaskFactory.create(any(), any()))
          .thenAnswer(i -> new FetchBlockAndBlobsSidecarTask(eth2Network, i.getArgument(1)));
      when(blobsSidecarManager.createAvailabilityChecker(any()))
          .thenReturn(blobsSidecarAvailabilityChecker);
      // make all blobs sidecars valid
      doAnswer(
              i -> {
                final Optional<BlobsSidecar> blobsSidecar = i.getArgument(0);
                return SafeFuture.completedFuture(
                    BlobsSidecarAndValidationResult.validResult(blobsSidecar.orElseThrow()));
              })
          .when(blobsSidecarAvailabilityChecker)
          .validate(any());
      doAnswer(
              i -> {
                final Map<UInt64, BlobsSidecar> finalizedBlobsSidecars = i.getArgument(1);
                if (customFinalizedBlobsSidecarsAssertion.isPresent()) {
                  customFinalizedBlobsSidecarsAssertion.get().accept(finalizedBlobsSidecars);
                } else {
                  assertThat(finalizedBlobsSidecars)
                      .containsValues(blobsSidecarBatch.toArray(BlobsSidecar[]::new));
                }
                return SafeFuture.COMPLETE;
              })
          .when(storageUpdateChannel)
          .onFinalizedBlocks(any(), any());
    } else {
      when(fetchBlockTaskFactory.create(any(), any()))
          .thenAnswer(i -> new FetchBlockTask(eth2Network, i.getArgument(1)));
      when(blobsSidecarManager.createAvailabilityChecker(any()))
          .thenReturn(BlobsSidecarAvailabilityChecker.NOT_REQUIRED);
      doAnswer(
              i -> {
                assertThat((Map<UInt64, BlobsSidecar>) i.getArgument(1)).isEmpty();
                return SafeFuture.COMPLETE;
              })
          .when(storageUpdateChannel)
          .onFinalizedBlocks(any(), any());
    }
  }

  private ArgumentMatcher<Optional<BlobsSidecar>> blobsSidecarForSlot(final UInt64 slot) {
    return blobsSidecar ->
        blobsSidecar
            .map(BlobsSidecar::getBeaconBlockSlot)
            .map(sidecarSlot -> sidecarSlot.equals(slot))
            .orElse(false);
  }
}
