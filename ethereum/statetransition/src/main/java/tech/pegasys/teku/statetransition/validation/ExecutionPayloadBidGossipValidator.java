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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;
import static tech.pegasys.teku.spec.config.Constants.HIGHEST_BID_SET_SIZE;
import static tech.pegasys.teku.spec.config.Constants.MAX_SLOTS_TO_TRACK_BUILDERS_BIDS;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.saveForFuture;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;

public class ExecutionPayloadBidGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final GossipValidationHelper gossipValidationHelper;
  private final SigningRootUtil signingRootUtil;
  private final int minBidIncrementPercentage;
  private final Map<UInt64, Set<BuilderAndParent>> seenExecutionPayloadBids =
      LimitedMap.createSynchronizedLRU(MAX_SLOTS_TO_TRACK_BUILDERS_BIDS);
  private final Map<BidParent, UInt64> highestBids =
      LimitedMap.createSynchronizedLRU(HIGHEST_BID_SET_SIZE);

  private final ProposerPreferencesManager proposerPreferencesManager;

  public ExecutionPayloadBidGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final ProposerPreferencesManager proposerPreferencesManager,
      final int minBidIncrementPercentage) {
    this.gossipValidationHelper = gossipValidationHelper;
    this.proposerPreferencesManager = proposerPreferencesManager;
    this.minBidIncrementPercentage = minBidIncrementPercentage;
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
     * [IGNORE] bid.slot is the current slot or the next slot.
     */
    if (!gossipValidationHelper.isSlotCurrentOrNext(bid.getSlot())) {
      LOG.trace("Bid must be for current or next slot but was for slot {}", bid.getSlot());
      return completedFuture(
          ignore("Bid must be for current or next slot but was for slot %s", bid.getSlot()));
    }

    /*
     * [IGNORE] the SignedProposerPreferences where preferences.proposal_slot is equal to
     * bid.slot has been seen
     */
    final Optional<ProposerPreferences> proposerPreferences =
        proposerPreferencesManager.getProposerPreferences(bid.getSlot());
    if (proposerPreferences.isEmpty()) {
      LOG.trace(
          "No proposer preferences available at slot {}. The bid from the builder with index {} will be saved for future processing.",
          bid.getSlot(),
          bid.getBuilderIndex());
      return completedFuture(
          saveForFuture(
              "No proposer preferences available at slot %s. The bid from the builder with index %s will be saved for future processing.",
              bid.getSlot(), bid.getBuilderIndex()));
    }

    /*
     * [REJECT] bid.fee_recipient matches the fee_recipient from the proposer's
     * SignedProposerPreferences associated with bid.slot
     */
    if (!bid.getFeeRecipient().equals(proposerPreferences.get().getFeeRecipient())) {
      LOG.trace(
          "Bid fee recipient {} does not match proposer preferences fee recipient {}",
          bid.getFeeRecipient(),
          proposerPreferences.get().getFeeRecipient());
      return completedFuture(
          ignore(
              "Bid fee recipient %s does not match proposer preferences fee recipient %s",
              bid.getFeeRecipient(), proposerPreferences.get().getFeeRecipient()));
    }

    /*
     * [IGNORE] this is the first signed bid seen with a valid signature from the given builder for the tuple
     * (bid.slot, bid.parent_block_hash, bid.parent_block_root).
     */
    final BuilderAndParent builderAndParent =
        new BuilderAndParent(
            bid.getBuilderIndex(), bid.getParentBlockHash(), bid.getParentBlockRoot());
    if (seenExecutionPayloadBids.getOrDefault(bid.getSlot(), Set.of()).contains(builderAndParent)) {
      LOG.trace(
          "Already received a bid from builder with index {} at slot {} for parent hash {} and parent root {}",
          bid.getBuilderIndex(),
          bid.getSlot(),
          bid.getParentBlockHash(),
          bid.getParentBlockRoot());
      return completedFuture(
          ignore(
              "Already received a bid from builder with index %s at slot %s",
              bid.getBuilderIndex(), bid.getSlot()));
    }

    /*
     * [IGNORE] this bid is the highest value bid seen for the tuple
     * (bid.slot, bid.parent_block_hash, bid.parent_block_root).
     *
     * Note: Implementations SHOULD include DoS prevention measures to
     * mitigate spam from malicious builders submitting numerous bids with minimal value increments.
     * Possible strategies include: (1) only forwarding bids that exceed the current highest bid by a
     * minimum threshold, or (2) forwarding only the highest observed bid at regular time intervals.
     *
     */
    final BidParent bidValueKey =
        new BidParent(bid.getSlot(), bid.getParentBlockHash(), bid.getParentBlockRoot());
    final UInt64 existingBidValue = highestBids.getOrDefault(bidValueKey, UInt64.ZERO);
    if (!existingBidValue.isZero()) {
      final UInt64 minRequiredBid = calculateMinimumRequiredBid(existingBidValue);

      if (bid.getValue().isLessThan(minRequiredBid)) {
        LOG.trace(
            "Bid value {} ETH does not meet minimum increment threshold ({}%). Current highest: {} ETH, minimum required: {} ETH",
            () -> gweiToEth(bid.getValue()),
            () -> minBidIncrementPercentage,
            () -> gweiToEth(existingBidValue),
            () -> gweiToEth(minRequiredBid));
        return completedFuture(
            ignore(
                "Bid value %s ETH does not meet minimum increment threshold (%s%%). Current highest: %s ETH, minimum required: %s ETH",
                gweiToEth(bid.getValue()),
                minBidIncrementPercentage,
                gweiToEth(existingBidValue),
                gweiToEth(minRequiredBid)));
      }
    }

    /*
     * [IGNORE] bid.parent_block_hash is the block hash of a known execution payload in fork choice
     * and is_gas_limit_target_compatible(parent_gas_limit, bid.gas_limit, proposer_preferences.target_gas_limit)
     * is True where parent_gas_limit is the gas_limit of that execution payload.
     */
    final Optional<UInt64> maybeParentGasLimit =
        gossipValidationHelper.getGasLimitForExecutionPayload(
            bid.getParentBlockRoot(), bid.getParentBlockHash());
    if (maybeParentGasLimit.isEmpty()) {
      LOG.trace(
          "Gas limit for parent execution payload with block hash {} is unavailable. It will be saved for future processing",
          bid.getParentBlockHash());
      return completedFuture(
          saveForFuture(
              "Gas limit for parent execution payload with block hash %s is unavailable. The bid will be saved for future processing",
              bid.getParentBlockHash()));
    }
    final UInt64 parentGasLimit = maybeParentGasLimit.get();
    final UInt64 targetGasLimit = proposerPreferences.get().getTargetGasLimit();
    if (!isGasLimitTargetCompatible(parentGasLimit, bid.getGasLimit(), targetGasLimit)) {
      LOG.trace(
          "Bid gas limit {} is not compatible with parent gas limit {} and proposer preferences target gas limit {}",
          bid.getGasLimit(),
          parentGasLimit,
          targetGasLimit);
      return completedFuture(
          ignore(
              "Bid gas limit %s is not compatible with parent gas limit %s and proposer preferences target gas limit %s",
              bid.getGasLimit(), parentGasLimit, targetGasLimit));
    }

    /*
     * [IGNORE] The bid is compatible with the current head branch, i.e.
     * is_bid_compatible_with_head(store, bid) returns True.
     */
    if (!gossipValidationHelper.isBidCompatibleWithHead(bid)) {
      LOG.trace("Bid is not compatible with the current head branch");
      return completedFuture(ignore("Bid is not compatible with the current head branch"));
    }

    /*
     * Retrieve the bid's parent block slot for the remaining validation rules.
     */
    final Optional<UInt64> maybeParentBlockSlot =
        gossipValidationHelper.getSlotForBlockRoot(bid.getParentBlockRoot());
    if (maybeParentBlockSlot.isEmpty()) {
      LOG.trace(
          "Bid's parent block with root {} is unknown. The bid will be saved for future processing",
          bid.getParentBlockRoot());
      return completedFuture(
          saveForFuture(
              "Bid's parent block with root %s is unknown. The bid will be saved for future processing",
              bid.getParentBlockRoot()));
    }
    final UInt64 parentBlockSlot = maybeParentBlockSlot.get();

    /*
     * [REJECT] The bid is for a higher slot than its parent block.
     */
    if (!bid.getSlot().isGreaterThan(parentBlockSlot)) {
      LOG.trace(
          "Bid slot {} is not greater than parent block slot {}", bid.getSlot(), parentBlockSlot);
      return completedFuture(
          reject(
              "Bid slot %s is not greater than parent block slot %s",
              bid.getSlot(), parentBlockSlot));
    }

    return gossipValidationHelper
        .getParentStateInBlockEpoch(parentBlockSlot, bid.getParentBlockRoot(), bid.getSlot())
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "State for block root {} and slot {} is unavailable.",
                    bid.getParentBlockRoot(),
                    parentBlockSlot);
                return saveForFuture(
                    "State for block root %s and slot %s is unavailable. The bid will be saved for future processing.",
                    bid.getParentBlockRoot(), parentBlockSlot);
              }
              final BeaconState state = maybeState.get();

              /*
               * [REJECT] bid.prev_randao is the correct RANDAO mix -- i.e. validate that
               * bid.prev_randao == get_randao_mix(parent_state, get_current_epoch(parent_state)).
               */
              final Bytes32 expectedRandaoMix =
                  gossipValidationHelper.getRandaoMixForCurrentEpoch(state, bid.getSlot());
              if (!bid.getPrevRandao().equals(expectedRandaoMix)) {
                LOG.trace(
                    "Bid prev randao {} does not match expected RANDAO mix {}",
                    bid.getPrevRandao(),
                    expectedRandaoMix);
                return reject(
                    "Bid prev randao %s does not match expected RANDAO mix %s",
                    bid.getPrevRandao(), expectedRandaoMix);
              }

              /*
               * [REJECT] bid.builder_index is a valid/active builder index -- i.e. is_active_builder(state, bid.builder_index) returns True
               */
              if (!gossipValidationHelper.isActiveBuilder(
                  bid.getBuilderIndex(), state, bid.getSlot())) {
                LOG.trace(
                    "Invalid builder index {}. Builder should be valid, active and non-slashed.",
                    bid.getBuilderIndex());
                return reject(
                    "Invalid builder index %s. Builder should be valid, active and non-slashed.",
                    bid.getBuilderIndex());
              }

              /*
               * [IGNORE] bid.value is less or equal than the builder's excess balance
               * -- i.e. MIN_ACTIVATION_BALANCE + bid.value <= state.balances[bid.builder_index].
               */
              if (!gossipValidationHelper.builderHasEnoughBalanceForBid(
                  bid.getValue(), bid.getBuilderIndex(), state, bid.getSlot())) {
                LOG.trace(
                    "Bid value {} ETH exceeds builder with index {} excess balance",
                    () -> gweiToEth(bid.getValue()),
                    bid::getBuilderIndex);
                return ignore(
                    "Bid value %s ETH exceeds builder with index %s excess balance",
                    gweiToEth(bid.getValue()), bid.getBuilderIndex());
              }

              /*
               * [REJECT] signed_execution_payload_bid.signature is valid with respect to the bid.builder_index.
               */
              if (!isSignatureValid(signedExecutionPayloadBid, state)) {
                LOG.trace("Invalid payload execution bid signature");
                return reject("Invalid payload execution bid signature");
              }

              if (!seenExecutionPayloadBids
                  .computeIfAbsent(bid.getSlot(), __ -> ConcurrentHashMap.newKeySet())
                  .add(builderAndParent)) {
                LOG.trace(
                    "Another payload execution bid from Builder with index {} already processed while validating bid for slot {}, parent hash {}, and parent root {}",
                    bid.getBuilderIndex(),
                    bid.getSlot(),
                    bid.getParentBlockHash(),
                    bid.getParentBlockRoot());
                return ignore(
                    "Another payload execution bid from Builder with index %s already processed while validating bid for slot %s",
                    bid.getBuilderIndex(), bid.getSlot());
              }

              // Atomically check threshold and update the highest bid
              final UInt64 bidValue = bid.getValue();
              final AtomicReference<UInt64> existingBidRef = new AtomicReference<>();
              final UInt64 actualHighestBid =
                  highestBids.compute(
                      bidValueKey,
                      (key, existingValue) -> {
                        existingBidRef.set(existingValue);
                        return computeNewHighestBid(existingValue, bidValue);
                      });

              // Check if our bid was actually accepted
              if (!wasBidAccepted(bidValue, existingBidRef.get(), actualHighestBid)) {
                final UInt64 minRequired = calculateMinimumRequiredBid(actualHighestBid);
                LOG.trace(
                    "Bid value {} ETH does not meet minimum increment threshold ({}%) due to concurrent update. Current highest: {} ETH, minimum required: {} ETH",
                    () -> gweiToEth(bidValue),
                    () -> minBidIncrementPercentage,
                    () -> gweiToEth(actualHighestBid),
                    () -> gweiToEth(minRequired));
                return ignore(
                    "Bid value %s ETH does not meet minimum increment threshold (%s%%) due to concurrent update. Current highest: %s ETH, minimum required: %s ETH",
                    gweiToEth(bidValue),
                    minBidIncrementPercentage,
                    gweiToEth(actualHighestBid),
                    gweiToEth(minRequired));
              }

              return ACCEPT;
            });
  }

  /**
   * Calculates the minimum required bid value based on the current highest bid and the configured
   * minimum increment percentage.
   *
   * @param currentHighestBid the current highest bid value
   * @return the minimum bid value required to exceed the threshold
   */
  private UInt64 calculateMinimumRequiredBid(final UInt64 currentHighestBid) {
    final UInt64 minIncrement = currentHighestBid.times(minBidIncrementPercentage).dividedBy(100);
    return currentHighestBid.plus(minIncrement);
  }

  /**
   * Atomically computes the new highest bid value. This method is used within compute() to
   * atomically check the threshold and update the highest bid.
   *
   * @param existingValue the current highest bid value (may be null for first bid)
   * @param newBidValue the new bid value to evaluate
   * @return the value to store (either existing if rejected, or new if accepted)
   */
  private UInt64 computeNewHighestBid(final UInt64 existingValue, final UInt64 newBidValue) {
    // First bid, accept it
    if (existingValue == null) {
      return newBidValue;
    }

    final UInt64 minRequired = calculateMinimumRequiredBid(existingValue);
    // Below threshold, keep existing
    if (newBidValue.isLessThan(minRequired)) {
      return existingValue;
    }
    // Accept the new bid if higher
    return newBidValue.max(existingValue);
  }

  /**
   * Determines if a bid was actually accepted based on the atomic compute operation result. This
   * method correctly handles the case where bidValue equals existingBid (which would fail the
   * threshold check but pass a naive equality test).
   *
   * @param bidValue the bid value that was submitted
   * @param existingBid the bid value that existed before compute (may be null for first bid)
   * @param actualHighestBid the resulting highest bid value after compute
   * @return true if the bid was accepted, false if rejected due to threshold
   */
  private boolean wasBidAccepted(
      final UInt64 bidValue, final UInt64 existingBid, final UInt64 actualHighestBid) {
    // The bid must be the current highest
    if (!actualHighestBid.equals(bidValue)) {
      return false;
    }

    // First bid is always accepted (no threshold to check)
    if (existingBid == null) {
      return true;
    }

    // New bids must meet the minimum increment threshold
    final UInt64 minRequired = calculateMinimumRequiredBid(existingBid);
    return bidValue.isGreaterThanOrEqualTo(minRequired);
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

  static boolean isGasLimitTargetCompatible(
      final UInt64 parentGasLimit, final UInt64 gasLimit, final UInt64 targetGasLimit) {
    final UInt64 maxGasLimitDifference =
        parentGasLimit.dividedBy(1024).max(UInt64.ONE).minus(UInt64.ONE);
    final UInt64 minGasLimit = parentGasLimit.minus(maxGasLimitDifference);
    final UInt64 maxGasLimit = parentGasLimit.plus(maxGasLimitDifference);

    if (targetGasLimit.isGreaterThanOrEqualTo(minGasLimit)
        && targetGasLimit.isLessThanOrEqualTo(maxGasLimit)) {
      return gasLimit.equals(targetGasLimit);
    }
    if (targetGasLimit.isGreaterThan(maxGasLimit)) {
      return gasLimit.equals(maxGasLimit);
    }
    return gasLimit.equals(minGasLimit);
  }

  record BuilderAndParent(UInt64 builderIndex, Bytes32 parentBlockHash, Bytes32 parentBlockRoot) {}

  record BidParent(UInt64 slot, Bytes32 parentBlockHash, Bytes32 parentBlockRoot) {}
}
