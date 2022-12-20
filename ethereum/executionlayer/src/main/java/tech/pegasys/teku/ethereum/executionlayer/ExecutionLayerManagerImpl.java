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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EB_REQUESTS;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EE_REQUESTS;

import java.util.Arrays;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.bls.BLSPublicKey;
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
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
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
  private static final UInt64 FALLBACK_DATA_RETENTION_SLOTS = UInt64.valueOf(2);
  // TODO: the will be Builder API where Payload will come together with Blobs, until this pack it
  // with dummy
  private static final SafeFuture<BlobsBundle> BLOBS_BUNDLE_BUILDER_DUMMY =
      SafeFuture.completedFuture(
          new BlobsBundle(Bytes32.ZERO, Collections.emptyList(), Collections.emptyList()));

  private final NavigableMap<UInt64, ExecutionPayloadResult> executionResultCache =
      new ConcurrentSkipListMap<>();

  private final Optional<BuilderClient> builderClient;
  private final AtomicBoolean latestBuilderAvailability;
  private final Spec spec;
  private final EventLogger eventLogger;
  private final BuilderBidValidator builderBidValidator;
  private final BuilderCircuitBreaker builderCircuitBreaker;
  private final LabelledMetric<Counter> executionPayloadSourceCounter;
  private final ExecutionClientHandler executionClientHandler;
  private final BlobsBundleValidator blobsBundleValidator;

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
    if (version != Version.KILNV2 && version != Version.NO_BLOCK_VALUE) {
      throw new InvalidConfigurationException("Unsupported execution engine version: " + version);
    }
    final ExecutionEngineClient engineClient = new Web3JExecutionEngineClient(web3JClient, version);
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
    this.builderClient = builderClient;
    this.latestBuilderAvailability = new AtomicBoolean(builderClient.isPresent());
    this.spec = spec;
    this.eventLogger = eventLogger;
    this.builderBidValidator = builderBidValidator;
    this.builderCircuitBreaker = builderCircuitBreaker;
    this.executionPayloadSourceCounter = executionPayloadSourceCounter;
    this.blobsBundleValidator = blobsBundleValidator;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    updateBuilderAvailability();
    executionResultCache.headMap(slot.minusMinZero(FALLBACK_DATA_RETENTION_SLOTS), false).clear();
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
  public Optional<ExecutionPayloadResult> getPayloadResult(final UInt64 slot) {
    return Optional.ofNullable(executionResultCache.get(slot));
  }

  @Override
  public ExecutionPayloadResult initiateBlockProduction(
      final ExecutionPayloadContext context,
      final BeaconState blockSlotState,
      final boolean isBlind) {
    final ExecutionPayloadResult result;
    if (!isBlind) {
      final SafeFuture<ExecutionPayload> executionPayloadFuture =
          engineGetPayload(context, blockSlotState.getSlot());
      result =
          new ExecutionPayloadResult(
              context,
              Optional.of(executionPayloadFuture),
              Optional.empty(),
              Optional.empty(),
              Optional.empty());
    } else {
      result = builderGetHeader(context, blockSlotState);
    }
    executionResultCache.put(blockSlotState.getSlot(), result);
    return result;
  }

  @Override
  public ExecutionPayloadResult initiateBlockAndBlobsProduction(
      final ExecutionPayloadContext context,
      final BeaconState blockSlotState,
      final boolean isBlind) {
    final ExecutionPayloadResult result;
    if (!isBlind) {
      final SafeFuture<ExecutionPayload> executionPayloadFuture =
          engineGetPayload(context, blockSlotState.getSlot());
      final SafeFuture<BlobsBundle> blobsBundleFuture =
          executionPayloadFuture.thenCompose(
              executionPayload ->
                  engineGetBlobsBundle(
                      blockSlotState.getSlot(),
                      context.getPayloadId(),
                      Optional.of(executionPayload)));
      result =
          new ExecutionPayloadResult(
              context,
              Optional.of(executionPayloadFuture),
              Optional.empty(),
              Optional.empty(),
              Optional.of(blobsBundleFuture));
    } else {
      result = builderGetHeader(context, blockSlotState);
    }
    executionResultCache.put(blockSlotState.getSlot(), result);
    return result;
  }

  @Override
  public SafeFuture<ExecutionPayload> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    return engineGetPayload(executionPayloadContext, slot, false)
        .thenPeek(__ -> recordExecutionPayloadSource(Source.LOCAL_EL, FallbackReason.NONE));
  }

  public SafeFuture<ExecutionPayload> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext,
      final UInt64 slot,
      final boolean isFallbackCall) {
    LOG.trace(
        "calling engineGetPayload(payloadId={}, slot={})",
        executionPayloadContext.getPayloadId(),
        slot);
    if (!isFallbackCall
        && isBuilderAvailable()
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
    LOG.trace(
        "calling builderRegisterValidator(slot={},signedValidatorRegistrations={})",
        slot,
        signedValidatorRegistrations);

    if (!isBuilderAvailable()) {
      return SafeFuture.failedFuture(
          new RuntimeException("Unable to register validators: builder not available"));
    }

    return builderClient
        .orElseThrow()
        .registerValidators(slot, signedValidatorRegistrations)
        .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
        .thenPeek(
            __ ->
                LOG.trace(
                    "builderRegisterValidator(slot={},signedValidatorRegistrations={}) -> success",
                    slot,
                    signedValidatorRegistrations));
  }

  @Override
  public ExecutionPayloadResult builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext, final BeaconState state) {

    final UInt64 slot = state.getSlot();

    final SafeFuture<ExecutionPayload> localExecutionPayload =
        engineGetPayload(executionPayloadContext, slot, true);

    final Optional<SignedValidatorRegistration> validatorRegistration =
        executionPayloadContext.getPayloadBuildingAttributes().getValidatorRegistration();

    // fallback conditions
    final FallbackReason fallbackReason;
    if (builderClient.isEmpty() && validatorRegistration.isEmpty()) {
      fallbackReason = FallbackReason.NOT_NEEDED;
    } else if (isTransitionNotFinalized(executionPayloadContext)) {
      fallbackReason = FallbackReason.TRANSITION_NOT_FINALIZED;
    } else if (isCircuitBreakerEngaged(state)) {
      fallbackReason = FallbackReason.CIRCUIT_BREAKER_ENGAGED;
    } else if (builderClient.isEmpty()) {
      fallbackReason = FallbackReason.BUILDER_NOT_CONFIGURED;
    } else if (!isBuilderAvailable()) {
      fallbackReason = FallbackReason.BUILDER_NOT_AVAILABLE;
    } else if (validatorRegistration.isEmpty()) {
      fallbackReason = FallbackReason.VALIDATOR_NOT_REGISTERED;
    } else {
      fallbackReason = null;
    }

    if (fallbackReason != null) {
      return getResultFromLocalExecutionPayload(
          executionPayloadContext, localExecutionPayload, slot, fallbackReason);
    }

    final BLSPublicKey validatorPublicKey = validatorRegistration.get().getMessage().getPublicKey();

    LOG.trace(
        "calling builderGetHeader(slot={}, pubKey={}, parentHash={})",
        slot,
        validatorPublicKey,
        executionPayloadContext.getParentHash());

    final SafeFuture<ExecutionPayloadResult> executionPayloadResultSafeFuture =
        builderClient
            .orElseThrow()
            .getHeader(slot, validatorPublicKey, executionPayloadContext.getParentHash())
            .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
            .thenPeek(
                signedBuilderBidMaybe ->
                    LOG.trace(
                        "builderGetHeader(slot={}, pubKey={}, parentHash={}) -> {}",
                        slot,
                        validatorPublicKey,
                        executionPayloadContext.getParentHash(),
                        signedBuilderBidMaybe))
            .thenApplyChecked(
                signedBuilderBidMaybe -> {
                  if (signedBuilderBidMaybe.isEmpty()) {
                    return getResultFromLocalExecutionPayload(
                        executionPayloadContext,
                        localExecutionPayload,
                        slot,
                        FallbackReason.BUILDER_HEADER_NOT_AVAILABLE);
                  }
                  final SignedBuilderBid signedBuilderBid = signedBuilderBidMaybe.get();
                  logReceivedBuilderBid(signedBuilderBid.getMessage());
                  final ExecutionPayloadHeader executionPayloadHeader =
                      builderBidValidator.validateAndGetPayloadHeader(
                          spec, signedBuilderBid, validatorRegistration.get(), state);
                  return new ExecutionPayloadResult(
                      executionPayloadContext,
                      Optional.empty(),
                      Optional.of(SafeFuture.completedFuture(executionPayloadHeader)),
                      Optional.empty(),
                      Optional.of(BLOBS_BUNDLE_BUILDER_DUMMY));
                })
            .exceptionally(
                error -> {
                  LOG.error(
                      "Unable to obtain a valid bid from builder. Falling back to local execution engine.",
                      error);
                  return getResultFromLocalExecutionPayload(
                      executionPayloadContext,
                      localExecutionPayload,
                      slot,
                      FallbackReason.BUILDER_ERROR);
                });

    return new ExecutionPayloadResult(
        executionPayloadContext,
        Optional.empty(),
        Optional.of(
            executionPayloadResultSafeFuture.thenCompose(
                executionPayloadResult ->
                    executionPayloadResult.getExecutionPayloaHeaderdFuture().orElseThrow())),
        Optional.of(
            executionPayloadResultSafeFuture.thenCompose(
                executionPayloadResult ->
                    executionPayloadResult.getFallbackDataFuture().orElseThrow())),
        Optional.of(BLOBS_BUNDLE_BUILDER_DUMMY));
  }

  @Override
  public SafeFuture<ExecutionPayload> builderGetPayload(
      final SignedBeaconBlock signedBlindedBeaconBlock) {

    checkArgument(
        signedBlindedBeaconBlock.getMessage().getBody().isBlinded(),
        "SignedBeaconBlock must be blind");

    final UInt64 slot = signedBlindedBeaconBlock.getSlot();

    final Optional<SafeFuture<Optional<FallbackData>>> maybeProcessedSlot =
        Optional.ofNullable(executionResultCache.get(slot))
            .flatMap(ExecutionPayloadResult::getFallbackDataFuture);

    if (maybeProcessedSlot.isEmpty()) {
      LOG.warn(
          "Blinded block seems to not be built via either builder or local EL. Trying to unblind it via builder endpoint anyway.");
      return getPayloadFromBuilder(signedBlindedBeaconBlock);
    }

    final SafeFuture<Optional<FallbackData>> optionalFallbackData = maybeProcessedSlot.get();

    return getPayloadFromFallbackData(signedBlindedBeaconBlock, optionalFallbackData);
  }

  private ExecutionPayloadResult getResultFromLocalExecutionPayload(
      final ExecutionPayloadContext executionPayloadContext,
      final SafeFuture<ExecutionPayload> localExecutionPayload,
      final UInt64 slot,
      final FallbackReason reason) {

    SafeFuture<Optional<FallbackData>> fallbackDataOptional =
        localExecutionPayload.thenApply(
            executionPayload -> Optional.of(new FallbackData(executionPayload, reason)));

    SafeFuture<ExecutionPayloadHeader> header =
        localExecutionPayload.thenApply(
            executionPayload ->
                spec.atSlot(slot)
                    .getSchemaDefinitions()
                    .toVersionBellatrix()
                    .orElseThrow()
                    .getExecutionPayloadHeaderSchema()
                    .createFromExecutionPayload(executionPayload));
    return new ExecutionPayloadResult(
        executionPayloadContext,
        Optional.empty(),
        Optional.of(header),
        Optional.of(fallbackDataOptional),
        Optional.of(BLOBS_BUNDLE_BUILDER_DUMMY));
  }

  private SafeFuture<ExecutionPayload> getPayloadFromBuilder(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    LOG.trace("calling builderGetPayload(signedBlindedBeaconBlock={})", signedBlindedBeaconBlock);

    return builderClient
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Unable to get payload from builder: builder endpoint not available"))
        .getPayload(signedBlindedBeaconBlock)
        .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
        .thenPeek(
            executionPayload -> {
              logReceivedBuilderExecutionPayload(executionPayload);
              recordExecutionPayloadSource(Source.BUILDER, FallbackReason.NONE);
              LOG.trace(
                  "builderGetPayload(signedBlindedBeaconBlock={}) -> {}",
                  signedBlindedBeaconBlock,
                  executionPayload);
            });
  }

  private SafeFuture<ExecutionPayload> getPayloadFromFallbackData(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      SafeFuture<Optional<FallbackData>> optionalFallbackData) {
    // note: we don't do any particular consistency check here.
    // the header/payload compatibility check is done by SignedBeaconBlockUnblinder
    return optionalFallbackData.thenCompose(
        fallbackDataOptional -> {
          if (fallbackDataOptional.isEmpty()) {
            return getPayloadFromBuilder(signedBlindedBeaconBlock);
          } else {
            final FallbackData fallbackData = fallbackDataOptional.get();
            logFallbackToLocalExecutionPayload(fallbackData);
            recordExecutionPayloadSource(
                Source.BUILDER_LOCAL_EL_FALLBACK, fallbackData.getReason());
            return SafeFuture.completedFuture(fallbackData.getExecutionPayload());
          }
        });
  }

  boolean isBuilderAvailable() {
    return latestBuilderAvailability.get();
  }

  private void updateBuilderAvailability() {
    if (builderClient.isEmpty()) {
      return;
    }
    builderClient
        .get()
        .status()
        .finish(
            statusResponse -> {
              if (statusResponse.isFailure()) {
                markBuilderAsNotAvailable(statusResponse.getErrorMessage());
              } else {
                if (latestBuilderAvailability.compareAndSet(false, true)) {
                  eventLogger.builderIsAvailable();
                }
              }
            },
            throwable -> markBuilderAsNotAvailable(getMessageOrSimpleName(throwable)));
  }

  private boolean isTransitionNotFinalized(final ExecutionPayloadContext executionPayloadContext) {
    return executionPayloadContext.getForkChoiceState().getFinalizedExecutionBlockHash().isZero();
  }

  private boolean isCircuitBreakerEngaged(final BeaconState state) {
    try {
      return builderCircuitBreaker.isEngaged(state);
    } catch (Exception e) {
      if (ExceptionUtil.hasCause(e, InterruptedException.class)) {
        LOG.debug("Shutting down");
      } else {
        LOG.error(
            "Builder circuit breaker engagement failure at slot {}. Acting like it has been engaged.",
            state.getSlot(),
            e);
      }
      return true;
    }
  }

  private void markBuilderAsNotAvailable(final String errorMessage) {
    latestBuilderAvailability.set(false);
    eventLogger.builderIsNotAvailable(errorMessage);
  }

  private void logFallbackToLocalExecutionPayload(final FallbackData fallbackData) {
    LOG.log(
        fallbackData.getReason() == FallbackReason.NOT_NEEDED ? Level.DEBUG : Level.INFO,
        "Falling back to locally produced execution payload (Block Number {}, Block Hash = {}, Fallback Reason = {})",
        fallbackData.getExecutionPayload().getBlockNumber(),
        fallbackData.getExecutionPayload().getBlockHash(),
        fallbackData.getReason());
  }

  private void logReceivedBuilderExecutionPayload(final ExecutionPayload executionPayload) {
    LOG.info(
        "Received execution payload from Builder (Block Number {}, Block Hash = {})",
        executionPayload.getBlockNumber(),
        executionPayload.getBlockHash());
  }

  private void logReceivedBuilderBid(final BuilderBid builderBid) {
    final ExecutionPayloadHeader payloadHeader = builderBid.getExecutionPayloadHeader();
    LOG.info(
        "Received Builder Bid (Block Number = {}, Block Hash = {}, MEV Reward (wei) = {}, Gas Limit = {}, Gas Used = {})",
        payloadHeader.getBlockNumber(),
        payloadHeader.getBlockHash(),
        builderBid.getValue().toDecimalString(),
        payloadHeader.getGasLimit(),
        payloadHeader.getGasUsed());
  }

  private void recordExecutionPayloadSource(
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
