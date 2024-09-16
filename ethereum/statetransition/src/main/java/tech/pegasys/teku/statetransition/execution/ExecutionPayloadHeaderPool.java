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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip7732.ExecutionPayloadHeaderEip7732;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class ExecutionPayloadHeaderPool implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  // builders can broadcast a bid for only the current or the next slot, so no need to keep bids for
  // a long time in the pool
  private static final int BIDS_RETENTION_SLOTS = 3;
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

  @Override
  public void onSlot(final UInt64 slot) {
    signedBids.removeIf(
        bid -> {
          final UInt64 bidSlot = ExecutionPayloadHeaderEip7732.required(bid.getMessage()).getSlot();
          return bidSlot.isLessThan(slot.minusMinZero(BIDS_RETENTION_SLOTS));
        });
  }

  public void subscribeOperationAdded(
      final OperationAddedSubscriber<SignedExecutionPayloadHeader> subscriber) {
    this.subscribers.subscribe(subscriber);
  }

  public Optional<SignedExecutionPayloadHeader> selectBidForBlock(
      final BeaconState stateAtBlockSlot, final Bytes32 parentBeaconBlockRoot) {
    final List<SignedExecutionPayloadHeader> applicableBids =
        signedBids.stream()
            .filter(
                signedBid -> {
                  // The header parent block root equals the current block's parent_root.
                  final ExecutionPayloadHeaderEip7732 bid =
                      ExecutionPayloadHeaderEip7732.required(signedBid.getMessage());
                  return bid.getParentBlockRoot().equals(parentBeaconBlockRoot);
                })
            .toList();
    for (SignedExecutionPayloadHeader bid : applicableBids) {
      final Optional<OperationInvalidReason> blockInclusionValidation =
          operationValidator.validateForBlockInclusion(stateAtBlockSlot, bid);
      if (blockInclusionValidation.isEmpty()) {
        return Optional.of(bid);
      } else {
        LOG.warn(
            "Bid is not valid to be included in a block: {}. Removing it from the pool.",
            blockInclusionValidation.get().describe());
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
