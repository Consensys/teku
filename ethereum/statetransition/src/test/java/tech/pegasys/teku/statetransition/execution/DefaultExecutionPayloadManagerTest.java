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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
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

  @BeforeEach
  void setUp() {
    when(recentChainData.getGenesisTimeMillis()).thenReturn(UInt64.ZERO);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getTimeInMillis()).thenReturn(UInt64.ZERO);
  }

  @Test
  public void shouldDelegateIsExecutionPayloadRecentlySeenToTheGossipValidator() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    when(executionPayloadGossipValidator.isPayloadSeen(beaconBlockRoot)).thenReturn(true);
    assertThat(executionPayloadManager.isExecutionPayloadRecentlySeen(beaconBlockRoot)).isTrue();
  }

  @Test
  public void shouldValidateAndImport() {
    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertDebugLog("Successfully imported execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload, false);
    assertExecutionPayloadSeenBeforeDeadline(signedExecutionPayload);
  }

  @Test
  public void shouldNotMarkExecutionPayloadSeenBeforeDeadlineWhenReceivedAfterTheDeadline() {
    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(
            signedExecutionPayload, payloadDueDeadlineMillis(signedExecutionPayload).plus(1));

    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
    assertExecutionPayloadNotSeenBeforeDeadline(signedExecutionPayload);
  }

  @Test
  public void shouldNotImportIfValidationFails() {
    final InternalValidationResult rejectedResult = reject("oopsy");
    givenValidationResult(signedExecutionPayload, rejectedResult);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload);

    asyncRunner.executeDueActions();

    verifyNoInteractions(forkChoice);
    assertThat(resultFuture).isCompletedWithValue(rejectedResult);
  }

  @Test
  public void shouldHandleInternalErrorsWhileImporting() {
    givenValidationResult(signedExecutionPayload, ACCEPT);
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenThrow(new IllegalStateException("oopsy"));

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertErrorLog("Internal error while importing execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
  }

  @Test
  public void shouldProcessExecutionPayloadWhichHasBeenReceivedBeforeTheBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        signedExecutionPayloadForBlock(block);
    givenValidationResult(signedExecutionPayload, SAVE_FOR_FUTURE);

    validateAndImportAndJoin(signedExecutionPayload, UInt64.ZERO);

    asyncRunner.executeDueActions();
    verifyNoInteractions(receivedExecutionPayloadEventsChannelPublisher);

    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);
    executionPayloadManager.onBlockImported(block, false);

    asyncRunner.executeDueActions();

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload, false);
    assertThat(publishedExecutionPayload).hasValue(signedExecutionPayload);
    assertExecutionPayloadSeenBeforeDeadline(signedExecutionPayload);
  }

  private void givenValidationResult(
      final SignedExecutionPayloadEnvelope executionPayload,
      final InternalValidationResult validationResult) {
    when(executionPayloadGossipValidator.validate(executionPayload))
        .thenReturn(completedFuture(validationResult));
  }

  private void givenSuccessfulImport(final SignedExecutionPayloadEnvelope executionPayload) {
    when(forkChoice.onExecutionPayloadEnvelope(executionPayload, executionLayer))
        .thenReturn(completedFuture(ExecutionPayloadImportResult.successful(executionPayload)));
  }

  private SafeFuture<InternalValidationResult> validateAndImport(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return executionPayloadManager.validateAndImportExecutionPayload(executionPayload);
  }

  private SafeFuture<InternalValidationResult> validateAndImport(
      final SignedExecutionPayloadEnvelope executionPayload, final UInt64 arrivalTimestamp) {
    return executionPayloadManager.validateAndImportExecutionPayload(
        executionPayload, Optional.of(arrivalTimestamp));
  }

  private void validateAndImportAndJoin(
      final SignedExecutionPayloadEnvelope executionPayload, final UInt64 arrivalTimestamp) {
    SafeFutureAssert.safeJoin(validateAndImport(executionPayload, arrivalTimestamp));
  }

  private UInt64 payloadDueDeadlineMillis(final SignedExecutionPayloadEnvelope executionPayload) {
    final UInt64 slot = executionPayload.getSlot();
    return spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis())
        .plus(spec.getPayloadDueMillis(slot).orElseThrow());
  }

  private SignedExecutionPayloadEnvelope signedExecutionPayloadForBlock(
      final SignedBeaconBlock block) {
    return dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(block);
  }

  private void assertExecutionPayloadSeenBeforeDeadline(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadSeenBeforeDeadline(
                executionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  private void assertExecutionPayloadNotSeenBeforeDeadline(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadSeenBeforeDeadline(
                executionPayload.getBeaconBlockRoot()))
        .isFalse();
  }
}
