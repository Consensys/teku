/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

final class PayloadAttributesSession {
  private final UInt64 proposalSlot;
  private ForkChoiceUpdateData forkChoiceUpdateData;
  private final SafeFuture<Void> payloadAttributesResolved = new SafeFuture<>();
  private boolean forBlockProduction;
  private boolean payloadIdRequested = false;

  PayloadAttributesSession(
      final UInt64 proposalSlot,
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final boolean forBlockProduction) {
    this.proposalSlot = proposalSlot;
    this.forkChoiceUpdateData = forkChoiceUpdateData;
    this.forBlockProduction = forBlockProduction;
  }

  UInt64 getProposalSlot() {
    return proposalSlot;
  }

  ForkChoiceUpdateData getForkChoiceUpdateData() {
    return forkChoiceUpdateData;
  }

  boolean isForBlockProduction() {
    return forBlockProduction;
  }

  boolean isForBlockProductionAtSlot(final UInt64 blockSlot) {
    return forBlockProduction && proposalSlot.equals(blockSlot);
  }

  boolean isFor(final UInt64 blockSlot) {
    return proposalSlot.equals(blockSlot);
  }

  boolean isBlockingForkChoiceUpdates() {
    return forBlockProduction && !payloadIdRequested;
  }

  SafeFuture<Void> getPayloadAttributesResolved() {
    return payloadAttributesResolved;
  }

  boolean arePayloadAttributesResolved() {
    return payloadAttributesResolved.isDone();
  }

  boolean hasForkChoiceState(final ForkChoiceState forkChoiceState) {
    return forkChoiceUpdateData.getForkChoiceState().equals(forkChoiceState);
  }

  void promoteToBlockProduction() {
    forBlockProduction = true;
  }

  void markPayloadIdRequested(final UInt64 blockSlot) {
    if (isForBlockProductionAtSlot(blockSlot)) {
      payloadIdRequested = true;
    }
  }

  void withPayloadBuildingAttributes(
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    forkChoiceUpdateData =
        forkChoiceUpdateData.withPayloadBuildingAttributes(payloadBuildingAttributes);
  }

  void withTerminalBlockHash(final Bytes32 executionBlockHash) {
    forkChoiceUpdateData = forkChoiceUpdateData.withTerminalBlockHash(executionBlockHash);
  }

  void completePayloadAttributesResolved() {
    payloadAttributesResolved.complete(null);
  }

  void completePayloadAttributesExceptionally(final Throwable error) {
    payloadAttributesResolved.completeExceptionally(error);
  }

  @Override
  public String toString() {
    return "PayloadAttributesSession{"
        + "proposalSlot="
        + proposalSlot
        + ", forBlockProduction="
        + forBlockProduction
        + ", payloadIdRequested="
        + payloadIdRequested
        + ", forkChoiceUpdateData="
        + forkChoiceUpdateData
        + '}';
  }
}
