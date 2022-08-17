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
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.ethereum.executionclient.web3j.Web3JClient;
import tech.pegasys.teku.ethereum.executionclient.web3j.Web3JExecutionEngineClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
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
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class ExecutionLayerManagerImpl implements ExecutionLayerManager {

  private static final Logger LOG = LogManager.getLogger();
  private static final UInt64 FALLBACK_DATA_RETENTION_SLOTS = UInt64.valueOf(2);

  /**
   * slotToLocalElFallbackPayload usage:
   *
   * <p>if we serve builderGetHeader using local execution engine, we store slot->executionPayload
   * to be able to serve builderGetPayload later
   *
   * <p>if we serve builderGetHeader using builder, we store slot->Optional.empty() to signal that
   * we must call the builder to serve builderGetPayload later
   */
  private final NavigableMap<UInt64, Optional<FallbackData>> slotToLocalElFallbackData =
      new ConcurrentSkipListMap<>();

  private final ExecutionEngineClient executionEngineClient;
  private final Optional<BuilderClient> builderClient;
  private final AtomicBoolean latestBuilderAvailability;
  private final Spec spec;
  private final EventLogger eventLogger;
  private final BuilderBidValidator builderBidValidator;
  private final LabelledMetric<Counter> executionPayloadSourceCounter;

  public static ExecutionLayerManagerImpl create(
      final EventLogger eventLogger,
      final ExecutionEngineClient executionEngineClient,
      final Optional<BuilderClient> builderClient,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final BuilderBidValidator builderBidValidator) {

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
        executionEngineClient,
        builderClient,
        spec,
        eventLogger,
        builderBidValidator,
        executionPayloadSourceCounter);
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
      final ExecutionEngineClient executionEngineClient,
      final Optional<BuilderClient> builderClient,
      final Spec spec,
      final EventLogger eventLogger,
      final BuilderBidValidator builderBidValidator,
      final LabelledMetric<Counter> executionPayloadSourceCounter) {
    this.executionEngineClient = executionEngineClient;
    this.builderClient = builderClient;
    this.latestBuilderAvailability = new AtomicBoolean(builderClient.isPresent());
    this.spec = spec;
    this.eventLogger = eventLogger;
    this.builderBidValidator = builderBidValidator;
    this.executionPayloadSourceCounter = executionPayloadSourceCounter;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    updateBuilderAvailability();
    slotToLocalElFallbackData
        .headMap(slot.minusMinZero(FALLBACK_DATA_RETENTION_SLOTS), false)
        .clear();
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    LOG.trace("calling eth1GetPowBlock(blockHash={})", blockHash);

    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(
            powBlock -> LOG.trace("eth1GetPowBlock(blockHash={}) -> {}", blockHash, powBlock));
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    LOG.trace("calling eth1GetPowChainHead()");

    return executionEngineClient
        .getPowChainHead()
        .thenPeek(powBlock -> LOG.trace("eth1GetPowChainHead() -> {}", powBlock));
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult>
      engineForkChoiceUpdated(
          final ForkChoiceState forkChoiceState,
          final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {

    LOG.trace(
        "calling engineForkChoiceUpdated(forkChoiceState={}, payloadAttributes={})",
        forkChoiceState,
        payloadBuildingAttributes);

    return executionEngineClient
        .forkChoiceUpdated(
            ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState),
            PayloadAttributesV1.fromInternalPayloadBuildingAttributes(payloadBuildingAttributes))
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenApply(ForkChoiceUpdatedResult::asInternalExecutionPayload)
        .thenPeek(
            forkChoiceUpdatedResult ->
                LOG.trace(
                    "engineForkChoiceUpdated(forkChoiceState={}, payloadAttributes={}) -> {}",
                    forkChoiceState,
                    payloadBuildingAttributes,
                    forkChoiceUpdatedResult));
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

    return executionEngineClient
        .getPayload(executionPayloadContext.getPayloadId())
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenCombine(
            SafeFuture.of(
                () ->
                    SchemaDefinitionsBellatrix.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getExecutionPayloadSchema()),
            ExecutionPayloadV1::asInternalExecutionPayload)
        .thenPeek(
            executionPayload ->
                LOG.trace(
                    "engineGetPayload(payloadId={}, slot={}) -> {}",
                    executionPayloadContext.getPayloadId(),
                    slot,
                    executionPayload));
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    LOG.trace("calling engineNewPayload(executionPayload={})", executionPayload);

    return executionEngineClient
        .newPayload(ExecutionPayloadV1.fromInternalExecutionPayload(executionPayload))
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenApply(PayloadStatusV1::asInternalExecutionPayload)
        .thenPeek(
            payloadStatus ->
                LOG.trace(
                    "engineNewPayload(executionPayload={}) -> {}", executionPayload, payloadStatus))
        .exceptionally(PayloadStatus::failedExecution);
  }

  @Override
  public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      final TransitionConfiguration transitionConfiguration) {
    LOG.trace(
        "calling engineExchangeTransitionConfiguration(transitionConfiguration={})",
        transitionConfiguration);

    return executionEngineClient
        .exchangeTransitionConfiguration(
            TransitionConfigurationV1.fromInternalTransitionConfiguration(transitionConfiguration))
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenApply(TransitionConfigurationV1::asInternalTransitionConfiguration)
        .thenPeek(
            remoteTransitionConfiguration ->
                LOG.trace(
                    "engineExchangeTransitionConfiguration(transitionConfiguration={}) -> {}",
                    transitionConfiguration,
                    remoteTransitionConfiguration));
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
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenPeek(
            __ ->
                LOG.trace(
                    "builderRegisterValidator(slot={},signedValidatorRegistrations={}) -> success",
                    slot,
                    signedValidatorRegistrations));
  }

  @Override
  public SafeFuture<ExecutionPayloadHeader> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final boolean transitionNotFinalized) {
    final UInt64 slot = state.getSlot();

    final SafeFuture<ExecutionPayload> localExecutionPayload =
        engineGetPayload(executionPayloadContext, slot, true);

    final Optional<SignedValidatorRegistration> validatorRegistration =
        executionPayloadContext.getPayloadBuildingAttributes().getValidatorRegistration();

    // fallback conditions
    final FallbackReason fallbackReason;
    if (builderClient.isEmpty() && validatorRegistration.isEmpty()) {
      fallbackReason = FallbackReason.NOT_NEEDED;
    } else if (transitionNotFinalized) {
      fallbackReason = FallbackReason.TRANSITION_NOT_FINALIZED;
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
      return getHeaderFromLocalExecutionPayload(localExecutionPayload, slot, fallbackReason);
    }

    final BLSPublicKey validatorPublicKey = validatorRegistration.get().getMessage().getPublicKey();

    LOG.trace(
        "calling builderGetHeader(slot={}, pubKey={}, parentHash={})",
        slot,
        validatorPublicKey,
        executionPayloadContext.getParentHash());

    return builderClient
        .orElseThrow()
        .getHeader(slot, validatorPublicKey, executionPayloadContext.getParentHash())
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
        .thenPeek(
            signedBuilderBidMaybe ->
                LOG.trace(
                    "builderGetHeader(slot={}, pubKey={}, parentHash={}) -> {}",
                    slot,
                    validatorPublicKey,
                    executionPayloadContext.getParentHash(),
                    signedBuilderBidMaybe))
        .thenComposeChecked(
            signedBuilderBidMaybe -> {
              if (signedBuilderBidMaybe.isEmpty()) {
                return getHeaderFromLocalExecutionPayload(
                    localExecutionPayload, slot, FallbackReason.BUILDER_HEADER_NOT_AVAILABLE);
              }
              final SignedBuilderBid signedBuilderBid = signedBuilderBidMaybe.get();
              logReceivedBuilderBid(signedBuilderBid.getMessage());
              final ExecutionPayloadHeader executionPayloadHeader =
                  builderBidValidator.validateAndGetPayloadHeader(
                      spec, signedBuilderBid, validatorRegistration.get(), state);
              slotToLocalElFallbackData.put(slot, Optional.empty());
              return SafeFuture.completedFuture(executionPayloadHeader);
            })
        .exceptionallyCompose(
            error -> {
              LOG.error(
                  "Unable to obtain a valid bid from builder. Falling back to local execution engine.",
                  error);
              return getHeaderFromLocalExecutionPayload(
                  localExecutionPayload, slot, FallbackReason.BUILDER_ERROR);
            });
  }

  @Override
  public SafeFuture<ExecutionPayload> builderGetPayload(
      final SignedBeaconBlock signedBlindedBeaconBlock) {

    checkArgument(
        signedBlindedBeaconBlock.getMessage().getBody().isBlinded(),
        "SignedBeaconBlock must be blind");

    final UInt64 slot = signedBlindedBeaconBlock.getSlot();

    final Optional<Optional<FallbackData>> maybeProcessedSlot =
        Optional.ofNullable(slotToLocalElFallbackData.get(slot));

    if (maybeProcessedSlot.isEmpty()) {
      LOG.warn(
          "Blinded block seems to not be built via either builder or local EL. Trying to unblind it via builder endpoint anyway.");
      return getPayloadFromBuilder(signedBlindedBeaconBlock);
    }

    final Optional<FallbackData> maybeLocalElFallbackData = maybeProcessedSlot.get();

    if (maybeLocalElFallbackData.isEmpty()) {
      return getPayloadFromBuilder(signedBlindedBeaconBlock);
    }

    return getPayloadFromFallbackData(maybeLocalElFallbackData.get());
  }

  private SafeFuture<ExecutionPayloadHeader> getHeaderFromLocalExecutionPayload(
      final SafeFuture<ExecutionPayload> localExecutionPayload,
      final UInt64 slot,
      final FallbackReason reason) {

    return localExecutionPayload.thenApply(
        executionPayload -> {
          // store the fallback payload for this slot
          slotToLocalElFallbackData.put(
              slot, Optional.of(new FallbackData(executionPayload, reason)));

          return spec.atSlot(slot)
              .getSchemaDefinitions()
              .toVersionBellatrix()
              .orElseThrow()
              .getExecutionPayloadHeaderSchema()
              .createFromExecutionPayload(executionPayload);
        });
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
        .thenApply(ExecutionLayerManagerImpl::unwrapResponseOrThrow)
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

  private SafeFuture<ExecutionPayload> getPayloadFromFallbackData(final FallbackData fallbackData) {
    // note: we don't do any particular consistency check here.
    // the header/payload compatibility check is done by SignedBeaconBlockUnblinder

    logFallbackToLocalExecutionPayload(fallbackData);
    recordExecutionPayloadSource(Source.BUILDER_LOCAL_EL_FALLBACK, fallbackData.reason);

    return SafeFuture.completedFuture(fallbackData.executionPayload);
  }

  boolean isBuilderAvailable() {
    return latestBuilderAvailability.get();
  }

  private static <K> K unwrapResponseOrThrow(final Response<K> response) {
    checkArgument(response.isSuccess(), "Invalid remote response: %s", response.getErrorMessage());
    return response.getPayload();
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
                  eventLogger.builderIsBackOnline();
                }
              }
            },
            throwable -> markBuilderAsNotAvailable(getMessageOrSimpleName(throwable)));
  }

  private void markBuilderAsNotAvailable(final String errorMessage) {
    latestBuilderAvailability.set(false);
    eventLogger.builderIsOffline(errorMessage);
  }

  private void logFallbackToLocalExecutionPayload(final FallbackData fallbackData) {
    LOG.log(
        fallbackData.reason == FallbackReason.NOT_NEEDED ? Level.DEBUG : Level.INFO,
        "Falling back to locally produced execution payload (Block Number {}, Block Hash = {}, Fallback Reason = {})",
        fallbackData.executionPayload.getBlockNumber(),
        fallbackData.executionPayload.getBlockHash(),
        fallbackData.reason);
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

  private static class FallbackData {
    final ExecutionPayload executionPayload;
    final FallbackReason reason;

    public FallbackData(final ExecutionPayload executionPayload, final FallbackReason reason) {
      this.executionPayload = executionPayload;
      this.reason = reason;
    }
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

  // Metric - fallback "reason" label values
  protected enum FallbackReason {
    NOT_NEEDED("not_needed"),
    VALIDATOR_NOT_REGISTERED("validator_not_registered"),
    TRANSITION_NOT_FINALIZED("transition_not_finalized"),
    BUILDER_NOT_AVAILABLE("builder_not_available"),
    BUILDER_NOT_CONFIGURED("builder_not_configured"),
    BUILDER_HEADER_NOT_AVAILABLE("builder_header_not_available"),
    BUILDER_ERROR("builder_error"),
    NONE("");

    private final String displayName;

    FallbackReason(final String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
