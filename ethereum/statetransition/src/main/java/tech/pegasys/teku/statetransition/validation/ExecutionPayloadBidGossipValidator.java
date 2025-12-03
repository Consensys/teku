/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.HIGHEST_BID_SET_SIZE;
import static tech.pegasys.teku.spec.config.Constants.SEEN_EXECUTION_PAYLOAD_BID_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;

public class ExecutionPayloadBidGossipValidator {

  private static final Logger LOG = LogManager.getLogger();
  final GossipValidationHelper gossipValidationHelper;
  final SigningRootUtil signingRootUtil;

  private final Set<BuilderIndexAndSlot> seenExecutionPayloadBids =
      LimitedSet.createSynchronized(SEEN_EXECUTION_PAYLOAD_BID_SET_SIZE);
  private final Map<Bytes32, UInt64> highestBids =
      LimitedMap.createSynchronizedLRU(HIGHEST_BID_SET_SIZE);

  public ExecutionPayloadBidGossipValidator(
      final Spec spec, final GossipValidationHelper gossipValidationHelper) {
    this.gossipValidationHelper = gossipValidationHelper;
    signingRootUtil = new SigningRootUtil(spec);
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedExecutionPayloadBid signedExecutionPayloadBid) {
    final ExecutionPayloadBid bid = signedExecutionPayloadBid.getMessage();

    /*
     * [REJECT] bid.execution_payment is zero.
     */
    final UInt64 executionPayment = bid.getExecutionPayment();
    if (!executionPayment.isZero()) {
      LOG.trace("Bid's execution payment should be 0 but was {}", executionPayment);
      return completedFuture(
          reject("Bid's execution payment should be 0 but was %s", executionPayment));
    }

    /*
     * [IGNORE] this is the first signed bid seen with a valid signature from the given builder for this slot.
     */
    final BuilderIndexAndSlot key = new BuilderIndexAndSlot(bid.getBuilderIndex(), bid.getSlot());
    if (seenExecutionPayloadBids.contains(key)) {
      return completedFuture(
          ignore(
              "Already received a bid from builder with index %s at slot %s",
              bid.getBuilderIndex(), bid.getSlot()));
    }

    /*
     * [IGNORE] this bid is the highest value bid seen for the corresponding slot and the given parent block hash.
     */

    if (highestBids.containsKey(bid.getParentBlockHash())) {
      final UInt64 existingBidValue = highestBids.get(bid.getParentBlockHash());
      if (bid.getValue().isLessThan(existingBidValue)) {
        LOG.trace(
            "Already received a bid with a higher value {} for block with parent hash {}. Current bid's value is {}",
            existingBidValue,
            bid.getParentBlockHash(),
            bid.getValue());
        return completedFuture(
            ignore(
                "Already received a bid with a higher value %s for block with parent hash %s. Current bid's value is %s",
                existingBidValue, bid.getParentBlockHash(), bid.getValue()));
      }
    }

    /*
     * [IGNORE] bid.slot is the current slot or the next slot.
     */

    // This check considers the gossip clock disparity allowance and hence accepts bids with current
    // slot, next slot but not too early, previous slot but not too late
    if (!gossipValidationHelper.isCurrentSlotWithGossipDisparityAllowance(bid.getSlot())) {
      LOG.trace("Bid must be for current or next slot but was for slot {}", bid.getSlot());
      return completedFuture(
          ignore("Bid must be for current or next slot but was for slot %s", bid.getSlot()));
    }

    /*
     * [IGNORE] bid.parent_block_hash is the block hash of a known execution payload in fork choice.
     */
    if (!gossipValidationHelper.isBlockHashKnown(
        bid.getParentBlockHash(), bid.getParentBlockRoot())) {
      LOG.trace(
          "Bid's parent block hash {} is not the block hash of a known execution payload in fork choice. It will be saved for future processing",
          bid.getParentBlockHash());
      return completedFuture(SAVE_FOR_FUTURE);
    }

    /*
     * [IGNORE] bid.parent_block_root is the hash tree root of a known beacon block in fork choice.
     */
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(bid.getParentBlockRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace("Bid's parent block does not exist. It will be saved for future processing");
      return completedFuture(SAVE_FOR_FUTURE);
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    return gossipValidationHelper
        .getParentStateInBlockEpoch(parentBlockSlot, bid.getParentBlockRoot(), bid.getSlot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "State for block root {} and slot {} is unavailable.",
                    bid.getParentBlockRoot(),
                    bid.getSlot());
                return SAVE_FOR_FUTURE;
              }
              final BeaconState state = maybeState.get();

              /*
               * [REJECT] bid.builder_index is a valid, active, and non-slashed builder index.
               */

              final UInt64 buildrIndex = bid.getBuilderIndex();
              if (!gossipValidationHelper.isValidBuilderIndex(buildrIndex, state, bid.getSlot())) {
                LOG.trace(
                    "Invalid builder index {}. Builder should be valid, active and non-slashed.",
                    buildrIndex);
                return reject(
                    "Invalid builder index %s. Builder should be valid, active and non-slashed.",
                    buildrIndex);
              }

              /*
               * [REJECT] the builder's withdrawal credentials' prefix is BUILDER_WITHDRAWAL_PREFIX
               * -- i.e. is_builder_withdrawal_credential(state.validators[bid.builder_index].withdrawal_credentials)
               * returns True.
               */
              if (!gossipValidationHelper.hasBuilderWithdrawalCredential(
                  bid.getBuilderIndex(), state, bid.getSlot())) {
                LOG.trace(
                    "Builder with index {} must have builder withdrawal credential",
                    bid.getBuilderIndex());
                return reject(
                    "Builder with index %s must have builder withdrawal credential",
                    bid.getBuilderIndex());
              }

              /*
               * [IGNORE] bid.value is less or equal than the builder's excess balance
               * -- i.e. MIN_ACTIVATION_BALANCE + bid.value <= state.balances[bid.builder_index].
               */
              if (!gossipValidationHelper.builderHasEnoughBalanceForBid(
                  bid.getValue(), bid.getBuilderIndex(), state, bid.getSlot())) {
                LOG.trace(
                    "Bid value {} exceeds builder with index {} excess balance",
                    bid.getValue(),
                    bid.getBuilderIndex());
                return ignore(
                    "Bid value %s exceeds builder with index %s excess balance",
                    bid.getValue(), bid.getBuilderIndex());
              }

              /*
               * [REJECT] signed_execution_payload_bid.signature is valid with respect to the bid.builder_index.
               */
              if (!isSignatureValid(signedExecutionPayloadBid, state)) {
                LOG.trace("Invalid payload execution bid signature");
                return reject("Invalid payload execution bid signature");
              }
              highestBids.put(bid.getParentBlockHash(), bid.getValue());
              seenExecutionPayloadBids.add(key);
              return ACCEPT;
            });
  }

  private boolean isSignatureValid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid, final BeaconState state) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignExecutionPayloadBid(
            signedExecutionPayloadBid.getMessage(), state.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
        signingRoot,
        signedExecutionPayloadBid.getMessage().getBuilderIndex(),
        signedExecutionPayloadBid.getSignature(),
        state);
  }

  record BuilderIndexAndSlot(UInt64 validatorIndex, UInt64 slot) {}
}
