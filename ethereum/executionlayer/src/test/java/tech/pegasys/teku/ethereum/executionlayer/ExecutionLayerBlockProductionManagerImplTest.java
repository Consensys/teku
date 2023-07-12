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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl.Source;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.builder.BlindedBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionLayerBlockProductionManagerImplTest {

  private final ExecutionClientHandler executionClientHandler = mock(ExecutionClientHandler.class);

  private final BuilderClient builderClient = Mockito.mock(BuilderClient.class);

  private Spec spec = TestSpecFactory.createMinimalCapella();

  private DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private final EventLogger eventLogger = mock(EventLogger.class);

  private final BuilderCircuitBreaker builderCircuitBreaker = mock(BuilderCircuitBreaker.class);
  private ExecutionLayerManagerImpl executionLayerManager;
  private ExecutionLayerBlockProductionManagerImpl blockProductionManager;

  @BeforeEach
  public void setup() {
    this.executionLayerManager = createExecutionLayerChannelImpl(true, false);
    this.blockProductionManager =
        new ExecutionLayerBlockProductionManagerImpl(executionLayerManager);
  }

  @Test
  public void preDeneb_builderOffline() throws Exception {
    setBuilderOffline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayload payload =
        prepareEngineGetPayloadResponse(executionPayloadContext, UInt256.ZERO, slot);

    final ExecutionPayloadHeader header =
        spec.getGenesisSpec()
            .getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow()
            .getExecutionPayloadHeaderSchema()
            .createFromExecutionPayload(payload);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(executionPayloadContext, state, true);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFuture()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFuture()).isEmpty();

    // we expect local engine header as result
    final HeaderWithFallbackData expectedResult =
        HeaderWithFallbackData.create(
            header, new FallbackData(payload, FallbackReason.BUILDER_NOT_AVAILABLE));
    final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture =
        executionPayloadResult.getHeaderWithFallbackDataFuture().orElseThrow();
    assertThat(headerWithFallbackDataFuture.get()).isEqualTo(expectedResult);
    final BuilderPayload localPayload =
        verifyFallbackToLocalEL(slot, executionPayloadContext, expectedResult);

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);
    // wrong slot
    assertThat(blockProductionManager.getCachedPayloadResult(slot.plus(1))).isEmpty();

    final SafeFuture<BuilderPayload> unblindedPayload =
        blockProductionManager.getUnblindedPayload(
            dataStructureUtil.randomSignedBlindedBeaconBlock(slot));
    assertThat(unblindedPayload.get()).isEqualTo(localPayload);

    // wrong slot, we will hit builder client by this call
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot.plus(1));
    assertThatThrownBy(() -> blockProductionManager.getUnblindedPayload(signedBlindedBeaconBlock));
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
  }

  @Test
  public void preDeneb_builderOnline() throws Exception {
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    // we expect result from the builder
    final BuilderBid builderBid = prepareBuilderGetHeaderResponse(executionPayloadContext, false);
    prepareEngineGetPayloadResponse(executionPayloadContext, UInt256.ZERO, slot);
    final ExecutionPayloadHeader header = builderBid.getHeader();
    final HeaderWithFallbackData expectedResult = HeaderWithFallbackData.create(header);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(executionPayloadContext, state, true);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFuture()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFuture()).isEmpty();
    final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture =
        executionPayloadResult.getHeaderWithFallbackDataFuture().orElseThrow();
    assertThat(headerWithFallbackDataFuture.get()).isEqualTo(expectedResult);

    // we expect both builder and local engine have been called
    verifyBuilderCalled(slot, executionPayloadContext);
    verifyEngineCalled(executionPayloadContext, slot);

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    final ExecutionPayload payload = prepareBuilderGetPayloadResponse(signedBlindedBeaconBlock);

    // we expect result from the builder
    assertThat(blockProductionManager.getUnblindedPayload(signedBlindedBeaconBlock))
        .isCompletedWithValue(payload);

    // we expect both builder and local engine have been called
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
    verifyNoMoreInteractions(executionClientHandler);
    verifySourceCounter(Source.BUILDER, FallbackReason.NONE);
  }

  @Test
  public void preDeneb_noBuilder() throws Exception {
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayload payload =
        prepareEngineGetPayloadResponse(executionPayloadContext, UInt256.ZERO, slot);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(executionPayloadContext, state, false);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getHeaderWithFallbackDataFuture()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFuture()).isEmpty();

    final ExecutionPayload executionPayload =
        executionPayloadResult.getExecutionPayloadFuture().orElseThrow().get();
    assertThat(executionPayload).isEqualTo(payload);

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);

    // we will hit builder client by this call
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);
    assertThatThrownBy(() -> blockProductionManager.getUnblindedPayload(signedBlindedBeaconBlock));
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
  }

  @Test
  public void postDeneb_builderOffline() throws Exception {
    setupDeneb();
    setBuilderOffline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final GetPayloadResponse getPayloadResponse =
        prepareEngineGetPayloadResponseWithBlobs(executionPayloadContext, UInt256.ZERO, slot);

    final ExecutionPayload payload = getPayloadResponse.getExecutionPayload();
    final BlobsBundle blobsBundle = getPayloadResponse.getBlobsBundle().orElseThrow();

    final SchemaDefinitionsDeneb schemaDefinitions =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions());

    final ExecutionPayloadHeader header =
        schemaDefinitions.getExecutionPayloadHeaderSchema().createFromExecutionPayload(payload);

    final BlindedBlobsBundle blindedBlobsBundle =
        schemaDefinitions.getBlindedBlobsBundleSchema().createFromExecutionBlobsBundle(blobsBundle);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockAndBlobsProduction(
            executionPayloadContext, state, true);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFuture()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFuture()).isEmpty();

    // we expect local engine header as result
    final HeaderWithFallbackData expectedResult =
        HeaderWithFallbackData.create(
            header,
            Optional.of(blindedBlobsBundle),
            new FallbackData(
                payload, Optional.of(blobsBundle), FallbackReason.BUILDER_NOT_AVAILABLE));
    final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture =
        executionPayloadResult.getHeaderWithFallbackDataFuture().orElseThrow();
    assertThat(headerWithFallbackDataFuture.get()).isEqualTo(expectedResult);
    final BuilderPayload localPayload =
        verifyFallbackToLocalEL(slot, executionPayloadContext, expectedResult);

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);

    final SafeFuture<BuilderPayload> unblindedPayload =
        blockProductionManager.getUnblindedPayload(
            dataStructureUtil.randomSignedBlindedBlockContents(slot));
    assertThat(unblindedPayload.get()).isEqualTo(localPayload);

    verifyNoMoreInteractions(builderClient);
    verifyNoMoreInteractions(executionClientHandler);
  }

  @Test
  public void postDeneb_builderOnline() throws Exception {
    setupDeneb();
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    // we expect result from the builder
    final BuilderBid builderBid = prepareBuilderGetHeaderResponse(executionPayloadContext, false);
    prepareEngineGetPayloadResponseWithBlobs(executionPayloadContext, UInt256.ZERO, slot);

    final HeaderWithFallbackData expectedResult =
        HeaderWithFallbackData.create(
            builderBid.getHeader(),
            Optional.of(builderBid.getOptionalBlindedBlobsBundle().orElseThrow()));

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockAndBlobsProduction(
            executionPayloadContext, state, true);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFuture()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFuture()).isEmpty();

    final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture =
        executionPayloadResult.getHeaderWithFallbackDataFuture().orElseThrow();
    assertThat(headerWithFallbackDataFuture.get()).isEqualTo(expectedResult);

    // we expect both builder and local engine have been called
    verifyBuilderCalled(slot, executionPayloadContext);
    verifyEngineCalled(executionPayloadContext, slot);

    final SignedBlindedBlockContents signedBlindedBlockContents =
        dataStructureUtil.randomSignedBlindedBlockContents(slot);

    final ExecutionPayloadAndBlobsBundle payloadAndBlobsBundle =
        prepareBuilderGetPayloadResponseWithBlobs(signedBlindedBlockContents);

    // we expect result from the builder
    assertThat(blockProductionManager.getUnblindedPayload(signedBlindedBlockContents))
        .isCompletedWithValue(payloadAndBlobsBundle);

    // we expect both builder and local engine have been called
    verify(builderClient).getPayload(signedBlindedBlockContents);
    verifyNoMoreInteractions(executionClientHandler);
    verifySourceCounter(Source.BUILDER, FallbackReason.NONE);
  }

  @Test
  public void postDeneb_noBuilder() throws Exception {
    setupDeneb();
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final GetPayloadResponse getPayloadResponse =
        prepareEngineGetPayloadResponseWithBlobs(executionPayloadContext, UInt256.ZERO, slot);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockAndBlobsProduction(
            executionPayloadContext, state, false);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getHeaderWithFallbackDataFuture()).isEmpty();

    final ExecutionPayload executionPayload =
        executionPayloadResult.getExecutionPayloadFuture().orElseThrow().get();
    assertThat(executionPayload).isEqualTo(getPayloadResponse.getExecutionPayload());
    final Optional<BlobsBundle> blobsBundle =
        executionPayloadResult.getBlobsBundleFuture().orElseThrow().get();
    assertThat(blobsBundle).isEqualTo(getPayloadResponse.getBlobsBundle());

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);

    // we will hit builder client by this call
    final SignedBlindedBlockContents signedBlindedBlockContents =
        dataStructureUtil.randomSignedBlindedBlockContents(slot);
    assertThatThrownBy(
        () -> blockProductionManager.getUnblindedPayload(signedBlindedBlockContents));
    verify(builderClient).getPayload(signedBlindedBlockContents);
  }

  private void setupDeneb() {
    this.spec = TestSpecFactory.createMinimalDeneb();
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.executionLayerManager = createExecutionLayerChannelImpl(true, false);
    this.blockProductionManager =
        new ExecutionLayerBlockProductionManagerImpl(executionLayerManager);
  }

  private BuilderBid prepareBuilderGetHeaderResponse(
      final ExecutionPayloadContext executionPayloadContext, final boolean prepareEmptyResponse) {
    final UInt64 slot = executionPayloadContext.getForkChoiceState().getHeadBlockSlot();

    final SignedBuilderBid signedBuilderBid = dataStructureUtil.randomSignedBuilderBid();

    doAnswer(
            __ -> {
              if (prepareEmptyResponse) {
                return SafeFuture.completedFuture(new Response<>(Optional.empty()));
              }
              return SafeFuture.completedFuture(new Response<>(Optional.of(signedBuilderBid)));
            })
        .when(builderClient)
        .getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash());

    return signedBuilderBid.getMessage();
  }

  private BuilderPayload verifyFallbackToLocalEL(
      final UInt64 slot,
      final ExecutionPayloadContext executionPayloadContext,
      final HeaderWithFallbackData headerWithFallbackData) {
    final FallbackData fallbackData =
        headerWithFallbackData.getFallbackDataOptional().orElseThrow();
    final FallbackReason fallbackReason = fallbackData.getReason();
    if (fallbackReason == FallbackReason.BUILDER_HEADER_NOT_AVAILABLE
        || fallbackReason == FallbackReason.BUILDER_ERROR
        || fallbackReason == FallbackReason.LOCAL_BLOCK_VALUE_WON) {
      // we expect both builder and local engine have been called
      verifyBuilderCalled(slot, executionPayloadContext);
    } else {
      // we expect only local engine have been called
      verifyNoInteractions(builderClient);
    }
    verifyEngineCalled(executionPayloadContext, slot);

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    final BuilderPayload builderPayload =
        spec.atSlot(slot)
            .getSchemaDefinitions()
            .toVersionDeneb()
            .map(
                schemaDefinitionsDeneb -> {
                  final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
                      schemaDefinitionsDeneb
                          .getBlobsBundleSchema()
                          .createFromExecutionBlobsBundle(
                              fallbackData.getBlobsBundle().orElseThrow());
                  return (BuilderPayload)
                      schemaDefinitionsDeneb
                          .getExecutionPayloadAndBlobsBundleSchema()
                          .create(fallbackData.getExecutionPayload(), blobsBundle);
                })
            .orElseGet(fallbackData::getExecutionPayload);

    // we expect result from the cached payload
    assertThat(
            executionLayerManager.builderGetPayload(
                signedBlindedBeaconBlock,
                (aSlot) ->
                    Optional.of(
                        new ExecutionPayloadResult(
                            executionPayloadContext,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(SafeFuture.completedFuture(headerWithFallbackData))))))
        .isCompletedWithValue(builderPayload);

    // we expect no additional calls
    verifyNoMoreInteractions(builderClient);
    verifyNoMoreInteractions(executionClientHandler);

    verifySourceCounter(Source.BUILDER_LOCAL_EL_FALLBACK, fallbackReason);

    return builderPayload;
  }

  private ExecutionPayload prepareBuilderGetPayloadResponse(
      final SignedBlindedBlockContainer signedBlindedBlockContainer) {
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    when(builderClient.getPayload(signedBlindedBlockContainer))
        .thenReturn(SafeFuture.completedFuture(new Response<>(payload)));
    return payload;
  }

  private ExecutionPayloadAndBlobsBundle prepareBuilderGetPayloadResponseWithBlobs(
      final SignedBlindedBlockContainer signedBlindedBlockContainer) {
    final ExecutionPayloadAndBlobsBundle payloadAndBlobsBundle =
        dataStructureUtil.randomExecutionPayloadAndBlobsBundle();
    when(builderClient.getPayload(signedBlindedBlockContainer))
        .thenReturn(SafeFuture.completedFuture(new Response<>(payloadAndBlobsBundle)));
    return payloadAndBlobsBundle;
  }

  private ExecutionPayload prepareEngineGetPayloadResponse(
      final ExecutionPayloadContext executionPayloadContext,
      final UInt256 blockValue,
      final UInt64 slot) {
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    when(executionClientHandler.engineGetPayload(executionPayloadContext, slot))
        .thenReturn(SafeFuture.completedFuture(new GetPayloadResponse(payload, blockValue)));
    return payload;
  }

  private GetPayloadResponse prepareEngineGetPayloadResponseWithBlobs(
      final ExecutionPayloadContext executionPayloadContext,
      final UInt256 blockValue,
      final UInt64 slot) {
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();
    final GetPayloadResponse getPayloadResponse =
        new GetPayloadResponse(payload, blockValue, blobsBundle, false);
    when(executionClientHandler.engineGetPayload(executionPayloadContext, slot))
        .thenReturn(SafeFuture.completedFuture(getPayloadResponse));
    return getPayloadResponse;
  }

  private ExecutionLayerManagerImpl createExecutionLayerChannelImpl(
      final boolean builderEnabled, final boolean builderValidatorEnabled) {
    when(builderCircuitBreaker.isEngaged(any())).thenReturn(false);
    return ExecutionLayerManagerImpl.create(
        eventLogger,
        executionClientHandler,
        builderEnabled ? Optional.of(builderClient) : Optional.empty(),
        spec,
        stubMetricsSystem,
        builderValidatorEnabled
            ? new BuilderBidValidatorImpl(eventLogger)
            : BuilderBidValidator.NOOP,
        builderCircuitBreaker,
        BlobsBundleValidator.NOOP,
        Optional.of(100),
        true);
  }

  private void updateBuilderStatus(SafeFuture<Response<Void>> builderClientResponse, UInt64 slot) {
    when(builderClient.status()).thenReturn(builderClientResponse);
    // trigger update of the builder status
    executionLayerManager.onSlot(slot);
  }

  private void setBuilderOffline() {
    setBuilderOffline(UInt64.ONE);
  }

  private void setBuilderOffline(final UInt64 slot) {
    updateBuilderStatus(SafeFuture.completedFuture(Response.withErrorMessage("oops")), slot);
    reset(builderClient);
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isFalse();
  }

  private void setBuilderOnline() {
    updateBuilderStatus(SafeFuture.completedFuture(Response.withNullPayload()), UInt64.ONE);
    reset(builderClient);
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isTrue();
  }

  private void verifyBuilderCalled(
      final UInt64 slot, final ExecutionPayloadContext executionPayloadContext) {
    verify(builderClient)
        .getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash());
  }

  private void verifyEngineCalled(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    verify(executionClientHandler).engineGetPayload(executionPayloadContext, slot);
  }

  private void verifySourceCounter(final Source source, final FallbackReason reason) {
    final long actualCount =
        stubMetricsSystem
            .getCounter(TekuMetricCategory.BEACON, "execution_payload_source")
            .getValue(source.toString(), reason.toString());
    assertThat(actualCount).isOne();
  }
}
