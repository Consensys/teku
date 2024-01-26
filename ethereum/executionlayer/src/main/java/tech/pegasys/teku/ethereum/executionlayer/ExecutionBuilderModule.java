/*
 * Copyright Consensys Software Inc., 2022
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

import static tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl.Source;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;
import static tech.pegasys.teku.infrastructure.logging.Converter.weiToEth;

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class ExecutionBuilderModule {

  private static final Logger LOG = LogManager.getLogger();
  private static final int HUNDRED_PERCENT = 100;

  private static final UInt64 VC_BUILDER_BOOST_FACTOR_PREFER_EXECUTION = UInt64.ZERO;
  private static final UInt64 VC_BUILDER_BOOST_FACTOR_PREFER_BUILDER = UInt64.MAX_VALUE;

  private final Spec spec;
  private final AtomicBoolean latestBuilderAvailability;
  private final ExecutionLayerManagerImpl executionLayerManager;
  private final BuilderBidValidator builderBidValidator;
  private final BuilderCircuitBreaker builderCircuitBreaker;
  private final Optional<BuilderClient> builderClient;
  private final EventLogger eventLogger;
  private final Optional<Integer> builderBidCompareFactor;
  private final boolean useShouldOverrideBuilderFlag;

  public ExecutionBuilderModule(
      final Spec spec,
      final ExecutionLayerManagerImpl executionLayerManager,
      final BuilderBidValidator builderBidValidator,
      final BuilderCircuitBreaker builderCircuitBreaker,
      final Optional<BuilderClient> builderClient,
      final EventLogger eventLogger,
      final Optional<Integer> builderBidCompareFactor,
      final boolean useShouldOverrideBuilderFlag) {
    this.spec = spec;
    this.latestBuilderAvailability = new AtomicBoolean(builderClient.isPresent());
    this.executionLayerManager = executionLayerManager;
    this.builderBidValidator = builderBidValidator;
    this.builderCircuitBreaker = builderCircuitBreaker;
    this.builderClient = builderClient;
    this.eventLogger = eventLogger;
    this.builderBidCompareFactor = builderBidCompareFactor;
    this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
  }

  private Optional<SafeFuture<HeaderWithFallbackData>> validateBuilderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> localGetPayloadResponse,
      final SafeFuture<UInt256> payloadValueResult) {
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
      return Optional.of(
          getResultFromLocalGetPayloadResponse(
              localGetPayloadResponse, state.getSlot(), fallbackReason, payloadValueResult));
    }

    return Optional.empty();
  }

  public SafeFuture<HeaderWithFallbackData> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final SafeFuture<UInt256> payloadValueResult,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {

    final SafeFuture<GetPayloadResponse> localGetPayloadResponse =
        executionLayerManager
            .engineGetPayloadForFallback(executionPayloadContext, state.getSlot())
            .thenPeek(__ -> blockProductionPerformance.engineGetPayload());

    final Optional<SafeFuture<HeaderWithFallbackData>> validationResult =
        validateBuilderGetHeader(
            executionPayloadContext, state, localGetPayloadResponse, payloadValueResult);
    if (validationResult.isPresent()) {
      return validationResult.get();
    }

    final UInt64 slot = state.getSlot();

    final Optional<SignedValidatorRegistration> validatorRegistration =
        executionPayloadContext.getPayloadBuildingAttributes().getValidatorRegistration();

    final BLSPublicKey validatorPublicKey =
        validatorRegistration.orElseThrow().getMessage().getPublicKey();

    LOG.trace(
        "calling builderGetHeader(slot={}, pubKey={}, parentHash={})",
        slot,
        validatorPublicKey,
        executionPayloadContext.getParentHash());

    // Ensures we can still propose a builder block even if getPayload fails
    final SafeFuture<Optional<GetPayloadResponse>> safeLocalGetPayloadResponse =
        localGetPayloadResponse.thenApply(Optional::of).exceptionally(__ -> Optional.empty());

    return builderClient
        .orElseThrow()
        .getHeader(slot, validatorPublicKey, executionPayloadContext.getParentHash())
        .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
        .thenPeek(
            signedBuilderBidMaybe -> {
              blockProductionPerformance.builderGetHeader();
              LOG.trace(
                  "builderGetHeader(slot={}, pubKey={}, parentHash={}) -> {}",
                  slot,
                  validatorPublicKey,
                  executionPayloadContext.getParentHash(),
                  signedBuilderBidMaybe);
            })
        .thenComposeCombined(
            safeLocalGetPayloadResponse,
            (signedBuilderBidMaybe, maybeLocalGetPayloadResponse) -> {
              if (signedBuilderBidMaybe.isEmpty()) {
                return getResultFromLocalGetPayloadResponse(
                    localGetPayloadResponse,
                    slot,
                    FallbackReason.BUILDER_HEADER_NOT_AVAILABLE,
                    payloadValueResult);
              } else {
                // Treat the shouldOverrideBuilder flag as false if local payload is unavailable
                final boolean shouldOverrideBuilder =
                    maybeLocalGetPayloadResponse
                        .map(GetPayloadResponse::getShouldOverrideBuilder)
                        .orElse(false);
                if (useShouldOverrideBuilderFlag && shouldOverrideBuilder) {
                  return getResultFromLocalGetPayloadResponse(
                      localGetPayloadResponse,
                      slot,
                      FallbackReason.SHOULD_OVERRIDE_BUILDER_FLAG_IS_TRUE,
                      payloadValueResult);
                }

                final SignedBuilderBid signedBuilderBid = signedBuilderBidMaybe.get();
                // Treat the local block value as zero if local payload is unavailable
                final UInt256 localBlockValue =
                    maybeLocalGetPayloadResponse
                        .map(GetPayloadResponse::getExecutionPayloadValue)
                        .orElse(UInt256.ZERO);
                final UInt256 builderBidValue = signedBuilderBid.getMessage().getValue();

                logReceivedBuilderBid(signedBuilderBid.getMessage());

                if (isLocalPayloadValueWinning(
                    builderBidValue, localBlockValue, requestedBuilderBoostFactor)) {
                  logLocalPayloadWin(builderBidValue, localBlockValue, requestedBuilderBoostFactor);
                  return getResultFromLocalGetPayloadResponse(
                      localGetPayloadResponse,
                      slot,
                      FallbackReason.LOCAL_BLOCK_VALUE_WON,
                      payloadValueResult);
                }

                final Optional<ExecutionPayload> localExecutionPayload =
                    maybeLocalGetPayloadResponse.map(GetPayloadResponse::getExecutionPayload);
                return getResultFromSignedBuilderBid(
                    signedBuilderBidMaybe.get(),
                    state,
                    validatorRegistration.get(),
                    localExecutionPayload,
                    payloadValueResult,
                    blockProductionPerformance);
              }
            })
        .exceptionallyCompose(
            error -> {
              LOG.error(
                  "Unable to obtain a valid bid from builder. Falling back to local execution engine.",
                  error);
              return getResultFromLocalGetPayloadResponse(
                  localGetPayloadResponse, slot, FallbackReason.BUILDER_ERROR, payloadValueResult);
            });
  }

  /** 1 ETH is 10^18 wei, Uint256 max is more than 10^77 */
  private boolean isLocalPayloadValueWinning(
      final UInt256 builderBidValue,
      final UInt256 localPayloadValue,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    if (requestedBuilderBoostFactor.isPresent()) {
      final UInt64 builderBoostFactor = requestedBuilderBoostFactor.get();

      LOG.debug("Using requestedBuilderBoostFactor " + builderBoostFactor);

      if (builderBoostFactor.equals(VC_BUILDER_BOOST_FACTOR_PREFER_EXECUTION)) {
        return true;
      }

      if (builderBoostFactor.equals(VC_BUILDER_BOOST_FACTOR_PREFER_BUILDER)) {
        return false;
      }

      return builderBidValue
          .multiply(UInt256.valueOf(builderBoostFactor.longValue()))
          .lessOrEqualThan(localPayloadValue.multiply(HUNDRED_PERCENT));
    }

    return builderBidCompareFactor.isPresent()
        && builderBidValue
            .multiply(builderBidCompareFactor.get())
            .lessOrEqualThan(localPayloadValue.multiply(HUNDRED_PERCENT));
  }

  private SafeFuture<HeaderWithFallbackData> getResultFromSignedBuilderBid(
      final SignedBuilderBid signedBuilderBid,
      final BeaconState state,
      final SignedValidatorRegistration validatorRegistration,
      final Optional<ExecutionPayload> localExecutionPayload,
      final SafeFuture<UInt256> payloadValueResult,
      final BlockProductionPerformance blockProductionPerformance) {
    builderBidValidator.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, localExecutionPayload);
    blockProductionPerformance.builderBidValidated();
    final BuilderBid builderBid = signedBuilderBid.getMessage();
    payloadValueResult.complete(builderBid.getValue());
    return SafeFuture.completedFuture(
        HeaderWithFallbackData.create(
            builderBid.getHeader(), builderBid.getOptionalBlobKzgCommitments()));
  }

  public SafeFuture<Void> builderRegisterValidators(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations, final UInt64 slot) {
    LOG.trace(
        "calling builderRegisterValidator(slot={},signedValidatorRegistrations={})",
        slot,
        signedValidatorRegistrations);

    if (!isBuilderAvailable()) {
      final String reason;
      if (builderClient.isEmpty()) {
        reason = "builder not configured";
      } else {
        reason = "builder not available";
      }
      return SafeFuture.failedFuture(
          new RuntimeException("Unable to register validators: " + reason));
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

  public SafeFuture<BuilderPayload> builderGetPayload(
      final SignedBeaconBlock signedBeaconBlock,
      final Function<UInt64, Optional<ExecutionPayloadResult>> getPayloadResultFunction) {

    Preconditions.checkArgument(signedBeaconBlock.isBlinded(), "SignedBeaconBlock must be blind");

    final UInt64 slot = signedBeaconBlock.getSlot();

    final Optional<SafeFuture<HeaderWithFallbackData>> maybeProcessedSlot =
        getPayloadResultFunction
            .apply(slot)
            .flatMap(ExecutionPayloadResult::getHeaderWithFallbackDataFuture);

    if (maybeProcessedSlot.isEmpty()) {
      LOG.warn(
          "Blinded block seems to not be built via either builder or local EL. Trying to unblind it via builder endpoint anyway.");
      return getPayloadFromBuilder(signedBeaconBlock);
    }

    final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture =
        maybeProcessedSlot.get();

    return getPayloadFromBuilderOrFallbackData(signedBeaconBlock, headerWithFallbackDataFuture);
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

  private SafeFuture<HeaderWithFallbackData> getResultFromLocalGetPayloadResponse(
      final SafeFuture<GetPayloadResponse> localGetPayloadResponse,
      final UInt64 slot,
      final FallbackReason reason,
      final SafeFuture<UInt256> payloadValueResult) {
    return localGetPayloadResponse.thenApply(
        getPayloadResponse -> {
          payloadValueResult.complete(getPayloadResponse.getExecutionPayloadValue());
          final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
          final ExecutionPayload executionPayload = getPayloadResponse.getExecutionPayload();
          final ExecutionPayloadHeader executionPayloadHeader =
              schemaDefinitions
                  .toVersionBellatrix()
                  .orElseThrow()
                  .getExecutionPayloadHeaderSchema()
                  .createFromExecutionPayload(executionPayload);
          final Optional<SszList<SszKZGCommitment>> blobKzgCommitments =
              getPayloadResponse
                  .getBlobsBundle()
                  .map(
                      blobsBundle ->
                          SchemaDefinitionsDeneb.required(schemaDefinitions)
                              .getBlobKzgCommitmentsSchema()
                              .createFromBlobsBundle(blobsBundle));
          final FallbackData fallbackData =
              new FallbackData(executionPayload, getPayloadResponse.getBlobsBundle(), reason);
          return HeaderWithFallbackData.create(
              executionPayloadHeader, blobKzgCommitments, fallbackData);
        });
  }

  private SafeFuture<BuilderPayload> getPayloadFromBuilder(
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
            builderPayload -> {
              final ExecutionPayload executionPayload = builderPayload.getExecutionPayload();
              logReceivedBuilderExecutionPayload(executionPayload);
              builderPayload
                  .getOptionalBlobsBundle()
                  .ifPresent(this::logReceivedBuilderBlobsBundle);
              executionLayerManager.recordExecutionPayloadFallbackSource(
                  Source.BUILDER, FallbackReason.NONE);
              LOG.trace(
                  "builderGetPayload(signedBlindedBeaconBlock={}) -> {}",
                  signedBlindedBeaconBlock,
                  builderPayload);
            });
  }

  private SafeFuture<BuilderPayload> getPayloadFromBuilderOrFallbackData(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture) {
    // note: we don't do any particular consistency check here.
    // the header/payload compatibility check is done by SignedBeaconBlockUnblinder
    // the blobs bundle compatibility check is done by
    // BlockOperationSelectorFactory#createBlobSidecarsSelector
    return headerWithFallbackDataFuture.thenCompose(
        headerWithFallbackData -> {
          if (headerWithFallbackData.getFallbackData().isEmpty()) {
            return getPayloadFromBuilder(signedBlindedBeaconBlock);
          } else {
            final FallbackData fallbackData = headerWithFallbackData.getFallbackData().get();
            logFallbackToLocalExecutionPayloadAndBlobsBundle(fallbackData);
            executionLayerManager.recordExecutionPayloadFallbackSource(
                Source.BUILDER_LOCAL_EL_FALLBACK, fallbackData.getReason());
            final BuilderPayload builderPayload =
                fallbackData
                    .getBlobsBundle()
                    .map(
                        executionBlobsBundle -> {
                          final SchemaDefinitionsDeneb schemaDefinitions =
                              SchemaDefinitionsDeneb.required(
                                  spec.atSlot(signedBlindedBeaconBlock.getSlot())
                                      .getSchemaDefinitions());
                          final BlobsBundle blobsBundle =
                              schemaDefinitions
                                  .getBlobsBundleSchema()
                                  .createFromExecutionBlobsBundle(executionBlobsBundle);
                          return (BuilderPayload)
                              schemaDefinitions
                                  .getExecutionPayloadAndBlobsBundleSchema()
                                  .create(fallbackData.getExecutionPayload(), blobsBundle);
                        })
                    .orElseGet(fallbackData::getExecutionPayload);
            return SafeFuture.completedFuture(builderPayload);
          }
        });
  }

  boolean isBuilderAvailable() {
    return latestBuilderAvailability.get();
  }

  void updateBuilderAvailability() {
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

  private void markBuilderAsNotAvailable(final String errorMessage) {
    latestBuilderAvailability.set(false);
    eventLogger.builderIsNotAvailable(errorMessage);
  }

  private void logFallbackToLocalExecutionPayloadAndBlobsBundle(final FallbackData fallbackData) {
    final Level logLevel =
        fallbackData.getReason() == FallbackReason.NOT_NEEDED ? Level.DEBUG : Level.INFO;
    final ExecutionPayload executionPayload = fallbackData.getExecutionPayload();
    fallbackData
        .getBlobsBundle()
        .ifPresentOrElse(
            blobsBundle ->
                LOG.log(
                    logLevel,
                    "Falling back to locally produced execution payload and blobs bundle (Block Number {}, Block Hash = {}, Blobs = {}, Fallback Reason = {})",
                    executionPayload.getBlockNumber(),
                    executionPayload.getBlockHash(),
                    blobsBundle.getNumberOfBlobs(),
                    fallbackData.getReason()),
            () ->
                LOG.log(
                    logLevel,
                    "Falling back to locally produced execution payload (Block Number {}, Block Hash = {}, Fallback Reason = {})",
                    executionPayload.getBlockNumber(),
                    executionPayload.getBlockHash(),
                    fallbackData.getReason()));
  }

  private void logReceivedBuilderExecutionPayload(final ExecutionPayload executionPayload) {
    LOG.info(
        "Received execution payload from Builder (Block Number = {}, Block Hash = {})",
        executionPayload.getBlockNumber(),
        executionPayload.getBlockHash());
  }

  private void logReceivedBuilderBlobsBundle(final BlobsBundle blobsBundle) {
    LOG.info(
        "Received blobs bundle from Builder (Blobs count = {})", blobsBundle.getNumberOfBlobs());
  }

  private void logReceivedBuilderBid(final BuilderBid builderBid) {
    final ExecutionPayloadHeader payloadHeader = builderBid.getHeader();
    final String blobsLog =
        builderBid
            .getOptionalBlobKzgCommitments()
            .map(blobKzgCommitments -> ", Blobs count = " + blobKzgCommitments.size())
            .orElse("");
    LOG.info(
        "Received Builder Bid (Block Number = {}, Block Hash = {}, MEV Reward (ETH) = {}, Gas Limit = {}, Gas Used = {}{})",
        payloadHeader.getBlockNumber(),
        payloadHeader.getBlockHash(),
        weiToEth(builderBid.getValue()),
        payloadHeader.getGasLimit(),
        payloadHeader.getGasUsed(),
        blobsLog);
  }

  private void logLocalPayloadWin(
      final UInt256 builderBidValue,
      final UInt256 localPayloadValue,
      final Optional<UInt64> requestedBuilderBoostFactor) {
    final boolean isDefaultComparison;
    final Optional<?> actualComparisonFactor;
    final String comparisonFactorSource;

    if (requestedBuilderBoostFactor.isPresent()) {
      // If the requestedBuilderBoostFactor is set,
      // we always use it over builderBidCompareFactor to determine whether the local payload wins
      isDefaultComparison =
          requestedBuilderBoostFactor.get().equals(UInt64.valueOf(HUNDRED_PERCENT));
      actualComparisonFactor = requestedBuilderBoostFactor;
      comparisonFactorSource = "VC";
    } else if (builderBidCompareFactor.isPresent()) {
      isDefaultComparison = builderBidCompareFactor.get() == HUNDRED_PERCENT;
      actualComparisonFactor = builderBidCompareFactor;
      comparisonFactorSource = "BN";
    } else {
      isDefaultComparison = true;
      actualComparisonFactor = Optional.empty();
      comparisonFactorSource = "DEFAULT";
    }

    if (isDefaultComparison) {
      LOG.info(
          "Local execution payload ({} ETH) is chosen over builder bid ({} ETH, compare factor MAX_PROFIT, compare factor source {}).",
          weiToEth(localPayloadValue),
          weiToEth(builderBidValue),
          comparisonFactorSource);
    } else {
      LOG.info(
          "Local execution payload ({} ETH) is chosen over builder bid ({} ETH, compare factor {}%, compare factor source {}).",
          weiToEth(localPayloadValue),
          weiToEth(builderBidValue),
          actualComparisonFactor.get(),
          comparisonFactorSource);
    }
  }
}
