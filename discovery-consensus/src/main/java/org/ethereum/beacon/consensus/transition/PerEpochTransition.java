/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.consensus.transition;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.StateTransition;
import org.ethereum.beacon.consensus.TransitionType;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;

/**
 * Per-epoch transition, which happens at the start of the first slot of every epoch.
 *
 * <p>Calls {@link BeaconChainSpec#process_epoch(MutableBeaconState)}.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#epoch-processing">Epoch
 *     processing</a> in the spec.
 */
public class PerEpochTransition implements StateTransition<BeaconStateEx> {
  private static final Logger logger = LogManager.getLogger(PerEpochTransition.class);

  private final BeaconChainSpec spec;

  public PerEpochTransition(BeaconChainSpec spec) {
    this.spec = spec;
  }

  @Override
  public BeaconStateEx apply(BeaconStateEx origState) {
    logger.debug(
        () ->
            "Applying epoch transition to state: ("
                + spec.hash_tree_root(origState).toStringShort()
                + ") "
                + origState.toString(spec.getConstants(), spec::signing_root));

    TransitionType.EPOCH.checkCanBeAppliedAfter(origState.getTransition());

    MutableBeaconState state = origState.createMutableCopy();

    spec.process_epoch(state);

    BeaconStateEx ret = new BeaconStateExImpl(state.createImmutable(), TransitionType.EPOCH);

    logger.debug(
        () ->
            "Epoch transition result state: ("
                + spec.hash_tree_root(ret).toStringShort()
                + ") "
                + ret.toString(spec.getConstants(), spec::signing_root));

    return ret;
  }

  EpochTransitionSummary getEpochTransitionSummary(BeaconStateEx origState) {
    EpochTransitionSummary summary = new EpochTransitionSummary();

    // Process epoch on the first slot of the next epoch.
    if (!origState
        .getSlot()
        .increment()
        .modulo(spec.getConstants().getSlotsPerEpoch())
        .equals(SlotNumber.ZERO)) {
      return summary;
    }

    summary.preState = origState;

    MutableBeaconState state = origState.createMutableCopy();

    summary.currentEpochSummary.activeAttesters =
        spec.get_active_validator_indices(state, spec.get_current_epoch(state));
    summary.currentEpochSummary.validatorBalance =
        spec.get_total_balance(state, summary.currentEpochSummary.activeAttesters);
    List<PendingAttestation> current_epoch_boundary_attestations =
        spec.get_matching_source_attestations(state, spec.get_current_epoch(state));
    summary.currentEpochSummary.boundaryAttesters =
        current_epoch_boundary_attestations.stream()
            .flatMap(
                a ->
                    spec.get_attesting_indices(state, a.getData(), a.getAggregationBits()).stream())
            .collect(Collectors.toList());
    summary.currentEpochSummary.boundaryAttestingBalance =
        spec.get_attesting_balance(state, current_epoch_boundary_attestations);

    summary.previousEpochSummary.activeAttesters =
        spec.get_active_validator_indices(state, spec.get_previous_epoch(state));
    summary.previousEpochSummary.validatorBalance =
        spec.get_total_balance(state, summary.previousEpochSummary.activeAttesters);
    List<PendingAttestation> previous_epoch_boundary_attestations =
        spec.get_matching_source_attestations(state, spec.get_previous_epoch(state));
    summary.previousEpochSummary.boundaryAttesters =
        previous_epoch_boundary_attestations.stream()
            .flatMap(
                a ->
                    spec.get_attesting_indices(state, a.getData(), a.getAggregationBits()).stream())
            .collect(Collectors.toList());
    summary.previousEpochSummary.boundaryAttestingBalance =
        spec.get_attesting_balance(state, previous_epoch_boundary_attestations);
    List<PendingAttestation> previous_epoch_matching_head_attestations =
        spec.get_matching_head_attestations(state, spec.get_previous_epoch(state));
    summary.headAttesters =
        previous_epoch_matching_head_attestations.stream()
            .flatMap(
                a ->
                    spec.get_attesting_indices(state, a.getData(), a.getAggregationBits()).stream())
            .collect(Collectors.toList());
    summary.headAttestingBalance =
        spec.get_attesting_balance(state, previous_epoch_matching_head_attestations);
    summary.justifiedAttesters.addAll(summary.previousEpochSummary.activeAttesters);
    summary.justifiedAttestingBalance = summary.previousEpochSummary.validatorBalance;

    EpochNumber epochs_since_finality =
        spec.get_current_epoch(state).increment().minus(state.getFinalizedCheckpoint().getEpoch());

    if (epochs_since_finality.lessEqual(spec.getConstants().getMinEpochsToInactivityPenalty())) {
      summary.noFinality = false;
    } else {
      summary.noFinality = true;
    }

    spec.process_justification_and_finalization(state);
    spec.process_crosslinks(state);

    if (!spec.get_current_epoch(state).equals(spec.getConstants().getGenesisEpoch())) {
      summary.attestationDeltas = spec.get_attestation_deltas(state);
      summary.crosslinkDeltas = spec.get_crosslink_deltas(state);
    }

    spec.process_rewards_and_penalties(state);
    List<ValidatorIndex> ejectedValidators = spec.process_registry_updates(state);
    spec.process_slashings(state);
    spec.process_final_updates(state);

    BeaconStateEx ret = new BeaconStateExImpl(state.createImmutable(), TransitionType.EPOCH);

    summary.ejectedValidators = ejectedValidators;
    summary.postState = ret;

    return summary;
  }
}
