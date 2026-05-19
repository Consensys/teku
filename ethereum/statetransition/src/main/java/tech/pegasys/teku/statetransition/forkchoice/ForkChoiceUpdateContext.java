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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

sealed interface ForkChoiceUpdateContext
    permits PlainForkChoiceUpdateContext, PreparingForkChoiceUpdateContext {
  static ForkChoiceUpdateContext plain(final ForkChoiceUpdateData forkChoiceUpdateData) {
    return new PlainForkChoiceUpdateContext(forkChoiceUpdateData);
  }

  static ForkChoiceUpdateContext preparing(final PayloadBuildSession payloadBuildSession) {
    return new PreparingForkChoiceUpdateContext(payloadBuildSession);
  }

  ForkChoiceUpdateData getForkChoiceUpdateData();

  default ForkChoiceUpdateData getForkChoiceUpdateDataForPayloadId(final UInt64 blockSlot) {
    return getSessionFor(blockSlot)
        .map(PayloadBuildSession::getForkChoiceUpdateData)
        .orElse(getForkChoiceUpdateData());
  }

  default Optional<PayloadBuildSession> getPayloadBuildSession() {
    return Optional.empty();
  }

  default Optional<PayloadBuildSession> getSessionFor(final UInt64 proposalSlot) {
    return getPayloadBuildSession().filter(session -> session.isFor(proposalSlot));
  }

  default Optional<PayloadBuildSession> getProductionSessionFor(final UInt64 blockSlot) {
    return getPayloadBuildSession().filter(session -> session.isProductionFor(blockSlot));
  }

  default boolean isBlockingForkChoiceUpdates() {
    return getPayloadBuildSession()
        .filter(PayloadBuildSession::isBlockingForkChoiceUpdates)
        .isPresent();
  }

  default ForkChoiceUpdateContext withTerminalBlockHash(final Bytes32 executionBlockHash) {
    return plain(getForkChoiceUpdateData().withTerminalBlockHash(executionBlockHash));
  }
}

record PlainForkChoiceUpdateContext(ForkChoiceUpdateData forkChoiceUpdateData)
    implements ForkChoiceUpdateContext {

  @Override
  public ForkChoiceUpdateData getForkChoiceUpdateData() {
    return forkChoiceUpdateData;
  }
}

record PreparingForkChoiceUpdateContext(PayloadBuildSession payloadBuildSession)
    implements ForkChoiceUpdateContext {

  @Override
  public ForkChoiceUpdateData getForkChoiceUpdateData() {
    return payloadBuildSession.getForkChoiceUpdateData();
  }

  @Override
  public Optional<PayloadBuildSession> getPayloadBuildSession() {
    return Optional.of(payloadBuildSession);
  }

  @Override
  public ForkChoiceUpdateContext withTerminalBlockHash(final Bytes32 executionBlockHash) {
    payloadBuildSession.withTerminalBlockHash(executionBlockHash);
    return this;
  }
}
