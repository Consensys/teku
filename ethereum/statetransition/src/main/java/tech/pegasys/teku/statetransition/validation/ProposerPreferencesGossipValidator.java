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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerPreferencesGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private static final int RECENT_SEEN_PROPOSER_PREFERENCES_CACHE_SIZE = 1024;

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final SigningRootUtil signingRootUtil;
  private final RecentChainData recentChainData;

  private final Set<DedupKey> seenProposerPreferences =
      LimitedSet.createSynchronized(RECENT_SEEN_PROPOSER_PREFERENCES_CACHE_SIZE);

  public ProposerPreferencesGossipValidator(
      final Spec spec,
      final GossipValidationHelper gossipValidationHelper,
      final RecentChainData recentChainData) {
    this.spec = spec;
    this.gossipValidationHelper = gossipValidationHelper;
    this.signingRootUtil = new SigningRootUtil(spec);
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedProposerPreferences signedProposerPreferences) {
    final ProposerPreferences proposerPreferences = signedProposerPreferences.getMessage();
    final UInt64 proposalSlot = proposerPreferences.getProposalSlot();
    final Bytes32 dependentRoot = proposerPreferences.getDependentRoot();

    /*
     * [IGNORE] preferences.proposal_slot is in the current or next epoch
     */
    if (!gossipValidationHelper.isSlotInCurrentEpoch(proposalSlot)
        && !gossipValidationHelper.isSlotInNextEpoch(proposalSlot)) {
      LOG.trace(
          "Proposer preferences proposal slot {} is not in the current or next epoch",
          proposalSlot);
      return completedFuture(
          ignore(
              "Proposer preferences proposal slot %s is not in the current or next epoch",
              proposalSlot));
    }

    /*
     * [IGNORE] preferences.proposal_slot has not already passed
     */
    if (!gossipValidationHelper.isSlotFromFuture(proposalSlot)
        && !gossipValidationHelper.isSlotCurrent(proposalSlot)) {
      LOG.trace("Proposer preferences proposal slot {} has already passed", proposalSlot);
      return completedFuture(
          ignore("Proposer preferences proposal slot %s has already passed", proposalSlot));
    }

    /*
     * [IGNORE] The block with root preferences.dependent_root has been seen
     * (a client MAY queue preferences for processing once the block is retrieved).
     */
    if (!gossipValidationHelper.isBlockAvailable(dependentRoot)) {
      LOG.trace(
          "Proposer preferences dependent root {} has not been seen. Saving for future processing",
          dependentRoot);
      return completedFuture(SAVE_FOR_FUTURE);
    }

    /*
     * [IGNORE] The signed_proposer_preferences is the first valid message for the tuple
     * (preferences.dependent_root, preferences.proposal_slot, preferences.validator_index)
     */
    final DedupKey dedupKey =
        new DedupKey(dependentRoot, proposalSlot, proposerPreferences.getValidatorIndex());
    if (seenProposerPreferences.contains(dedupKey)) {
      return completedFuture(ignoreAlreadySeen(dedupKey));
    }

    /*
     * Look up the checkpoint state at (proposal_epoch - 1, dependent_root). The state used by
     * is_valid_proposal_slot has current_epoch == proposal_epoch - 1, so the lookahead index for
     * proposal_slot is always SLOTS_PER_EPOCH + (proposal_slot % SLOTS_PER_EPOCH).
     */
    final UInt64 checkpointEpoch = spec.computeEpochAtSlot(proposalSlot).minusMinZero(1);
    return recentChainData
        .retrieveCheckpointState(new Checkpoint(checkpointEpoch, dependentRoot))
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "Could not retrieve checkpoint state for ({}, {}). Saving for future processing",
                    checkpointEpoch,
                    dependentRoot);
                return SAVE_FOR_FUTURE;
              }
              final BeaconState state = maybeState.get();

              /*
               * [REJECT] is_valid_proposal_slot(state, preferences) returns True
               */
              final int slotsPerEpoch = spec.atSlot(proposalSlot).getConfig().getSlotsPerEpoch();
              final int lookaheadIndex = slotsPerEpoch + proposalSlot.mod(slotsPerEpoch).intValue();
              final UInt64 expectedValidatorIndex =
                  BeaconStateFulu.required(state).getProposerLookahead().getElement(lookaheadIndex);
              if (!expectedValidatorIndex.equals(proposerPreferences.getValidatorIndex())) {
                LOG.trace(
                    "Proposer preferences validator index {} does not match expected proposer {} for slot {}",
                    proposerPreferences.getValidatorIndex(),
                    expectedValidatorIndex,
                    proposalSlot);
                return reject(
                    "Proposer preferences validator index %s does not match expected proposer %s for slot %s",
                    proposerPreferences.getValidatorIndex(), expectedValidatorIndex, proposalSlot);
              }

              /*
               * [REJECT] signed_proposer_preferences.signature is valid with respect to
               * the validator's public key
               */
              if (!isSignatureValid(signedProposerPreferences, state)) {
                LOG.trace("Invalid proposer preferences signature");
                return reject("Invalid proposer preferences signature");
              }

              if (!seenProposerPreferences.add(dedupKey)) {
                return ignoreAlreadySeen(dedupKey);
              }

              return ACCEPT;
            });
  }

  private boolean isSignatureValid(
      final SignedProposerPreferences signedProposerPreferences, final BeaconState state) {
    final Bytes signingRoot =
        signingRootUtil.signingRootForSignProposerPreferences(
            signedProposerPreferences.getMessage(), state.getForkInfo());
    return gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
        signingRoot,
        signedProposerPreferences.getMessage().getValidatorIndex(),
        signedProposerPreferences.getSignature(),
        state);
  }

  private InternalValidationResult ignoreAlreadySeen(final DedupKey dedupKey) {
    LOG.trace(
        "Already received proposer preferences for tuple ({}, {}, {})",
        dedupKey.dependentRoot(),
        dedupKey.proposalSlot(),
        dedupKey.validatorIndex());
    return ignore(
        "Already received proposer preferences for tuple (%s, %s, %s)",
        dedupKey.dependentRoot(), dedupKey.proposalSlot(), dedupKey.validatorIndex());
  }

  private record DedupKey(Bytes32 dependentRoot, UInt64 proposalSlot, UInt64 validatorIndex) {}
}
