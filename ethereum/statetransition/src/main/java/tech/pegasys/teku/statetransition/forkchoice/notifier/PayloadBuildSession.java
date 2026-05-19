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

package tech.pegasys.teku.statetransition.forkchoice.notifier;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

final class PayloadBuildSession {
  private final UInt64 proposalSlot;
  private ForkChoiceUpdateData forkChoiceUpdateData;
  private final SafeFuture<Void> payloadAttributesApplied = new SafeFuture<>();
  private boolean production;
  private boolean payloadIdRequested = false;

  PayloadBuildSession(
      final UInt64 proposalSlot,
      final ForkChoiceUpdateData forkChoiceUpdateData,
      final boolean production) {
    this.proposalSlot = proposalSlot;
    this.forkChoiceUpdateData = forkChoiceUpdateData;
    this.production = production;
  }

  UInt64 getProposalSlot() {
    return proposalSlot;
  }

  ForkChoiceUpdateData getForkChoiceUpdateData() {
    return forkChoiceUpdateData;
  }

  boolean isProduction() {
    return production;
  }

  boolean isProductionFor(final UInt64 blockSlot) {
    return production && proposalSlot.equals(blockSlot);
  }

  boolean isFor(final UInt64 blockSlot) {
    return proposalSlot.equals(blockSlot);
  }

  boolean isBlockingForkChoiceUpdates() {
    return production && !payloadIdRequested;
  }

  SafeFuture<Void> getPayloadAttributesApplied() {
    return payloadAttributesApplied;
  }

  boolean hasForkChoiceState(final ForkChoiceState forkChoiceState) {
    return forkChoiceUpdateData.getForkChoiceState().equals(forkChoiceState);
  }

  void promoteToProduction() {
    production = true;
  }

  void markPayloadIdRequested(final UInt64 blockSlot) {
    if (isProductionFor(blockSlot)) {
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

  void completePayloadAttributesApplied() {
    payloadAttributesApplied.complete(null);
  }

  void completePayloadAttributesExceptionally(final Throwable error) {
    payloadAttributesApplied.completeExceptionally(error);
  }

  @Override
  public String toString() {
    return "PayloadBuildSession{"
        + "proposalSlot="
        + proposalSlot
        + ", production="
        + production
        + ", payloadIdRequested="
        + payloadIdRequested
        + ", forkChoiceUpdateData="
        + forkChoiceUpdateData
        + '}';
  }
}
