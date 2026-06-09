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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
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
  private final Set<Bytes32> invalidExecutionPayloadRoots = new HashSet<>();
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
          invalidExecutionPayloadRoots,
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
    assertExecutionPayloadRecentlySeen(signedExecutionPayload);
    assertExecutionPayloadAvailableForPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldNotMarkExecutionPayloadAvailableWhenValidatedAtPayloadDueDeadline() {
    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload, payloadDueDeadlineMillis(signedExecutionPayload));

    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
    assertExecutionPayloadNotAvailableForPayloadAttestation(signedExecutionPayload);
    assertExecutionPayloadSeenForFullPayloadAttestation(signedExecutionPayload);
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
    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);

    final SafeFuture<InternalValidationResult> earlyResult =
        validateAndImport(signedExecutionPayload, UInt64.ZERO);
    final SafeFuture<InternalValidationResult> lateResult =
        validateAndImport(signedExecutionPayload, payloadDueDeadlineMillis(signedExecutionPayload));

    asyncRunner.executeDueActions();

    assertThat(earlyResult).isCompletedWithValue(ACCEPT);
    assertThat(lateResult).isCompletedWithValue(ACCEPT);
    assertExecutionPayloadAvailableForPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldKeepExecutionPayloadAvailableWhenLaterDuplicateValidatesFirst() {
    final SafeFuture<InternalValidationResult> earlyValidation = new SafeFuture<>();
    final SafeFuture<InternalValidationResult> lateValidation = new SafeFuture<>();
    givenValidationResults(signedExecutionPayload, earlyValidation, lateValidation);
    givenSuccessfulImport(signedExecutionPayload);

    final SafeFuture<InternalValidationResult> earlyResult =
        validateAndImport(signedExecutionPayload, UInt64.ZERO);
    final SafeFuture<InternalValidationResult> lateResult =
        validateAndImport(signedExecutionPayload, payloadDueDeadlineMillis(signedExecutionPayload));

    lateValidation.complete(ACCEPT);
    asyncRunner.executeDueActions();

    assertThat(lateResult).isCompletedWithValue(ACCEPT);
    assertExecutionPayloadAvailableForPayloadAttestation(signedExecutionPayload);

    earlyValidation.complete(IGNORE);

    assertThat(earlyResult).isCompletedWithValue(IGNORE);
    assertExecutionPayloadAvailableForPayloadAttestation(signedExecutionPayload);
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
    assertExecutionPayloadNotRecentlySeen(signedExecutionPayload);
    assertExecutionPayloadNotSeenForFullPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldReportExecutionPayloadSeenForFullPayloadAttestationWhenPresentInStore() {
    when(recentChainData.containsExecutionPayload(signedExecutionPayload.getBeaconBlockRoot()))
        .thenReturn(true);

    assertExecutionPayloadSeenForFullPayloadAttestation(signedExecutionPayload);
    assertExecutionPayloadNotRecentlySeen(signedExecutionPayload);
    assertExecutionPayloadNotAvailableForPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldNotReportExecutionPayloadSeenForFullPayloadAttestationWhenStoreUnavailable() {
    assertExecutionPayloadNotSeenForFullPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldNotReportExecutionPayloadSeenForFullPayloadAttestationUntilImportCompletes() {
    final SafeFuture<ExecutionPayloadImportResult> importResult = new SafeFuture<>();
    givenValidationResult(signedExecutionPayload, ACCEPT);
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(importResult);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload);

    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
    assertExecutionPayloadRecentlySeen(signedExecutionPayload);
    assertExecutionPayloadNotSeenForFullPayloadAttestation(signedExecutionPayload);

    importResult.complete(ExecutionPayloadImportResult.successful(signedExecutionPayload));

    assertExecutionPayloadSeenForFullPayloadAttestation(signedExecutionPayload);
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
    assertExecutionPayloadRecentlySeen(signedExecutionPayload);
    assertExecutionPayloadNotSeenForFullPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldNotCacheInvalidExecutionPayloadWhenImportFailsExecution() {
    final ExecutionPayloadImportResult failedImportResult =
        ExecutionPayloadImportResult.failedExecution(new RuntimeException("execution failed"));
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(completedFuture(failedImportResult));

    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload);
    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(failedImportResult);
    assertThat(invalidExecutionPayloadRoots)
        .doesNotContain(signedExecutionPayload.getBeaconBlockRoot());
    assertExecutionPayloadNotSeenForFullPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldCacheInvalidExecutionPayloadWhenImportFailsVerification() {
    final ExecutionPayloadImportResult failedImportResult =
        ExecutionPayloadImportResult.failedVerification(new RuntimeException("invalid"));
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(completedFuture(failedImportResult));

    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload);
    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(failedImportResult);
    assertThat(invalidExecutionPayloadRoots).contains(signedExecutionPayload.getBeaconBlockRoot());
    assertExecutionPayloadNotSeenForFullPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldCacheInvalidExecutionPayloadWhenDataAvailabilityCheckIsInvalid() {
    final ExecutionPayloadImportResult failedImportResult =
        ExecutionPayloadImportResult.failedDataAvailabilityCheckInvalid(
            Optional.of(new RuntimeException("invalid")));
    when(forkChoice.onExecutionPayloadEnvelope(signedExecutionPayload, executionLayer))
        .thenReturn(completedFuture(failedImportResult));

    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload);
    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(failedImportResult);
    assertThat(invalidExecutionPayloadRoots).contains(signedExecutionPayload.getBeaconBlockRoot());
    assertExecutionPayloadNotSeenForFullPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldProcessExecutionPayloadWhichHasBeenReceivedBeforeTheBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        signedExecutionPayloadForBlock(block);
    givenValidationResult(signedExecutionPayload, SAVE_FOR_FUTURE);

    validateAndImportAndJoin(signedExecutionPayload, UInt64.ZERO);
    validateAndImportAndJoin(
        signedExecutionPayload, payloadDueDeadlineMillis(signedExecutionPayload));

    asyncRunner.executeDueActions();
    verifyNoInteractions(receivedExecutionPayloadEventsChannelPublisher);

    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);
    executionPayloadManager.onBlockImported(block, false);

    asyncRunner.executeDueActions();

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload, false);
    assertThat(publishedExecutionPayload).hasValue(signedExecutionPayload);
    assertExecutionPayloadAvailableForPayloadAttestation(signedExecutionPayload);
  }

  @Test
  public void shouldProcessLaterValidPendingExecutionPayloadWhenEarlierCandidateRejected() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope invalidExecutionPayload =
        signedExecutionPayloadForBlock(block);
    final SignedExecutionPayloadEnvelope validExecutionPayload =
        signedExecutionPayloadForBlock(block);
    givenValidationResults(invalidExecutionPayload, SAVE_FOR_FUTURE, reject("invalid"));
    givenValidationResults(validExecutionPayload, SAVE_FOR_FUTURE, ACCEPT);
    givenSuccessfulImport(validExecutionPayload);

    validateAndImportAndJoin(invalidExecutionPayload, UInt64.ZERO);
    validateAndImportAndJoin(validExecutionPayload, UInt64.ONE);

    executionPayloadManager.onBlockImported(block, false);
    asyncRunner.executeDueActions();

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(validExecutionPayload, false);
    assertThat(publishedExecutionPayload).hasValue(validExecutionPayload);
  }

  @Test
  public void shouldLimitPendingExecutionPayloadCandidatesForSameBlockAndBuilder() {
    final int pendingPayloadLimit = RECENT_SEEN_EXECUTION_PAYLOADS_CACHE_SIZE * 2;
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final List<SignedExecutionPayloadEnvelope> executionPayloads =
        IntStream.rangeClosed(0, pendingPayloadLimit)
            .mapToObj(__ -> signedExecutionPayloadForBlock(block))
            .toList();
    executionPayloads.forEach(
        executionPayload -> {
          givenValidationResults(executionPayload, SAVE_FOR_FUTURE, ACCEPT);
          givenSuccessfulImport(executionPayload);
        });

    for (int i = 0; i < executionPayloads.size(); i++) {
      validateAndImportAndJoin(executionPayloads.get(i), UInt64.valueOf(i));
    }

    executionPayloadManager.onBlockImported(block, false);
    asyncRunner.executeDueActions();

    verify(executionPayloadGossipValidator, times(1)).validate(executionPayloads.get(0));
    verify(forkChoice, never())
        .onExecutionPayloadEnvelope(executionPayloads.get(0), executionLayer);
    verify(executionPayloadGossipValidator, times(2)).validate(executionPayloads.get(1));
    assertThat(publishedExecutionPayload).hasValue(executionPayloads.get(pendingPayloadLimit));
  }

  private void givenValidationResult(
      final SignedExecutionPayloadEnvelope executionPayload,
      final InternalValidationResult validationResult) {
    when(executionPayloadGossipValidator.validate(executionPayload))
        .thenReturn(completedFuture(validationResult));
  }

  private void givenValidationResults(
      final SignedExecutionPayloadEnvelope executionPayload,
      final InternalValidationResult firstResult,
      final InternalValidationResult secondResult) {
    when(executionPayloadGossipValidator.validate(executionPayload))
        .thenReturn(completedFuture(firstResult))
        .thenReturn(completedFuture(secondResult));
  }

  private void givenValidationResults(
      final SignedExecutionPayloadEnvelope executionPayload,
      final SafeFuture<InternalValidationResult> firstResult,
      final SafeFuture<InternalValidationResult> secondResult) {
    when(executionPayloadGossipValidator.validate(executionPayload))
        .thenReturn(firstResult)
        .thenReturn(secondResult);
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

  private void assertExecutionPayloadRecentlySeen(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                executionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  private void assertExecutionPayloadNotRecentlySeen(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadRecentlySeen(
                executionPayload.getBeaconBlockRoot()))
        .isFalse();
  }

  private void assertExecutionPayloadAvailableForPayloadAttestation(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadAvailableForPayloadAttestation(
                executionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  private void assertExecutionPayloadNotAvailableForPayloadAttestation(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadAvailableForPayloadAttestation(
                executionPayload.getBeaconBlockRoot()))
        .isFalse();
  }

  private void assertExecutionPayloadSeenForFullPayloadAttestation(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadSeenForFullPayloadAttestation(
                executionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  private void assertExecutionPayloadNotSeenForFullPayloadAttestation(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadSeenForFullPayloadAttestation(
                executionPayload.getBeaconBlockRoot()))
        .isFalse();
  }
}
