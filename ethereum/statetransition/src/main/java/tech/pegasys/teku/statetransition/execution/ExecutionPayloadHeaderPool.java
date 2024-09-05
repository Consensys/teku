/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class ExecutionPayloadHeaderPool {

  private static final int DEFAULT_SIGNED_BIDS_POOL_SIZE = 64;

  private final Set<SignedExecutionPayloadHeader> signedBids =
      LimitedSet.createSynchronizedIterable(DEFAULT_SIGNED_BIDS_POOL_SIZE);

  private final OperationValidator<SignedExecutionPayloadHeader> operationValidator;

  private final Subscribers<OperationAddedSubscriber<SignedExecutionPayloadHeader>> subscribers =
      Subscribers.create(true);

  public ExecutionPayloadHeaderPool(
      final OperationValidator<SignedExecutionPayloadHeader> operationValidator) {
    this.operationValidator = operationValidator;
  }

  public void subscribeOperationAdded(
      final OperationAddedSubscriber<SignedExecutionPayloadHeader> subscriber) {
    this.subscribers.subscribe(subscriber);
  }

  public Optional<SignedExecutionPayloadHeader> selectBidForBlock(
      final BeaconState stateAtBlockSlot) {
    for (SignedExecutionPayloadHeader bid : signedBids) {
      if (operationValidator.validateForBlockInclusion(stateAtBlockSlot, bid).isEmpty()) {
        return Optional.of(bid);
      } else {
        // The item is no longer valid to be included in a block so remove it from the pool.
        remove(bid);
      }
    }
    return Optional.empty();
  }

  public SafeFuture<InternalValidationResult> addLocal(final SignedExecutionPayloadHeader item) {
    return add(item, false);
  }

  public SafeFuture<InternalValidationResult> addRemote(
      final SignedExecutionPayloadHeader item, final Optional<UInt64> arrivalTimestamp) {
    return add(item, true);
  }

  public void remove(final SignedExecutionPayloadHeader item) {
    signedBids.remove(item);
  }

  private SafeFuture<InternalValidationResult> add(
      final SignedExecutionPayloadHeader signedBid, final boolean fromNetwork) {
    return operationValidator
        .validateForGossip(signedBid)
        .thenApply(
            result -> {
              if (result.code().equals(ValidationResultCode.ACCEPT)
                  || result.code().equals(ValidationResultCode.SAVE_FOR_FUTURE)) {
                signedBids.add(signedBid);
                subscribers.forEach(s -> s.onOperationAdded(signedBid, result, fromNetwork));
              }
              return result;
            });
  }
}
