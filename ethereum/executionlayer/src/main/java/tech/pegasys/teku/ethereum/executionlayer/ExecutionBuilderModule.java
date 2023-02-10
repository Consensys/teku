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
import static tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl.Source;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
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
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ExecutionBuilderModule {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AtomicBoolean latestBuilderAvailability;
  private final ExecutionLayerManagerImpl executionLayerManager;
  private final BuilderBidValidator builderBidValidator;
  private final BuilderCircuitBreaker builderCircuitBreaker;
  private final Optional<BuilderClient> builderClient;
  private final EventLogger eventLogger;

  public ExecutionBuilderModule(
      final Spec spec,
      final ExecutionLayerManagerImpl executionLayerManager,
      final BuilderBidValidator builderBidValidator,
      final BuilderCircuitBreaker builderCircuitBreaker,
      final Optional<BuilderClient> builderClient,
      final EventLogger eventLogger) {
    this.spec = spec;
    this.latestBuilderAvailability = new AtomicBoolean(builderClient.isPresent());
    this.executionLayerManager = executionLayerManager;
    this.builderBidValidator = builderBidValidator;
    this.builderCircuitBreaker = builderCircuitBreaker;
    this.builderClient = builderClient;
    this.eventLogger = eventLogger;
  }

  private Optional<SafeFuture<HeaderWithFallbackData>> validateBuilderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final SafeFuture<ExecutionPayloadWithValue> localExecutionPayload) {
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
          getResultFromLocalExecutionPayload(
              localExecutionPayload, state.getSlot(), fallbackReason));
    }

    return Optional.empty();
  }

  public SafeFuture<HeaderWithFallbackData> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext, final BeaconState state) {

    final SafeFuture<ExecutionPayloadWithValue> localExecutionPayload =
        executionLayerManager.engineGetPayloadForFallback(executionPayloadContext, state.getSlot());
    final Optional<SafeFuture<HeaderWithFallbackData>> validationResult =
        validateBuilderGetHeader(executionPayloadContext, state, localExecutionPayload);
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
    final SafeFuture<Optional<ExecutionPayloadWithValue>> safeLocalExecutionPayload =
        localExecutionPayload.thenApply(Optional::of).exceptionally(__ -> Optional.empty());

    return builderClient
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
        .thenComposeCombined(
            safeLocalExecutionPayload,
            (signedBuilderBidMaybe, maybeLocalExecutionPayload) -> {
              if (signedBuilderBidMaybe.isEmpty()) {
                return getResultFromLocalExecutionPayload(
                    localExecutionPayload, slot, FallbackReason.BUILDER_HEADER_NOT_AVAILABLE);
              } else {
                final SignedBuilderBid signedBuilderBid = signedBuilderBidMaybe.get();
                // Treat the local block value as zero if local payload is unavailable
                final UInt256 localPayloadValue =
                    maybeLocalExecutionPayload
                        .map(ExecutionPayloadWithValue::getValue)
                        .orElse(UInt256.ZERO);
                logReceivedBuilderBid(signedBuilderBid.getMessage());
                if (signedBuilderBid.getMessage().getValue().lessOrEqualThan(localPayloadValue)) {
                  return getResultFromLocalExecutionPayload(
                      localExecutionPayload, slot, FallbackReason.LOCAL_BLOCK_VALUE_HIGHER);
                }
                final Optional<ExecutionPayload> localPayload =
                    maybeLocalExecutionPayload.map(ExecutionPayloadWithValue::getExecutionPayload);
                return getResultFromSignedBuilderBid(
                    signedBuilderBidMaybe.get(), state, validatorRegistration.get(), localPayload);
              }
            })
        .exceptionallyCompose(
            error -> {
              LOG.error(
                  "Unable to obtain a valid bid from builder. Falling back to local execution engine.",
                  error);
              return getResultFromLocalExecutionPayload(
                  localExecutionPayload, slot, FallbackReason.BUILDER_ERROR);
            });
  }

  private SafeFuture<HeaderWithFallbackData> getResultFromSignedBuilderBid(
      final SignedBuilderBid signedBuilderBid,
      final BeaconState state,
      final SignedValidatorRegistration validatorRegistration,
      final Optional<ExecutionPayload> localExecutionPayload) {
    final ExecutionPayloadHeader executionPayloadHeader =
        builderBidValidator.validateAndGetPayloadHeader(
            spec, signedBuilderBid, validatorRegistration, state, localExecutionPayload);
    return SafeFuture.completedFuture(HeaderWithFallbackData.create(executionPayloadHeader));
  }

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

  public SafeFuture<ExecutionPayload> builderGetPayload(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      final Function<UInt64, Optional<ExecutionPayloadResult>> getPayloadResultFunction) {

    checkArgument(
        signedBlindedBeaconBlock.getMessage().getBody().isBlinded(),
        "SignedBeaconBlock must be blind");

    final UInt64 slot = signedBlindedBeaconBlock.getSlot();

    final Optional<SafeFuture<HeaderWithFallbackData>> maybeProcessedSlot =
        getPayloadResultFunction
            .apply(slot)
            .flatMap(ExecutionPayloadResult::getExecutionPayloadHeaderFuture);

    if (maybeProcessedSlot.isEmpty()) {
      LOG.warn(
          "Blinded block seems to not be built via either builder or local EL. Trying to unblind it via builder endpoint anyway.");
      return getPayloadFromBuilder(signedBlindedBeaconBlock);
    }

    final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture =
        maybeProcessedSlot.get();

    return getPayloadFromFallbackData(signedBlindedBeaconBlock, headerWithFallbackDataFuture);
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

  private SafeFuture<HeaderWithFallbackData> getResultFromLocalExecutionPayload(
      final SafeFuture<ExecutionPayloadWithValue> localExecutionPayload,
      final UInt64 slot,
      final FallbackReason reason) {
    return localExecutionPayload.thenApply(
        executionPayload -> {
          ExecutionPayloadHeader executionPayloadHeader =
              spec.atSlot(slot)
                  .getSchemaDefinitions()
                  .toVersionBellatrix()
                  .orElseThrow()
                  .getExecutionPayloadHeaderSchema()
                  .createFromExecutionPayload(executionPayload.getExecutionPayload());
          return HeaderWithFallbackData.create(
              executionPayloadHeader,
              new FallbackData(executionPayload.getExecutionPayload(), reason));
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
        .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
        .thenPeek(
            executionPayload -> {
              logReceivedBuilderExecutionPayload(executionPayload);
              executionLayerManager.recordExecutionPayloadFallbackSource(
                  Source.BUILDER, FallbackReason.NONE);
              LOG.trace(
                  "builderGetPayload(signedBlindedBeaconBlock={}) -> {}",
                  signedBlindedBeaconBlock,
                  executionPayload);
            });
  }

  private SafeFuture<ExecutionPayload> getPayloadFromFallbackData(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      final SafeFuture<HeaderWithFallbackData> headerWithFallbackDataFuture) {
    // note: we don't do any particular consistency check here.
    // the header/payload compatibility check is done by SignedBeaconBlockUnblinder
    return headerWithFallbackDataFuture.thenCompose(
        headerWithFallbackData -> {
          if (headerWithFallbackData.getFallbackDataOptional().isEmpty()) {
            return getPayloadFromBuilder(signedBlindedBeaconBlock);
          } else {
            final FallbackData fallbackData =
                headerWithFallbackData.getFallbackDataOptional().get();
            logFallbackToLocalExecutionPayload(fallbackData);
            executionLayerManager.recordExecutionPayloadFallbackSource(
                Source.BUILDER_LOCAL_EL_FALLBACK, fallbackData.getReason());
            return SafeFuture.completedFuture(fallbackData.getExecutionPayload());
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
}
