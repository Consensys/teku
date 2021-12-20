/*
 * Copyright 2021 ConsenSys AG.
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;

public class ForkChoiceUpdateData {
  private static final Logger LOG = LogManager.getLogger();

  private final ForkChoiceState forkChoiceState;
  private final Optional<PayloadAttributes> payloadAttributes;
  private final Optional<Bytes32> terminalBlockHash;
  private final SafeFuture<Optional<Bytes8>> payloadId = new SafeFuture<>();
  private boolean sent = false;

  public ForkChoiceUpdateData() {
    this.forkChoiceState = new ForkChoiceState(Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO);
    this.payloadAttributes = Optional.empty();
    this.terminalBlockHash = Optional.empty();
  }

  public ForkChoiceUpdateData(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadAttributes> payloadAttributes,
      final Optional<Bytes32> terminalBlockHash) {
    if (terminalBlockHash.isPresent() && forkChoiceState.getHeadBlockHash().isZero()) {
      this.forkChoiceState =
          new ForkChoiceState(terminalBlockHash.get(), terminalBlockHash.get(), Bytes32.ZERO);
    } else {
      this.forkChoiceState = forkChoiceState;
    }
    this.payloadAttributes = payloadAttributes;
    this.terminalBlockHash = terminalBlockHash;
  }

  public ForkChoiceUpdateData withForkChoiceState(final ForkChoiceState forkChoiceState) {
    if (this.forkChoiceState.equals(forkChoiceState)) {
      return this;
    }
    return new ForkChoiceUpdateData(forkChoiceState, payloadAttributes, terminalBlockHash);
  }

  public ForkChoiceUpdateData withPayloadAttributes(
      final Optional<PayloadAttributes> payloadAttributes) {
    if (this.payloadAttributes.equals(payloadAttributes)) {
      return this;
    }
    return new ForkChoiceUpdateData(forkChoiceState, payloadAttributes, terminalBlockHash);
  }

  public ForkChoiceUpdateData withTerminalBlockHash(final Bytes32 terminalBlockHash) {
    if (this.terminalBlockHash.isPresent()
        && this.terminalBlockHash.get().equals(terminalBlockHash)) {
      return this;
    }
    return new ForkChoiceUpdateData(
        forkChoiceState, payloadAttributes, Optional.of(terminalBlockHash));
  }

  public boolean isPayloadIdSuitable(final Bytes32 parentExecutionHash, final UInt64 timestamp) {
    if (payloadAttributes.isEmpty()) {
      LOG.debug("isPayloadIdSuitable - payloadAttributes.isEmpty returning false");
      // not producing a block
      return false;
    }

    final PayloadAttributes attributes = this.payloadAttributes.get();
    if (!attributes.getTimestamp().equals(timestamp)) {
      LOG.debug("isPayloadIdSuitable - wrong timestamp");
      // wrong timestamp
      return false;
    }

    // payloadId is suitable if builds on top of the correct parent hash
    if (parentExecutionHash.isZero()) {
      // pre-merge, must build on top of a detected terminal block
      boolean isSuitable =
          terminalBlockHash.isPresent()
              && forkChoiceState.getHeadBlockHash().equals(terminalBlockHash.get());
      LOG.debug("isPayloadIdSuitable - pre-merge: returning {}", isSuitable);
      return isSuitable;
    } else {
      // post-merge, must build on top of the existing parent
      boolean isSuitable = forkChoiceState.getHeadBlockHash().equals(parentExecutionHash);
      LOG.debug("isPayloadIdSuitable - post-merge: returning {}", isSuitable);
      return isSuitable;
    }
  }

  public SafeFuture<Optional<Bytes8>> getPayloadId() {
    return payloadId;
  }

  public void send(final ExecutionEngineChannel executionEngine) {
    if (sent) {
      LOG.debug("send - already sent");
      return;
    }
    sent = true;

    if (forkChoiceState.getHeadBlockHash().isZero()) {
      LOG.debug("send - getHeadBlockHash is zero - returning empty");
      payloadId.complete(Optional.empty());
      return;
    }

    LOG.debug("send - calling forkChoiceUpdated({}, {})", forkChoiceState, payloadAttributes);
    executionEngine
        .forkChoiceUpdated(forkChoiceState, payloadAttributes)
        .thenApply(ForkChoiceUpdatedResult::getPayloadId)
        .thenPeek(
            payloadId ->
                LOG.debug(
                    "send - forkChoiceUpdated returned payload id {} for {}, {}",
                    payloadId,
                    forkChoiceState,
                    payloadAttributes))
        .propagateTo(payloadId);
  }

  public boolean hasHeadBlockHash() {
    return !forkChoiceState.getHeadBlockHash().isZero();
  }

  public boolean hasTerminalBlockHash() {
    return terminalBlockHash.isPresent();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkChoiceState", forkChoiceState)
        .add("payloadAttributes", payloadAttributes)
        .add("terminalBlockHash", terminalBlockHash)
        .add("payloadId", payloadId)
        .toString();
  }
}
