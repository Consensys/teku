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

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EB_REQUESTS;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EE_REQUESTS;

import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.ThrottlingBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ThrottlingExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.metrics.MetricRecordingBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.metrics.MetricRecordingExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.rest.RestBuilderClient;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClient;
import tech.pegasys.teku.ethereum.executionclient.web3j.Web3JClient;
import tech.pegasys.teku.ethereum.executionclient.web3j.Web3JExecutionEngineClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;

public class ExecutionLayerManagerImpl implements ExecutionLayerManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ExecutionClientHandler executionClientHandler;
  private final BlobsBundleValidator blobsBundleValidator;
  private final ExecutionBuilderModule executionBuilderModule;
  private final LabelledMetric<Counter> executionPayloadSourceCounter;

  public static ExecutionLayerManagerImpl create(
      final EventLogger eventLogger,
      final ExecutionClientHandler executionClientHandler,
      final Optional<BuilderClient> builderClient,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final BuilderBidValidator builderBidValidator,
      final BuilderCircuitBreaker builderCircuitBreaker,
      final BlobsBundleValidator blobsBundleValidator) {
    final LabelledMetric<Counter> executionPayloadSourceCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "execution_payload_source",
            "Counter recording the source of the execution payload during block production",
            "source",
            "fallback_reason");

    // counter initialization
    executionPayloadSourceCounter.labels(
        Source.LOCAL_EL.toString(), FallbackReason.NONE.toString());
    executionPayloadSourceCounter.labels(Source.BUILDER.toString(), FallbackReason.NONE.toString());
    Arrays.stream(FallbackReason.values())
        .map(FallbackReason::toString)
        .forEach(
            fallbackReason ->
                executionPayloadSourceCounter.labels(
                    Source.BUILDER_LOCAL_EL_FALLBACK.toString(), fallbackReason));

    return new ExecutionLayerManagerImpl(
        executionClientHandler,
        builderClient,
        spec,
        eventLogger,
        builderBidValidator,
        builderCircuitBreaker,
        executionPayloadSourceCounter,
        blobsBundleValidator);
  }

  public static ExecutionLayerManagerImpl create(
      final EventLogger eventLogger,
      final ExecutionEngineClient executionEngineClient,
      final Optional<BuilderClient> builderClient,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final BuilderBidValidator builderBidValidator,
      final BuilderCircuitBreaker builderCircuitBreaker,
      final BlobsBundleValidator blobsBundleValidator) {
    final ExecutionClientHandler executionClientHandler;

    if (spec.isMilestoneSupported(SpecMilestone.EIP4844)) {
      executionClientHandler = new Eip4844ExecutionClientHandler(spec, executionEngineClient);
    } else if (spec.isMilestoneSupported(SpecMilestone.CAPELLA)) {
      executionClientHandler = new CapellaExecutionClientHandler(spec, executionEngineClient);
    } else {
      executionClientHandler = new BellatrixExecutionClientHandler(spec, executionEngineClient);
    }

    return create(
        eventLogger,
        executionClientHandler,
        builderClient,
        spec,
        metricsSystem,
        builderBidValidator,
        builderCircuitBreaker,
        blobsBundleValidator);
  }

  public static ExecutionEngineClient createEngineClient(
      final Version version,
      final Web3JClient web3JClient,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem) {
    checkNotNull(version);
    LOG.info("Execution Engine version: {}", version);
    if (version != Version.KILNV2) {
      throw new InvalidConfigurationException("Unsupported execution engine version: " + version);
    }
    final ExecutionEngineClient engineClient = new Web3JExecutionEngineClient(web3JClient);
    final ExecutionEngineClient metricEngineClient =
        new MetricRecordingExecutionEngineClient(engineClient, timeProvider, metricsSystem);
    return new ThrottlingExecutionEngineClient(
        metricEngineClient, MAXIMUM_CONCURRENT_EE_REQUESTS, metricsSystem);
  }

  public static BuilderClient createBuilderClient(
      final RestClient builderRestClient,
      final Spec spec,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem) {

    final RestBuilderClient restBuilderClient = new RestBuilderClient(builderRestClient, spec);
    final MetricRecordingBuilderClient metricRecordingBuilderClient =
        new MetricRecordingBuilderClient(restBuilderClient, timeProvider, metricsSystem);
    return new ThrottlingBuilderClient(
        metricRecordingBuilderClient, MAXIMUM_CONCURRENT_EB_REQUESTS, metricsSystem);
  }

  private ExecutionLayerManagerImpl(
      final ExecutionClientHandler executionClientHandler,
      final Optional<BuilderClient> builderClient,
      final Spec spec,
      final EventLogger eventLogger,
      final BuilderBidValidator builderBidValidator,
      final BuilderCircuitBreaker builderCircuitBreaker,
      final LabelledMetric<Counter> executionPayloadSourceCounter,
      final BlobsBundleValidator blobsBundleValidator) {
    this.executionClientHandler = executionClientHandler;
    this.spec = spec;
    this.blobsBundleValidator = blobsBundleValidator;
    this.executionPayloadSourceCounter = executionPayloadSourceCounter;
    this.executionBuilderModule =
        new ExecutionBuilderModule(
            spec, this, builderBidValidator, builderCircuitBreaker, builderClient, eventLogger);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    executionBuilderModule.updateBuilderAvailability();
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    return executionClientHandler.eth1GetPowBlock(blockHash);
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    return executionClientHandler.eth1GetPowChainHead();
  }

  @Override
  public SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {

    LOG.trace(
        "calling engineForkChoiceUpdated(forkChoiceState={}, payloadAttributes={})",
        forkChoiceState,
        payloadBuildingAttributes);
    return executionClientHandler.engineForkChoiceUpdated(
        forkChoiceState, payloadBuildingAttributes);
  }

  @Override
  public SafeFuture<ExecutionPayload> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    return engineGetPayload(executionPayloadContext, slot, false)
        .thenApply(ExecutionPayloadWithValue::getExecutionPayload)
        .thenPeek(__ -> recordExecutionPayloadFallbackSource(Source.LOCAL_EL, FallbackReason.NONE));
  }

  SafeFuture<ExecutionPayloadWithValue> engineGetPayloadForFallback(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    return engineGetPayload(executionPayloadContext, slot, true);
  }

  private SafeFuture<ExecutionPayloadWithValue> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext,
      final UInt64 slot,
      final boolean isFallbackCall) {
    LOG.trace(
        "calling engineGetPayload(payloadId={}, slot={})",
        executionPayloadContext.getPayloadId(),
        slot);
    if (!isFallbackCall
        && executionBuilderModule.isBuilderAvailable()
        && spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.BELLATRIX)) {
      LOG.warn("Builder endpoint is available but a non-blinded block has been requested");
    }
    return executionClientHandler.engineGetPayload(executionPayloadContext, slot);
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    LOG.trace("calling engineNewPayload(executionPayload={})", executionPayload);
    return executionClientHandler.engineNewPayload(executionPayload);
  }

  @Override
  public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      final TransitionConfiguration transitionConfiguration) {
    return executionClientHandler.engineExchangeTransitionConfiguration(transitionConfiguration);
  }

  @Override
  public SafeFuture<BlobsBundle> engineGetBlobsBundle(
      final UInt64 slot,
      final Bytes8 payloadId,
      Optional<ExecutionPayload> executionPayloadOptional) {
    LOG.trace(
        "calling engineGetBlobsBundle(slot={}, executionPayloadOptional={})",
        slot,
        executionPayloadOptional);
    return executionClientHandler
        .engineGetBlobsBundle(payloadId, slot)
        .thenApplyChecked(
            blobsBundle -> {
              blobsBundleValidator.validate(blobsBundle, executionPayloadOptional);
              return blobsBundle;
            });
  }

  @Override
  public SafeFuture<Void> builderRegisterValidators(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations, final UInt64 slot) {
    return executionBuilderModule.builderRegisterValidators(signedValidatorRegistrations, slot);
  }

  @Override
  public SafeFuture<ExecutionPayload> builderGetPayload(
      SignedBeaconBlock signedBlindedBeaconBlock,
      Function<UInt64, Optional<ExecutionPayloadResult>> getPayloadResultFunction) {
    return executionBuilderModule.builderGetPayload(
        signedBlindedBeaconBlock, getPayloadResultFunction);
  }

  @Override
  public SafeFuture<ExecutionPayloadHeader> builderGetHeader(
      ExecutionPayloadContext executionPayloadContext, BeaconState state) {
    return executionBuilderModule.builderGetHeader(executionPayloadContext, state);
  }

  @VisibleForTesting
  ExecutionBuilderModule getExecutionBuilderModule() {
    return executionBuilderModule;
  }

  void recordExecutionPayloadFallbackSource(
      final Source source, final FallbackReason fallbackReason) {
    executionPayloadSourceCounter.labels(source.toString(), fallbackReason.toString()).inc();
  }

  // Metric - execution payload "source" label values
  protected enum Source {
    LOCAL_EL("local_el"),
    BUILDER("builder"),
    BUILDER_LOCAL_EL_FALLBACK("builder_local_el_fallback");

    private final String displayName;

    Source(final String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
