/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.ethereum.executionclient.ExecutionBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionLayerManagerImplTest {

  private final ExecutionEngineClient executionEngineClient =
      Mockito.mock(ExecutionEngineClient.class);

  private final ExecutionBuilderClient executionBuilderClient =
      Mockito.mock(ExecutionBuilderClient.class);

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final EventLogger eventLogger = mock(EventLogger.class);

  private final ExecutionLayerManagerImpl executionLayerManager =
      createExecutionLayerChannelImpl(true);

  @Test
  public void builderShouldNotBeAvailableWhenBuilderNotEnabled() {
    ExecutionLayerManagerImpl noBuilderEnabled = createExecutionLayerChannelImpl(false);

    // trigger update of builder status (should not do anything since builder is not enabled)
    noBuilderEnabled.onSlot(UInt64.ONE);

    assertThat(noBuilderEnabled.isBuilderAvailable()).isFalse();
    verifyNoInteractions(executionBuilderClient);
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void builderShouldBeAvailableWhenBuilderIsOperatingNormally() {
    SafeFuture<Response<Void>> builderClientResponse =
        SafeFuture.completedFuture(Response.withNullPayload());

    updateBuilderStatus(builderClientResponse);

    assertThat(executionLayerManager.isBuilderAvailable()).isTrue();
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void builderShouldNotBeAvailableWhenBuilderIsNotOperatingNormally() {
    SafeFuture<Response<Void>> builderClientResponse =
        SafeFuture.completedFuture(Response.withErrorMessage("oops"));

    updateBuilderStatus(builderClientResponse);

    assertThat(executionLayerManager.isBuilderAvailable()).isFalse();
    verify(eventLogger).executionBuilderIsOffline("oops");
  }

  @Test
  public void builderShouldNotBeAvailableWhenBuilderStatusCallFails() {
    SafeFuture<Response<Void>> builderClientResponse =
        SafeFuture.failedFuture(new Throwable("oops"));

    updateBuilderStatus(builderClientResponse);

    assertThat(executionLayerManager.isBuilderAvailable()).isFalse();
    verify(eventLogger).executionBuilderIsOffline("oops");
  }

  @Test
  public void builderAvailabilityIsUpdatedOnSlotEventAndLoggedAdequately() {
    // Initially builder should be available
    assertThat(executionLayerManager.isBuilderAvailable()).isTrue();

    // Given builder status is ok
    updateBuilderStatus(SafeFuture.completedFuture(Response.withNullPayload()));

    // Then
    assertThat(executionLayerManager.isBuilderAvailable()).isTrue();
    verifyNoInteractions(eventLogger);

    // Given builder status is not ok
    updateBuilderStatus(SafeFuture.completedFuture(Response.withErrorMessage("oops")));

    // Then
    assertThat(executionLayerManager.isBuilderAvailable()).isFalse();
    verify(eventLogger).executionBuilderIsOffline("oops");

    // Given builder status is back to being ok
    updateBuilderStatus(SafeFuture.completedFuture(Response.withNullPayload()));

    // Then
    assertThat(executionLayerManager.isBuilderAvailable()).isTrue();
    verify(eventLogger).executionBuilderIsBackOnline();
  }

  @Test
  public void builderGetHeaderGetPayload_shouldReturnHeaderAndPayloadViaBuilder() {
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();

    final ExecutionPayloadHeader header = prepareBuilderGetHeaderResponse(executionPayloadContext);
    prepareEngineGetPayloadResponse(executionPayloadContext);

    // we expect result from the builder
    assertThat(executionLayerManager.builderGetHeader(executionPayloadContext, slot))
        .isCompletedWithValue(header);

    // we expect both builder and local engine have been called
    verify(executionBuilderClient)
        .getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash());
    verify(executionEngineClient).getPayload(executionPayloadContext.getPayloadId());

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    final ExecutionPayload payload = prepareBuilderGetPayloadResponse(signedBlindedBeaconBlock);

    // we expect result from the builder
    assertThat(executionLayerManager.builderGetPayload(signedBlindedBeaconBlock))
        .isCompletedWithValue(payload);

    // we expect both builder and local engine have been called
    verify(executionBuilderClient).getPayload(signedBlindedBeaconBlock);
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void builderGetHeaderGetPayload_shouldReturnHeaderAndPayloadViaEngineOnBuilderFailure() {
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();

    prepareBuilderGetHeaderFailure(executionPayloadContext);
    final ExecutionPayload payload = prepareEngineGetPayloadResponse(executionPayloadContext);

    final ExecutionPayloadHeader header =
        spec.getGenesisSpec()
            .getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow()
            .getExecutionPayloadHeaderSchema()
            .createFromExecutionPayload(payload);

    // we expect local engine header as result
    assertThat(executionLayerManager.builderGetHeader(executionPayloadContext, slot))
        .isCompletedWithValue(header);

    // we expect both builder and local engine have been called
    verify(executionBuilderClient)
        .getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash());
    verify(executionEngineClient).getPayload(executionPayloadContext.getPayloadId());

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    // we expect result from the cached payload
    assertThat(executionLayerManager.builderGetPayload(signedBlindedBeaconBlock))
        .isCompletedWithValue(payload);

    // we expect no additional calls
    verifyNoMoreInteractions(executionBuilderClient);
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void builderGetHeaderGetPayload_shouldReturnHeaderAndPayloadViaEngineIfBuilderNotActive() {
    setBuilderOffline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();

    final ExecutionPayload payload = prepareEngineGetPayloadResponse(executionPayloadContext);

    final ExecutionPayloadHeader header =
        spec.getGenesisSpec()
            .getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow()
            .getExecutionPayloadHeaderSchema()
            .createFromExecutionPayload(payload);

    // we expect local engine header as result
    assertThat(executionLayerManager.builderGetHeader(executionPayloadContext, slot))
        .isCompletedWithValue(header);

    // we expect only local engine have been called
    verifyNoInteractions(executionBuilderClient);
    verify(executionEngineClient).getPayload(executionPayloadContext.getPayloadId());

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    // we expect result from the cached payload
    assertThat(executionLayerManager.builderGetPayload(signedBlindedBeaconBlock))
        .isCompletedWithValue(payload);

    // we expect no additional calls
    verifyNoMoreInteractions(executionBuilderClient);
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  public void
      builderGetHeaderGetPayload_shouldReturnHeaderAndPayloadViaEngineIfValidatorIsNotRegistered() {
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, false);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();

    final ExecutionPayload payload = prepareEngineGetPayloadResponse(executionPayloadContext);

    final ExecutionPayloadHeader header =
        spec.getGenesisSpec()
            .getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow()
            .getExecutionPayloadHeaderSchema()
            .createFromExecutionPayload(payload);

    // we expect local engine header as result
    assertThat(executionLayerManager.builderGetHeader(executionPayloadContext, slot))
        .isCompletedWithValue(header);

    // we expect only local engine have been called
    verifyNoInteractions(executionBuilderClient);
    verify(executionEngineClient).getPayload(executionPayloadContext.getPayloadId());

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    // we expect result from the cached payload
    assertThat(executionLayerManager.builderGetPayload(signedBlindedBeaconBlock))
        .isCompletedWithValue(payload);

    // we expect no additional calls
    verifyNoMoreInteractions(executionBuilderClient);
    verifyNoMoreInteractions(executionEngineClient);
  }

  @Test
  void onSlot_shouldCleanUpFallbackCache() {
    setBuilderOffline();

    IntStream.rangeClosed(1, 4)
        .forEach(
            value -> {
              final UInt64 slot = UInt64.valueOf(value);
              final ExecutionPayloadContext executionPayloadContext =
                  dataStructureUtil.randomPayloadExecutionContext(slot, false);
              prepareEngineGetPayloadResponse(executionPayloadContext);
              assertThat(executionLayerManager.builderGetHeader(executionPayloadContext, slot))
                  .isCompleted();
            });

    reset(executionEngineClient);

    // this trigger onSlot at slot 5, which cleans cache up to slot 3
    setBuilderOffline(UInt64.valueOf(5));

    // we expect a call to builder even if it is offline
    // since we have nothing better to do

    final UInt64 slot = UInt64.ONE;
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    final ExecutionPayload payload = prepareBuilderGetPayloadResponse(signedBlindedBeaconBlock);

    // we expect result from the builder
    assertThat(executionLayerManager.builderGetPayload(signedBlindedBeaconBlock))
        .isCompletedWithValue(payload);

    // we expect both builder and local engine have been called
    verify(executionBuilderClient).getPayload(signedBlindedBeaconBlock);
    verifyNoMoreInteractions(executionEngineClient);
  }

  private ExecutionPayloadHeader prepareBuilderGetHeaderResponse(
      final ExecutionPayloadContext executionPayloadContext) {
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();

    SignedBuilderBid signedBuilderBid = dataStructureUtil.randomSignedBuilderBid();

    when(executionBuilderClient.getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash()))
        .thenReturn(SafeFuture.completedFuture(new Response<>(signedBuilderBid)));

    return signedBuilderBid.getMessage().getExecutionPayloadHeader();
  }

  private ExecutionPayload prepareBuilderGetPayloadResponse(
      final SignedBeaconBlock signedBlindedBeaconBlock) {

    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

    when(executionBuilderClient.getPayload(signedBlindedBeaconBlock))
        .thenReturn(SafeFuture.completedFuture(new Response<>(payload)));

    return payload;
  }

  private void prepareBuilderGetHeaderFailure(
      final ExecutionPayloadContext executionPayloadContext) {
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();

    when(executionBuilderClient.getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash()))
        .thenReturn(SafeFuture.failedFuture(new Throwable("error")));
  }

  private ExecutionPayload prepareEngineGetPayloadResponse(
      final ExecutionPayloadContext executionPayloadContext) {

    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

    when(executionEngineClient.getPayload(executionPayloadContext.getPayloadId()))
        .thenReturn(
            SafeFuture.completedFuture(
                new Response<>(ExecutionPayloadV1.fromInternalExecutionPayload(payload))));

    return payload;
  }

  private ExecutionLayerManagerImpl createExecutionLayerChannelImpl(boolean builderEnabled) {
    return new ExecutionLayerManagerImpl(
        executionEngineClient,
        builderEnabled ? Optional.of(executionBuilderClient) : Optional.empty(),
        spec,
        eventLogger);
  }

  private void updateBuilderStatus(SafeFuture<Response<Void>> builderClientResponse) {
    updateBuilderStatus(builderClientResponse, UInt64.ONE);
  }

  private void updateBuilderStatus(SafeFuture<Response<Void>> builderClientResponse, UInt64 slot) {
    when(executionBuilderClient.status()).thenReturn(builderClientResponse);
    // trigger update of the builder status
    executionLayerManager.onSlot(slot);
  }

  private void setBuilderOffline() {
    setBuilderOffline(UInt64.ONE);
  }

  private void setBuilderOffline(UInt64 slot) {
    updateBuilderStatus(SafeFuture.completedFuture(Response.withErrorMessage("oops")), slot);
    reset(executionBuilderClient);
    assertThat(executionLayerManager.isBuilderAvailable()).isFalse();
  }

  private void setBuilderOnline() {
    setBuilderOnline(UInt64.ONE);
  }

  private void setBuilderOnline(UInt64 slot) {
    updateBuilderStatus(SafeFuture.completedFuture(Response.withNullPayload()), slot);
    reset(executionBuilderClient);
    assertThat(executionLayerManager.isBuilderAvailable()).isTrue();
  }
}
