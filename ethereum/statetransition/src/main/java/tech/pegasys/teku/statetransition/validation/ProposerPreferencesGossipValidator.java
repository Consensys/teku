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
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.saveForFuture;

import com.google.errorprone.annotations.FormatMethod;
import java.util.Optional;
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

  // Two mainnet epochs for the current lookahead, with 4x overhead for dependent-root reorgs
  private static final int RECENT_SEEN_PROPOSER_PREFERENCES_CACHE_SIZE = 256;

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final SigningRootUtil signingRootUtil;
  private final RecentChainData recentChainData;

  private final Set<ProposerPreferencesDedupKey> seenProposerPreferences =
      LimitedSet.createSynchronizedLRU(RECENT_SEEN_PROPOSER_PREFERENCES_CACHE_SIZE);

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
     * [IGNORE]_ `preferences.proposal_slot` is within the proposer lookahead --
     * i.e. `compute_epoch_at_slot(preferences.proposal_slot)` is in the range
     * [compute_epoch_at_slot(current_slot), compute_epoch_at_slot(current_slot) + MIN_SEED_LOOKAHEAD]`.
     */
    if (!gossipValidationHelper.isSlotInCurrentEpochWithMinSeedLookaheadTolerance(proposalSlot)) {
      return completedFuture(
          ignorePreferences(
              proposerPreferences,
              "proposal slot is not in the current or within the lookahead epoch"));
    }

    /*
     * [IGNORE] preferences.proposal_slot has not already passed
     */
    if (!gossipValidationHelper.isSlotFromFuture(proposalSlot)
        && !gossipValidationHelper.isSlotCurrent(proposalSlot)) {
      return completedFuture(
          ignorePreferences(proposerPreferences, "proposal slot has already passed"));
    }

    /*
     * [IGNORE] The block with root preferences.dependent_root has been seen
     * (a client MAY queue preferences for processing once the block is retrieved).
     */
    if (!gossipValidationHelper.isBlockAvailable(dependentRoot)) {
      return completedFuture(
          savePreferencesForFuture(
              proposerPreferences,
              "dependent root has not been seen; saving for future processing"));
    }

    /*
     * [IGNORE] The signed_proposer_preferences is the first valid message for the tuple
     * (preferences.dependent_root, preferences.proposal_slot, preferences.validator_index)
     */
    final ProposerPreferencesDedupKey dedupKey =
        new ProposerPreferencesDedupKey(
            dependentRoot, proposalSlot, proposerPreferences.getValidatorIndex());
    if (seenProposerPreferences.contains(dedupKey)) {
      return completedFuture(ignoreAlreadySeen(proposerPreferences));
    }

    /*
     * Look up the checkpoint state at (proposal_epoch - MIN_SEED_LOOKAHEAD, dependent_root). The state used by
     * is_valid_proposal_slot has current_epoch == proposal_epoch - MIN_SEED_LOOKAHEAD, so the lookahead index for
     * proposal_slot is MIN_SEED_LOOKAHEAD * SLOTS_PER_EPOCH + (proposal_slot % SLOTS_PER_EPOCH).
     */
    final int minSeedLookahead = spec.atSlot(proposalSlot).getConfig().getMinSeedLookahead();
    final UInt64 checkpointEpoch =
        spec.computeEpochAtSlot(proposalSlot).minusMinZero(minSeedLookahead);

    /*
     * Pre-flight the checkpoint-state precondition for the [REJECT] is_valid_proposal_slot rule below.
     * A dependent root at or after the checkpoint boundary cannot be the root of the checkpoint state
     * required by that rule.
     */
    final UInt64 checkpointBoundarySlot = spec.computeStartSlotAtEpoch(checkpointEpoch);
    final Optional<UInt64> maybeDependentRootSlot =
        recentChainData.getSlotForBlockRoot(dependentRoot);
    if (maybeDependentRootSlot.isPresent()) {
      final UInt64 dependentRootSlot = maybeDependentRootSlot.get();
      if (!dependentRootSlot.isLessThan(checkpointBoundarySlot)) {
        return completedFuture(
            rejectPreferences(
                proposerPreferences,
                "dependent root is at slot %s but must be before checkpoint boundary slot %s",
                dependentRootSlot,
                checkpointBoundarySlot));
      }
    }
    return recentChainData
        .retrieveCheckpointState(new Checkpoint(checkpointEpoch, dependentRoot))
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return savePreferencesForFuture(
                    proposerPreferences,
                    "checkpoint state for checkpoint epoch %s is unavailable; saving for future processing",
                    checkpointEpoch);
              }
              final BeaconState state = maybeState.get();

              /*
               * [REJECT] is_valid_proposal_slot(state, preferences) returns True, where state is the checkpoint state
               * at the epoch compute_epoch_at_slot(preferences.proposal_slot) - MIN_SEED_LOOKAHEAD
               * and the root preferences.dependent_root.
               */
              final int slotsPerEpoch = spec.atSlot(proposalSlot).getConfig().getSlotsPerEpoch();
              final int lookaheadIndex =
                  minSeedLookahead * slotsPerEpoch + proposalSlot.mod(slotsPerEpoch).intValue();
              final UInt64 expectedValidatorIndex =
                  BeaconStateFulu.required(state).getProposerLookahead().getElement(lookaheadIndex);
              if (!expectedValidatorIndex.equals(proposerPreferences.getValidatorIndex())) {
                return rejectPreferences(
                    proposerPreferences,
                    "validator index does not match expected proposer %s",
                    expectedValidatorIndex);
              }

              /*
               * [REJECT] signed_proposer_preferences.signature is valid with respect to
               * the validator's public key
               */
              if (!isSignatureValid(signedProposerPreferences, state)) {
                return rejectPreferences(proposerPreferences, "invalid signature");
              }

              if (!seenProposerPreferences.add(dedupKey)) {
                return ignoreAlreadySeen(proposerPreferences);
              }

              return acceptPreferences(proposerPreferences);
            })
        .exceptionally(
            error -> {
              return rejectPreferencesWithError(
                  proposerPreferences,
                  error,
                  "unable to generate checkpoint state for checkpoint epoch %s",
                  checkpointEpoch);
            });
  }

  private InternalValidationResult acceptPreferences(
      final ProposerPreferences proposerPreferences) {
    LOG.trace(
        "ProposerPreferences Gossip Validation Result: ACCEPT, context: {}",
        formatProposerPreferencesContext(proposerPreferences));
    return ACCEPT;
  }

  @FormatMethod
  private InternalValidationResult rejectPreferences(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: REJECT, reason: {}", message);
    return reject("%s", message);
  }

  @FormatMethod
  private InternalValidationResult rejectPreferencesWithError(
      final ProposerPreferences proposerPreferences,
      final Throwable error,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: REJECT, reason: {}", message, error);
    return reject("%s", message);
  }

  @FormatMethod
  private InternalValidationResult ignorePreferences(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: IGNORE, reason: {}", message);
    return ignore("%s", message);
  }

  @FormatMethod
  private InternalValidationResult savePreferencesForFuture(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    final String message =
        formatProposerPreferencesMessage(proposerPreferences, descriptionTemplate, args);
    LOG.trace("ProposerPreferences Gossip Validation Result: SAVE_FOR_FUTURE, reason: {}", message);
    return saveForFuture("%s", message);
  }

  @FormatMethod
  private String formatProposerPreferencesMessage(
      final ProposerPreferences proposerPreferences,
      final String descriptionTemplate,
      final Object... args) {
    return String.format(
        "%s: %s",
        formatProposerPreferencesContext(proposerPreferences),
        String.format(descriptionTemplate, args));
  }

  private String formatProposerPreferencesContext(final ProposerPreferences proposerPreferences) {
    return String.format(
        "Proposer preferences (validator index %s, proposal slot %s, dependent root %s)",
        proposerPreferences.getValidatorIndex(),
        proposerPreferences.getProposalSlot(),
        proposerPreferences.getDependentRoot());
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

  private InternalValidationResult ignoreAlreadySeen(
      final ProposerPreferences proposerPreferences) {
    return ignorePreferences(proposerPreferences, "already received");
  }

  private record ProposerPreferencesDedupKey(
      Bytes32 dependentRoot, UInt64 proposalSlot, UInt64 validatorIndex) {}
}
