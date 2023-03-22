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

package tech.pegasys.teku.statetransition.forkchoice;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class ForkChoiceUpdateData {
  static final UInt64 RESEND_AFTER_MILLIS = UInt64.valueOf(30_000);
  private static final Logger LOG = LogManager.getLogger();
  private static final ForkChoiceState DEFAULT_FORK_CHOICE_STATE =
      new ForkChoiceState(
          Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO, false);

  private final ForkChoiceState forkChoiceState;
  private final Optional<PayloadBuildingAttributes> payloadBuildingAttributes;
  private final Optional<Bytes32> terminalBlockHash;
  private final SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContext =
      new SafeFuture<>();
  private UInt64 toBeSentAtTime = UInt64.ZERO;

  private long payloadBuildingAttributesSequenceProducer = 0;
  private long payloadBuildingAttributesSequenceConsumer = -1;

  public ForkChoiceUpdateData() {
    this.forkChoiceState = DEFAULT_FORK_CHOICE_STATE;
    this.payloadBuildingAttributes = Optional.empty();
    this.terminalBlockHash = Optional.empty();
  }

  public ForkChoiceUpdateData(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes,
      final Optional<Bytes32> terminalBlockHash) {
    if (terminalBlockHash.isPresent() && forkChoiceState.getHeadExecutionBlockHash().isZero()) {
      this.forkChoiceState =
          new ForkChoiceState(
              forkChoiceState.getHeadBlockRoot(),
              forkChoiceState.getHeadBlockSlot(),
              terminalBlockHash.get(),
              Bytes32.ZERO,
              Bytes32.ZERO,
              false);
    } else {
      this.forkChoiceState = forkChoiceState;
    }
    this.payloadBuildingAttributes = payloadBuildingAttributes;
    this.terminalBlockHash = terminalBlockHash;
  }

  public ForkChoiceUpdateData withForkChoiceState(final ForkChoiceState forkChoiceState) {
    if (this.forkChoiceState.equals(forkChoiceState)) {
      return this;
    }
    return new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), terminalBlockHash);
  }

  public ForkChoiceUpdateData withPayloadBuildingAttributes(
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    if (this.payloadBuildingAttributes.equals(payloadBuildingAttributes)) {
      return this;
    }
    return new ForkChoiceUpdateData(forkChoiceState, payloadBuildingAttributes, terminalBlockHash);
  }

  public ForkChoiceUpdateData withTerminalBlockHash(final Bytes32 terminalBlockHash) {
    if (this.terminalBlockHash.isPresent()
        && this.terminalBlockHash.get().equals(terminalBlockHash)) {
      return this;
    }
    return new ForkChoiceUpdateData(
        forkChoiceState, payloadBuildingAttributes, Optional.of(terminalBlockHash));
  }

  public SafeFuture<Optional<ForkChoiceUpdateData>> withPayloadBuildingAttributesAsync(
      final Supplier<SafeFuture<Optional<PayloadBuildingAttributes>>> payloadAttributesCalculator,
      final Executor executor) {
    // we want to preserve ordering in payload calculation,
    // so we first generate a sequence for each calculation request
    final long sequenceNumber = payloadBuildingAttributesSequenceProducer++;

    return payloadAttributesCalculator
        .get()
        .thenApplyAsync(
            newPayloadBuildingAttributes -> {
              // to preserve ordering we make sure we haven't already calculated a payload that has
              // been requested later than the current one
              if (sequenceNumber <= payloadBuildingAttributesSequenceConsumer) {
                LOG.debug(
                    "Ignoring calculated payload building attributes since it violates ordering");
                return Optional.empty();
              }
              payloadBuildingAttributesSequenceConsumer = sequenceNumber;
              return Optional.of(this.withPayloadBuildingAttributes(newPayloadBuildingAttributes));
            },
            executor);
  }

  public boolean isPayloadIdSuitable(final Bytes32 parentExecutionHash, final UInt64 timestamp) {
    if (payloadBuildingAttributes.isEmpty()) {
      LOG.debug("isPayloadIdSuitable - payloadAttributes.isEmpty, returning false");
      // EL is not building a block
      return false;
    }

    final PayloadBuildingAttributes attributes = this.payloadBuildingAttributes.get();
    if (!attributes.getTimestamp().equals(timestamp)) {
      LOG.debug("isPayloadIdSuitable - wrong timestamp, returning false");
      // EL building a block with wrong timestamp
      return false;
    }

    // payloadId is suitable if builds on top of the correct parent hash
    if (parentExecutionHash.isZero()) {
      // pre-merge, must build on top of a detected terminal block
      boolean isSuitable =
          terminalBlockHash.isPresent()
              && forkChoiceState.getHeadExecutionBlockHash().equals(terminalBlockHash.get());
      LOG.debug("isPayloadIdSuitable - pre-merge: returning {}", isSuitable);
      return isSuitable;
    } else {
      // post-merge, must build on top of the existing parent
      boolean isSuitable = forkChoiceState.getHeadExecutionBlockHash().equals(parentExecutionHash);
      LOG.debug("isPayloadIdSuitable - post-merge: returning {}", isSuitable);
      return isSuitable;
    }
  }

  public SafeFuture<Optional<ExecutionPayloadContext>> getExecutionPayloadContext() {
    return executionPayloadContext;
  }

  public SafeFuture<Optional<ForkChoiceUpdatedResult>> send(
      final ExecutionLayerChannel executionLayer, final UInt64 currentTimestamp) {
    if (!shouldBeSent(currentTimestamp)) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    toBeSentAtTime = currentTimestamp.plus(RESEND_AFTER_MILLIS);

    if (forkChoiceState.getHeadExecutionBlockHash().isZero()) {
      LOG.debug("send - getHeadBlockHash is zero - returning empty");
      executionPayloadContext.complete(Optional.empty());
      return SafeFuture.completedFuture(Optional.empty());
    }

    logSendingForkChoiceUpdated();
    final SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdatedResult =
        executionLayer.engineForkChoiceUpdated(forkChoiceState, payloadBuildingAttributes);

    forkChoiceUpdatedResult
        .thenApply(ForkChoiceUpdatedResult::getPayloadId)
        .thenPeek(this::logSendForkChoiceUpdatedComplete)
        .thenApply(
            maybePayloadId ->
                maybePayloadId.map(
                    payloadId ->
                        new ExecutionPayloadContext(
                            payloadId,
                            forkChoiceState,

                            // it is safe to use orElseThrow() since it should be impossible to
                            // receive a payloadId without having previously sent payloadAttributes
                            payloadBuildingAttributes.orElseThrow())))
        .propagateTo(executionPayloadContext);

    return forkChoiceUpdatedResult.thenApply(Optional::of);
  }

  private void logSendForkChoiceUpdatedComplete(Optional<Bytes8> payloadId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "send - forkChoiceUpdated returned payload id {} for {}, {}",
          payloadId,
          forkChoiceState,
          payloadBuildingAttributes);
    } else {
      if (payloadBuildingAttributes.isPresent()) {
        LOG.info(
            "Builder returned payload id {}, (block slot: {})",
            payloadId.isPresent() ? payloadId.get() : "EMPTY",
            payloadBuildingAttributes.get().getBlockSlot());
      } else {
        LOG.info(
            "Local execution layer returned payload id {}",
            payloadId.isPresent() ? payloadId.get() : "EMPTY");
      }
    }
  }

  private void logSendingForkChoiceUpdated() {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "send - calling forkChoiceUpdated({}, {})", forkChoiceState, payloadBuildingAttributes);
    } else {
      if (payloadBuildingAttributes.isEmpty()) {
        LOG.info(
            "Calling local execution layer to start block production (slot: {})",
            forkChoiceState.getHeadBlockSlot());
      } else {
        LOG.info(
            "Calling builder to start block production (block slot: {})",
            payloadBuildingAttributes.get().getBlockSlot());
      }
    }
  }

  public boolean hasHeadBlockHash() {
    return !forkChoiceState.getHeadExecutionBlockHash().isZero();
  }

  public boolean hasTerminalBlockHash() {
    return terminalBlockHash.isPresent();
  }

  public ForkChoiceState getForkChoiceState() {
    return forkChoiceState;
  }

  private boolean shouldBeSent(final UInt64 currentTimestamp) {
    if (toBeSentAtTime.isZero()) {
      return true;
    }
    if (toBeSentAtTime.isLessThanOrEqualTo(currentTimestamp)) {
      LOG.debug("send - already sent but resending");
      return true;
    }
    LOG.debug("send - already sent");
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkChoiceState", forkChoiceState)
        .add("payloadBuildingAttributes", payloadBuildingAttributes)
        .add("terminalBlockHash", terminalBlockHash)
        .add("executionPayloadContext", executionPayloadContext)
        .toString();
  }
}
