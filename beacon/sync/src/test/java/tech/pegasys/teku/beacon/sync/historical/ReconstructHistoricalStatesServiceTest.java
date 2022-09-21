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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class ReconstructHistoricalStatesServiceTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private ReconstructHistoricalStatesService service;
  private final StatusLogger statusLogger = mock(StatusLogger.class);

  @BeforeEach
  void setup() {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);

    when(storageUpdateChannel.onFinalizedState(any(), any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void shouldCompleteExceptionallyWithEmptyResource() {
    createService(Optional.empty());

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompletedExceptionally();
    assertThatSafeFuture(res).isCompletedExceptionallyWith(IllegalStateException.class);
    assertThatSafeFuture(res)
        .isCompletedExceptionallyWithMessage("Genesis state resource not provided");

    verify(chainDataClient, never()).getInitialAnchor();
    verify(storageUpdateChannel, never()).onFinalizedState(any(), any());
  }

  @Test
  public void shouldCompleteExceptionallyWithFailedLoadState() {
    createService(Optional.of("invalid resource"));

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompletedExceptionally();
    assertThatSafeFuture(res).isCompletedExceptionallyWith(InvalidConfigurationException.class);
    assertThatSafeFuture(res)
        .isCompletedExceptionallyWithMessage(
            "Failed to load initial state from invalid resource: Not found");

    verify(chainDataClient, never()).getInitialAnchor();
    verify(storageUpdateChannel, never()).onFinalizedState(any(), any());
  }

  @Test
  public void shouldCompleteSkipWithEmptyCheckpoint(@TempDir final Path tempDir)
      throws IOException {
    createService(createGenesisStateResource(tempDir));
    when(chainDataClient.getInitialAnchor())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
    verify(chainDataClient, times(1)).getInitialAnchor();
    verify(storageUpdateChannel, never()).onFinalizedState(any(), any());
  }

  @Test
  public void shouldRegenerateStates(@TempDir final Path tempDir) throws IOException {
    when(chainDataClient.getLatestStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    final Checkpoint initialAnchor = getInitialAnchor();
    setUpService(tempDir, initialAnchor);

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
    verify(chainDataClient, times(1)).getInitialAnchor();
    verify(storageUpdateChannel, times(initialAnchor.getEpochStartSlot(spec).minus(1).intValue()))
        .onFinalizedState(any(), any());
  }

  @Test
  void shouldRegenerateStatesWithEmptySlots(@TempDir final Path tempDir) throws IOException {
    when(chainDataClient.getLatestStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    chainBuilder.generateBlockAtSlot(12);
    chainBuilder.generateBlocksUpToSlot(18);
    final Checkpoint initialAnchor = getInitialAnchor();
    setUpService(tempDir, initialAnchor);

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
    verify(chainDataClient, times(1)).getInitialAnchor();
    verify(storageUpdateChannel, times(initialAnchor.getEpochStartSlot(spec).minus(2).intValue()))
        .onFinalizedState(any(), any());
  }

  @Test
  void shouldLogFailServiceProcess(@TempDir final Path tempDir) throws IOException {
    when(storageUpdateChannel.onFinalizedState(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException()));
    when(chainDataClient.getLatestStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    setUpService(tempDir, getInitialAnchor());

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
    verify(chainDataClient, times(1)).getInitialAnchor();
    verify(storageUpdateChannel, times(1)).onFinalizedState(any(), any());
    verify(statusLogger, times(1)).reconstructHistoricalStatesServiceFailedProcess(any());
  }

  @Test
  void shouldHandleShutdown(@TempDir final Path tempDir) throws IOException {
    when(storageUpdateChannel.onFinalizedState(any(), any()))
        .thenThrow(new RejectedExecutionException());
    when(chainDataClient.getLatestStateAtSlot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    setUpService(tempDir, getInitialAnchor());

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
    verify(chainDataClient, times(1)).getInitialAnchor();
    verify(storageUpdateChannel, times(1)).onFinalizedState(any(), any());
    verify(statusLogger, never()).reconstructHistoricalStatesServiceFailedProcess(any());
  }

  @Test
  void shouldHandleStartWithPartialStorage(@TempDir final Path tempDir) throws IOException {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    final ChainUpdater updater = storageSystem.chainUpdater();
    updater.initializeGenesis();
    final SignedBlockAndState blockAndState = updater.advanceChain(5);
    updater.updateBestBlock(blockAndState);

    when(chainDataClient.getLatestStateAtSlot(any()))
        .thenAnswer(
            invocation ->
                storageSystem
                    .combinedChainDataClient()
                    .getLatestStateAtSlot(invocation.getArgument(0)));
    final Checkpoint initialAnchor = getInitialAnchor();
    setUpService(tempDir, initialAnchor);

    final SafeFuture<?> res = service.start();
    assertThat(res).isCompleted();
    verify(chainDataClient, times(1)).getInitialAnchor();
    verify(
            storageUpdateChannel, // todo check incorrect number of invocations
            times(initialAnchor.getEpochStartSlot(spec).minus(1).intValue()))
        .onFinalizedState(any(), any());
  }

  private Checkpoint getInitialAnchor() {
    return chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
  }

  private void setUpService(final Path tempDir, final Checkpoint initialAnchor) throws IOException {
    createService(createGenesisStateResource(tempDir));
    when(chainDataClient.getInitialAnchor())
        .thenReturn(SafeFuture.completedFuture(Optional.of(initialAnchor)));
    when(chainDataClient.getBlockAtSlotExact(any()))
        .thenAnswer(
            invocation -> {
              final UInt64 slot = invocation.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.ofNullable(chainBuilder.getBlockAtSlot(slot)));
            });
  }

  private Optional<String> createGenesisStateResource(final Path tempDir) throws IOException {
    final BeaconState state = chainBuilder.getGenesis().getState();
    final File file =
        Files.write(tempDir.resolve("initial-state.ssz"), state.sszSerialize().toArrayUnsafe())
            .toFile();
    return Optional.of(file.getAbsolutePath());
  }

  private void createService(final Optional<String> genesisStateResource) {
    service =
        new ReconstructHistoricalStatesService(
            storageUpdateChannel, chainDataClient, spec, genesisStateResource, statusLogger);
  }
}
