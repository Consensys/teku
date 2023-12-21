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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.statetransition.block.NewBlockBuildingSubscriber;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;

public class StubForkChoiceNotifier implements ForkChoiceNotifier {

  private final Subscribers<ForkChoiceUpdatedResultSubscriber> forkChoiceUpdatedSubscribers =
      Subscribers.create(true);

  private final SafeFuture<Optional<ForkChoiceUpdatedResult>> forkChoiceUpdatedResultNotification =
      SafeFuture.completedFuture(Optional.empty());

  @Override
  public void subscribeToForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultSubscriber subscriber) {
    forkChoiceUpdatedSubscribers.subscribe(subscriber);
  }

  @Override
  public void subscribeToNewBlockBuilding(final NewBlockBuildingSubscriber subscriber) {}

  @Override
  public void onForkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<UInt64> proposingSlot) {
    forkChoiceUpdatedSubscribers.deliver(
        ForkChoiceUpdatedResultSubscriber::onForkChoiceUpdatedResult,
        new ForkChoiceUpdatedResultNotification(
            forkChoiceState, false, forkChoiceUpdatedResultNotification));
  }

  @Override
  public void onAttestationsDue(final UInt64 slot) {}

  @Override
  public void onSyncingStatusChanged(final boolean inSync) {}

  @Override
  public SafeFuture<Optional<ExecutionPayloadContext>> getPayloadId(
      final Bytes32 parentBeaconBlockRoot, final UInt64 blockSlot) {
    return null;
  }

  @Override
  public void onTerminalBlockReached(final Bytes32 executionBlockHash) {}

  @Override
  public boolean validatorIsConnected(final UInt64 validatorIndex, final UInt64 currentSlot) {
    return true;
  }
}
