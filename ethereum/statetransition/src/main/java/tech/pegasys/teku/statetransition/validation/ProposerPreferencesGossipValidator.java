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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerPreferencesGossipValidator {

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_SLOTS_TO_TRACK = 10;

  private final Spec spec;
  private final GossipValidationHelper gossipValidationHelper;
  private final SigningRootUtil signingRootUtil;
  private final RecentChainData recentChainData;

  private final Map<UInt64, Set<UInt64>> seenProposerPreferences =
      LimitedMap.createSynchronizedLRU(MAX_SLOTS_TO_TRACK);

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

    /*
     * [IGNORE] preferences.proposal_slot is in the next epoch -- i.e.
     * compute_epoch_at_slot(preferences.proposal_slot) == get_current_epoch(state) + 1
     */
    if (!gossipValidationHelper.isSlotInNextEpoch(proposalSlot)) {
      LOG.trace("Proposer preferences proposal slot {} is not in the next epoch", proposalSlot);
      return completedFuture(
          ignore("Proposer preferences proposal slot %s is not in the next epoch", proposalSlot));
    }

    /*
     * [IGNORE] The signed_proposer_preferences is the first valid message received from the
     * validator with index preferences.validator_index and the given slot preferences.slot
     */
    if (seenProposerPreferences
        .getOrDefault(proposalSlot, Set.of())
        .contains(proposerPreferences.getValidatorIndex())) {
      LOG.trace(
          "Already received proposer preferences from validator {} for slot {}",
          proposerPreferences.getValidatorIndex(),
          proposalSlot);
      return completedFuture(
          ignore(
              "Already received proposer preferences from validator %s for slot %s",
              proposerPreferences.getValidatorIndex(), proposalSlot));
    }

    return getState()
        .thenApply(
            state -> {
              final BeaconStateGloas gloasState = BeaconStateGloas.required(state);

              /*
               * [REJECT] preferences.validator_index is present at the correct slot in the
               * next epoch's portion of state.proposer_lookahead -- i.e.
               * is_valid_proposal_slot(state, preferences) returns True
               */
              final int slotsPerEpoch = spec.atSlot(proposalSlot).getConfig().getSlotsPerEpoch();
              final int lookaheadIndex = slotsPerEpoch + proposalSlot.mod(slotsPerEpoch).intValue();
              final UInt64 expectedValidatorIndex =
                  gloasState.getProposerLookahead().getElement(lookaheadIndex);
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

              if (!seenProposerPreferences
                  .computeIfAbsent(proposalSlot, __ -> ConcurrentHashMap.newKeySet())
                  .add(proposerPreferences.getValidatorIndex())) {
                LOG.trace(
                    "Another proposer preferences from validator {} for slot {} already processed",
                    proposerPreferences.getValidatorIndex(),
                    proposalSlot);
                return ignore(
                    "Another proposer preferences from validator %s for slot %s already processed",
                    proposerPreferences.getValidatorIndex(), proposalSlot);
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

  private SafeFuture<BeaconState> getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for proposer preferences processing."));
  }
}
