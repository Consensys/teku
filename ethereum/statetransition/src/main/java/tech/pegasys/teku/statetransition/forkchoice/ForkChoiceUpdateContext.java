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

final class ForkChoiceUpdateContext {
  private final ForkChoiceUpdateData forkChoiceUpdateData;
  private final Optional<PayloadAttributesSession> payloadAttributesSession;

  private ForkChoiceUpdateContext(final ForkChoiceUpdateData forkChoiceUpdateData) {
    this.forkChoiceUpdateData = forkChoiceUpdateData;
    this.payloadAttributesSession = Optional.empty();
  }

  private ForkChoiceUpdateContext(final PayloadAttributesSession payloadAttributesSession) {
    this.forkChoiceUpdateData = payloadAttributesSession.getForkChoiceUpdateData();
    this.payloadAttributesSession = Optional.of(payloadAttributesSession);
  }

  static ForkChoiceUpdateContext plain(final ForkChoiceUpdateData forkChoiceUpdateData) {
    return new ForkChoiceUpdateContext(forkChoiceUpdateData);
  }

  static ForkChoiceUpdateContext preparing(
      final PayloadAttributesSession payloadAttributesSession) {
    return new ForkChoiceUpdateContext(payloadAttributesSession);
  }

  ForkChoiceUpdateData getForkChoiceUpdateData() {
    return payloadAttributesSession
        .map(PayloadAttributesSession::getForkChoiceUpdateData)
        .orElse(forkChoiceUpdateData);
  }

  ForkChoiceUpdateData getForkChoiceUpdateDataForPayloadId(final UInt64 blockSlot) {
    return getSessionFor(blockSlot)
        .map(PayloadAttributesSession::getForkChoiceUpdateData)
        .orElse(getForkChoiceUpdateData());
  }

  Optional<PayloadAttributesSession> getPayloadAttributesSession() {
    return payloadAttributesSession;
  }

  Optional<PayloadAttributesSession> getSessionFor(final UInt64 proposalSlot) {
    return getPayloadAttributesSession().filter(session -> session.isFor(proposalSlot));
  }

  Optional<PayloadAttributesSession> getBlockProductionSessionFor(final UInt64 blockSlot) {
    return getPayloadAttributesSession()
        .filter(session -> session.isForBlockProductionAtSlot(blockSlot));
  }

  Optional<PayloadAttributesSession> getBlockingForkChoiceUpdatesSession() {
    return getPayloadAttributesSession()
        .filter(PayloadAttributesSession::isBlockingForkChoiceUpdates);
  }

  boolean isBlockingForkChoiceUpdates() {
    return getBlockingForkChoiceUpdatesSession().isPresent();
  }

  ForkChoiceUpdateContext withTerminalBlockHash(final Bytes32 executionBlockHash) {
    if (payloadAttributesSession.isPresent()) {
      payloadAttributesSession.orElseThrow().withTerminalBlockHash(executionBlockHash);
      return this;
    }
    return plain(getForkChoiceUpdateData().withTerminalBlockHash(executionBlockHash));
  }
}
