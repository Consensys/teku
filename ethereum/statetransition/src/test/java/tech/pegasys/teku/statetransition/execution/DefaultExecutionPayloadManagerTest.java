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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

class DefaultExecutionPayloadManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ExecutionPayloadGossipValidator executionPayloadGossipValidator =
      mock(ExecutionPayloadGossipValidator.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final ReceivedExecutionPayloadEventsChannel
      receivedExecutionPayloadEventsChannelPublisher =
          mock(ReceivedExecutionPayloadEventsChannel.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private Optional<SignedExecutionPayloadEnvelope> publishedExecutionPayload = Optional.empty();

  private final DefaultExecutionPayloadManager executionPayloadManager =
      new DefaultExecutionPayloadManager(
          spec,
          asyncRunner,
          executionPayloadGossipValidator,
          forkChoice,
          executionLayer,
          receivedExecutionPayloadEventsChannelPublisher,
          recentChainData,
          executionPayload -> {
            publishedExecutionPayload = Optional.of(executionPayload);
            return SafeFuture.COMPLETE;
          });

  private final SignedExecutionPayloadEnvelope signedExecutionPayload =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);

  private final ExecutionPayloadImportResult successfulImportResult =
      ExecutionPayloadImportResult.successful(signedExecutionPayload);

  @BeforeEach
  void setUp() {
    when(recentChainData.getGenesisTimeMillis()).thenReturn(UInt64.ZERO);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getTimeInMillis()).thenReturn(UInt64.ZERO);
  }

  @Test
  public void shouldValidateAndImport() {
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(SafeFuture.completedFuture(successfulImportResult));

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertDebugLog("Successfully imported execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(InternalValidationResult.ACCEPT);

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload, false);

    // verify the `beacon_block_root` is cached
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isTrue();
    assertThat(
            executionPayloadManager.isExecutionPayloadAvailableForPayloadAttestation(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  @Test
  public void shouldNotMarkExecutionPayloadAvailableWhenValidatedAtPayloadDueDeadline() {
    final UInt64 slot = signedExecutionPayload.getSlot();
    final UInt64 payloadDueDeadlineMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis())
            .plus(spec.getPayloadDueMillis(slot).orElseThrow());
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(SafeFuture.completedFuture(successfulImportResult));

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(
            signedExecutionPayload, Optional.of(payloadDueDeadlineMillis));

    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            executionPayloadManager.isExecutionPayloadAvailableForPayloadAttestation(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isFalse();
  }

  @Test
  public void shouldThrowWhenStoreIsUnavailableForArrivalTimestampFallback() {
    when(recentChainData.getStore()).thenReturn(null);

    assertThatThrownBy(
            () -> executionPayloadManager.validateAndImportExecutionPayload(signedExecutionPayload))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Store is unavailable while resolving execution payload arrival time");
    verifyNoInteractions(executionPayloadGossipValidator);
  }

  @Test
  public void shouldKeepExecutionPayloadAvailableWhenDuplicateArrivesAfterPayloadDueDeadline() {
    final UInt64 slot = signedExecutionPayload.getSlot();
    final UInt64 payloadDueDeadlineMillis =
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis())
            .plus(spec.getPayloadDueMillis(slot).orElseThrow());
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(SafeFuture.completedFuture(successfulImportResult));

    final SafeFuture<InternalValidationResult> earlyResult =
        executionPayloadManager.validateAndImportExecutionPayload(
            signedExecutionPayload, Optional.of(UInt64.ZERO));
    final SafeFuture<InternalValidationResult> lateResult =
        executionPayloadManager.validateAndImportExecutionPayload(
            signedExecutionPayload, Optional.of(payloadDueDeadlineMillis));

    asyncRunner.executeDueActions();

    assertThat(earlyResult).isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(lateResult).isCompletedWithValue(InternalValidationResult.ACCEPT);
    assertThat(
            executionPayloadManager.isExecutionPayloadAvailableForPayloadAttestation(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  @Test
  public void shouldNotImportIfValidationFails() {
    final InternalValidationResult rejectedResult = InternalValidationResult.reject("oopsy");
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(rejectedResult));

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(signedExecutionPayload);

    asyncRunner.executeDueActions();

    verifyNoInteractions(forkChoice);
    assertThat(resultFuture).isCompletedWithValue(rejectedResult);

    // `beacon_block_root` should not be cached when gossip validation fails
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isFalse();
  }

  @Test
  public void shouldHandleInternalErrorsWhileImporting() {
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final IllegalStateException exception = new IllegalStateException("oopsy");

    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenThrow(exception);

    final SafeFuture<InternalValidationResult> resultFuture =
        executionPayloadManager.validateAndImportExecutionPayload(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertErrorLog("Internal error while importing execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(InternalValidationResult.ACCEPT);

    // verify the `beacon_block_root` is cached
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  @Test
  public void shouldProcessExecutionPayloadWhichHasBeenReceivedBeforeTheBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(block);
    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));
    final UInt64 payloadDueDeadlineMillis =
        spec.computeTimeMillisAtSlot(
                signedExecutionPayload.getSlot(), recentChainData.getGenesisTimeMillis())
            .plus(spec.getPayloadDueMillis(signedExecutionPayload.getSlot()).orElseThrow());

    // should just cache the payload for future processing
    SafeFutureAssert.safeJoin(
        executionPayloadManager.validateAndImportExecutionPayload(
            signedExecutionPayload, Optional.of(UInt64.ZERO)));
    SafeFutureAssert.safeJoin(
        executionPayloadManager.validateAndImportExecutionPayload(
            signedExecutionPayload, Optional.of(payloadDueDeadlineMillis)));

    asyncRunner.executeDueActions();
    verifyNoInteractions(receivedExecutionPayloadEventsChannelPublisher);

    when(executionPayloadGossipValidator.validate(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(SafeFuture.completedFuture(successfulImportResult));
    executionPayloadManager.onBlockImported(block, false);

    asyncRunner.executeDueActions();
    // verify the payload has been processed
    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload, false);

    // verify the payload has been published
    assertThat(publishedExecutionPayload).hasValue(signedExecutionPayload);
    assertThat(
            executionPayloadManager.isExecutionPayloadAvailableForPayloadAttestation(
                signedExecutionPayload.getBeaconBlockRoot()))
        .isTrue();
  }
}
