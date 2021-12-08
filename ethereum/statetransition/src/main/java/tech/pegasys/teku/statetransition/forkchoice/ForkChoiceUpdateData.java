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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;

public class ForkChoiceUpdateData {

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
    // TODO: Use terminalBlockHash as forkChoiceState head root if it was zero
    this.forkChoiceState = forkChoiceState;
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

  public ForkChoiceUpdateData withPayloadAttributes(final PayloadAttributes payloadAttributes) {
    if (this.payloadAttributes.isPresent()
        && this.payloadAttributes.get().equals(payloadAttributes)) {
      return this;
    }
    return new ForkChoiceUpdateData(
        forkChoiceState, Optional.of(payloadAttributes), terminalBlockHash);
  }

  public ForkChoiceUpdateData withoutPayloadAttributes() {
    if (this.payloadAttributes.isEmpty()) {
      return this;
    }
    return new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), terminalBlockHash);
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
      return false;
    }
    final PayloadAttributes attributes = this.payloadAttributes.get();
    if (!attributes.getTimestamp().equals(timestamp)) {
      return false;
    }
    if (parentExecutionHash.isZero() && terminalBlockHash.isEmpty()) {
      return false;
    }
    // TODO: This is wrong. :)
    final Bytes32 requiredExecutionBlockHash =
        parentExecutionHash.isZero() ? terminalBlockHash.get() : parentExecutionHash;
    return forkChoiceState.getHeadBlockHash().equals(parentExecutionHash)
        || (parentExecutionHash.isZero() && terminalBlockHash.isPresent());
  }

  public SafeFuture<Optional<Bytes8>> getPayloadId() {
    return payloadId;
  }

  public void send(final ExecutionEngineChannel executionEngine) {
    if (sent) {
      return;
    }
    sent = true;
    if (forkChoiceState.getHeadBlockHash().isZero()) {
      payloadId.complete(Optional.empty());
      return;
    }
    // TODO: Handle errors here instead of propagating to result?
    executionEngine
        .forkChoiceUpdated(forkChoiceState, payloadAttributes)
        .thenApply(ForkChoiceUpdatedResult::getPayloadId)
        .propagateTo(payloadId);
  }

  public boolean hasHeadBlockHash() {
    return !forkChoiceState.getHeadBlockHash().isZero();
  }
}
