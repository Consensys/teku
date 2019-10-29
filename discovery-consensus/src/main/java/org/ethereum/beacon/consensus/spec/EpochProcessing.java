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

package org.ethereum.beacon.consensus.spec;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.state.HistoricalBatch;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.javatuples.Pair;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.Bitvector;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.uint.UInt64;
import tech.pegasys.artemis.util.uint.UInt64s;

/**
 * Epoch processing part.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#epoch-processing">Epoch
 *     processing</a> in the spec.
 */
public interface EpochProcessing extends HelperFunction {

  /*
   def get_matching_source_attestations(state: BeaconState, epoch: Epoch) -> List[PendingAttestation]:
     assert epoch in (get_current_epoch(state), get_previous_epoch(state))
     return state.current_epoch_attestations if epoch == get_current_epoch(state) else state.previous_epoch_attestations
  */
  default List<PendingAttestation> get_matching_source_attestations(
      BeaconState state, EpochNumber epoch) {
    assertTrue(epoch.equals(get_current_epoch(state)) || epoch.equals(get_previous_epoch(state)));
    return epoch.equals(get_current_epoch(state))
        ? state.getCurrentEpochAttestations().listCopy()
        : state.getPreviousEpochAttestations().listCopy();
  }

  /*
   def get_matching_target_attestations(state: BeaconState, epoch: Epoch) -> Sequence[PendingAttestation]:
     return [
         a for a in get_matching_source_attestations(state, epoch)
         if a.data.target.root == get_block_root(state, epoch)
     ]
  */
  default List<PendingAttestation> get_matching_target_attestations(
      BeaconState state, EpochNumber epoch) {
    return get_matching_source_attestations(state, epoch).stream()
        .filter(a -> a.getData().getTarget().getRoot().equals(get_block_root(state, epoch)))
        .collect(toList());
  }

  /*
   def get_matching_head_attestations(state: BeaconState, epoch: Epoch) -> List[PendingAttestation]:
     return [
         a for a in get_matching_source_attestations(state, epoch)
         if a.data.beacon_block_root == get_block_root_at_slot(state, get_attestation_data_slot(state, a))
     ]
  */
  default List<PendingAttestation> get_matching_head_attestations(
      BeaconState state, EpochNumber epoch) {
    return get_matching_source_attestations(state, epoch).stream()
        .filter(
            a ->
                a.getData()
                    .getBeaconBlockRoot()
                    .equals(
                        get_block_root_at_slot(
                            state, get_attestation_data_slot(state, a.getData()))))
        .collect(toList());
  }

  /*
   def get_unslashed_attesting_indices(state: BeaconState,
                                   attestations: Sequence[PendingAttestation]) -> Set[ValidatorIndex]:
     output = set()  # type: Set[ValidatorIndex]
     for a in attestations:
         output = output.union(get_attesting_indices(state, a.data, a.aggregation_bits))
     return set(filter(lambda index: not state.validators[index].slashed, output))
  */
  default List<ValidatorIndex> get_unslashed_attesting_indices(
      BeaconState state, List<PendingAttestation> attestations) {
    return attestations.stream()
        .flatMap(a -> get_attesting_indices(state, a.getData(), a.getAggregationBits()).stream())
        .distinct()
        .filter(i -> !state.getValidators().get(i).getSlashed())
        .collect(Collectors.toList());
  }

  /*
   def get_attesting_balance(state: BeaconState, attestations: List[PendingAttestation]) -> Gwei:
     return get_total_balance(state, get_unslashed_attesting_indices(state, attestations))
  */
  default Gwei get_attesting_balance(BeaconState state, List<PendingAttestation> attestations) {
    return get_total_balance(state, get_unslashed_attesting_indices(state, attestations));
  }

  /*
   def get_winning_crosslink_and_attesting_indices(state: BeaconState,
                                               epoch: Epoch,
                                               shard: Shard) -> Tuple[Crosslink, List[ValidatorIndex]]:
     attestations = [a for a in get_matching_source_attestations(state, epoch) if a.data.crosslink.shard == shard]
     crosslinks = filter(
         lambda c: hash_tree_root(state.current_crosslinks[shard]) in (c.parent_root, hash_tree_root(c)),
         [a.data.crosslink for a in attestations]
     )
     # Winning crosslink has the crosslink data root with the most balance voting for it (ties broken lexicographically)
     winning_crosslink = max(crosslinks, key=lambda c: (
         get_attesting_balance(state, [a for a in attestations if a.data.crosslink == c]), c.data_root
     ), default=Crosslink())
     winning_attestations = [a for a in attestations if a.data.crosslink == winning_crosslink]
     return winning_crosslink, get_unslashed_attesting_indices(state, winning_attestations)
  */
  default Pair<Crosslink, List<ValidatorIndex>> get_winning_crosslink_and_attesting_indices(
      BeaconState state, EpochNumber epoch, ShardNumber shard) {
    List<PendingAttestation> attestations =
        get_matching_source_attestations(state, epoch).stream()
            .filter(a -> a.getData().getCrosslink().getShard().equals(shard))
            .collect(toList());
    List<Crosslink> crosslinks =
        attestations.stream()
            .map(a -> a.getData().getCrosslink())
            .filter(
                c -> {
                  Hash32 root = hash_tree_root(state.getCurrentCrosslinks().get(shard));
                  return root.equals(c.getParentRoot()) || root.equals(hash_tree_root(c));
                })
            .collect(toList());

    Crosslink winning_crosslink =
        crosslinks.stream()
            .max(
                (c1, c2) -> {
                  Gwei b1 =
                      get_attesting_balance(
                          state,
                          attestations.stream()
                              .filter(a -> a.getData().getCrosslink().equals(c1))
                              .collect(toList()));
                  Gwei b2 =
                      get_attesting_balance(
                          state,
                          attestations.stream()
                              .filter(a -> a.getData().getCrosslink().equals(c2))
                              .collect(toList()));
                  if (b1.equals(b2)) {
                    return c1.getDataRoot().toString().compareTo(c2.getDataRoot().toString());
                  } else {
                    return b1.compareTo(b2);
                  }
                })
            .orElse(Crosslink.EMPTY);
    List<PendingAttestation> winning_attestations =
        attestations.stream()
            .filter(a -> a.getData().getCrosslink().equals(winning_crosslink))
            .collect(toList());

    return Pair.with(
        winning_crosslink, get_unslashed_attesting_indices(state, winning_attestations));
  }

  /*
   def process_justification_and_finalization(state: BeaconState) -> None:
     if get_current_epoch(state) <= GENESIS_EPOCH + 1:
         return
  */
  default void process_justification_and_finalization(MutableBeaconState state) {
    if (get_current_epoch(state).lessEqual(getConstants().getGenesisEpoch().increment())) {
      return;
    }

    EpochNumber previous_epoch = get_previous_epoch(state);
    EpochNumber current_epoch = get_current_epoch(state);
    Checkpoint old_previous_justified_checkpoint = state.getPreviousJustifiedCheckpoint();
    Checkpoint old_current_justified_checkpoint = state.getCurrentJustifiedCheckpoint();

    /* # Process justifications
    state.previous_justified_checkpoint = state.current_justified_checkpoint
    state.justification_bits[1:] = state.justification_bits[:-1]
    state.justification_bits[0] = 0b0 */
    state.setPreviousJustifiedCheckpoint(state.getCurrentJustifiedCheckpoint());
    state.setJustificationBits(state.getJustificationBits().shl(1));

    /* matching_target_attestations = get_matching_target_attestations(state, previous_epoch)  # Previous epoch
    if get_attesting_balance(state, matching_target_attestations) * 3 >= get_total_active_balance(state) * 2:
       state.current_justified_checkpoint = Checkpoint(epoch=previous_epoch,
                                                     root=get_block_root(state, previous_epoch))
    state.justification_bits[1] = 0b1 */
    List<PendingAttestation> matching_target_attestations =
        get_matching_target_attestations(state, previous_epoch);
    if (get_attesting_balance(state, matching_target_attestations)
        .times(3)
        .greaterEqual(get_total_active_balance(state).times(2))) {
      state.setCurrentJustifiedCheckpoint(
          new Checkpoint(previous_epoch, get_block_root(state, previous_epoch)));
      state.setJustificationBits(state.getJustificationBits().setBit(1, 0b1));
    }

    /* matching_target_attestations = get_matching_target_attestations(state, current_epoch)  # Current epoch
    if get_attesting_balance(state, matching_target_attestations) * 3 >= get_total_active_balance(state) * 2:
        state.current_justified_checkpoint = Checkpoint(epoch=current_epoch,
                                                     root=get_block_root(state, current_epoch))
        state.justification_bits[0] = 0b1 */
    matching_target_attestations = get_matching_target_attestations(state, current_epoch);
    if (get_attesting_balance(state, matching_target_attestations)
        .times(3)
        .greaterEqual(get_total_active_balance(state).times(2))) {
      state.setCurrentJustifiedCheckpoint(
          new Checkpoint(current_epoch, get_block_root(state, current_epoch)));
      state.setJustificationBits(state.getJustificationBits().setBit(0, 0b1));
    }

    /* # Process finalizations
    bits = state.justification_bits */
    Bitvector bits = state.getJustificationBits();

    /* # The 2nd/3rd/4th most recent epochs are justified, the 2nd using the 4th as source
    if all(bits[1:4]) and old_previous_justified_checkpoint.epoch + 3 == current_epoch:
        state.finalized_checkpoint = old_previous_justified_checkpoint */
    if ((bits.getValue() >>> 1) % 8 == 0b111
        && old_previous_justified_checkpoint.getEpoch().plus(3).equals(current_epoch)) {
      state.setFinalizedCheckpoint(old_previous_justified_checkpoint);
    }

    /* # The 2nd/3rd most recent epochs are justified, the 2nd using the 3rd as source
    if all(bits[1:3]) and old_previous_justified_checkpoint.epoch + 2 == current_epoch:
        state.finalized_checkpoint = old_previous_justified_checkpoint */
    if ((bits.getValue() >>> 1) % 4 == 0b11
        && old_previous_justified_checkpoint.getEpoch().plus(2).equals(current_epoch)) {
      state.setFinalizedCheckpoint(old_previous_justified_checkpoint);
    }

    /* # The 1st/2nd/3rd most recent epochs are justified, the 1st using the 3rd as source
    if all(bits[0:3]) and old_current_justified_checkpoint.epoch + 2 == current_epoch:
        state.finalized_checkpoint = old_current_justified_checkpoint */
    if (bits.getValue() % 8 == 0b111
        && old_current_justified_checkpoint.getEpoch().plus(2).equals(current_epoch)) {
      state.setFinalizedCheckpoint(old_current_justified_checkpoint);
    }

    /* # The 1st/2nd most recent epochs are justified, the 1st using the 2nd as source
    if all(bits[0:2]) and old_current_justified_checkpoint.epoch + 1 == current_epoch:
        state.finalized_checkpoint = old_current_justified_checkpoint */
    if (bits.getValue() % 4 == 0b11
        && old_current_justified_checkpoint.getEpoch().plus(1).equals(current_epoch)) {
      state.setFinalizedCheckpoint(old_current_justified_checkpoint);
    }
  }

  /*
   def process_crosslinks(state: BeaconState) -> None:
     state.previous_crosslinks = [c for c in state.current_crosslinks]
     for epoch in (get_previous_epoch(state), get_current_epoch(state)):
         for offset in range(get_committee_count(state, epoch)):
             shard = (get_start_shard(state, epoch) + offset) % SHARD_COUNT
             crosslink_committee = get_crosslink_committee(state, epoch, shard)
             winning_crosslink, attesting_indices = get_winning_crosslink_and_attesting_indices(state, epoch, shard)
             if 3 * get_total_balance(state, attesting_indices) >= 2 * get_total_balance(state, crosslink_committee):
                 state.current_crosslinks[shard] = winning_crosslink
  */
  default void process_crosslinks(MutableBeaconState state) {
    state.getPreviousCrosslinks().setAll(state.getCurrentCrosslinks());

    for (EpochNumber epoch :
        get_previous_epoch(state).iterateTo(get_current_epoch(state).increment())) {
      for (UInt64 offset : UInt64s.iterate(UInt64.ZERO, get_committee_count(state, epoch))) {
        ShardNumber shard =
            get_start_shard(state, epoch).plusModulo(offset, getConstants().getShardCount());
        List<ValidatorIndex> crosslink_committee = get_crosslink_committee(state, epoch, shard);
        Pair<Crosslink, List<ValidatorIndex>> winner =
            get_winning_crosslink_and_attesting_indices(state, epoch, shard);
        Crosslink winning_crosslink = winner.getValue0();
        List<ValidatorIndex> attesting_indices = winner.getValue1();
        if (get_total_balance(state, attesting_indices)
            .times(3)
            .greaterEqual(get_total_balance(state, crosslink_committee).times(2))) {
          state.getCurrentCrosslinks().set(shard, winning_crosslink);
        }
      }
      ;
    }
  }

  /*
   def get_base_reward(state: BeaconState, index: ValidatorIndex) -> Gwei:
     total_balance = get_total_active_balance(state)
     effective_balance = state.validator_registry[index].effective_balance
     return effective_balance * BASE_REWARD_FACTOR // integer_squareroot(total_balance) // BASE_REWARDS_PER_EPOCH
  */
  default Gwei get_base_reward(BeaconState state, ValidatorIndex index) {
    UInt64 total_balance = get_total_active_balance(state);
    Gwei effective_balance = state.getValidators().get(index).getEffectiveBalance();
    return effective_balance
        .times(getConstants().getBaseRewardFactor())
        .dividedBy(integer_squareroot(total_balance))
        .dividedBy(getConstants().getBaseRewardsPerEpoch());
  }

  /*
   def get_attestation_deltas(state: BeaconState) -> Tuple[List[Gwei], List[Gwei]]:
  */
  default Gwei[][] get_attestation_deltas(BeaconState state) {
    EpochNumber previous_epoch = get_previous_epoch(state);
    Gwei total_balance = get_total_active_balance(state);
    Gwei[] rewards = new Gwei[state.getValidators().size().getIntValue()];
    Gwei[] penalties = new Gwei[state.getValidators().size().getIntValue()];
    Arrays.fill(rewards, Gwei.ZERO);
    Arrays.fill(penalties, Gwei.ZERO);

    List<ValidatorIndex> eligible_validator_indices = new ArrayList<>();
    for (ValidatorIndex index : state.getValidators().size()) {
      ValidatorRecord validator = state.getValidators().get(index);
      if (is_active_validator(validator, previous_epoch)
          || (validator.getSlashed()
              && previous_epoch.increment().less(validator.getWithdrawableEpoch()))) {
        eligible_validator_indices.add(index);
      }
    }

    /* Micro-incentives for matching FFG source, FFG target, and head
    matching_source_attestations = get_matching_source_attestations(state, previous_epoch)
    matching_target_attestations = get_matching_target_attestations(state, previous_epoch)
    matching_head_attestations = get_matching_head_attestations(state, previous_epoch) */
    List<PendingAttestation> matching_source_attestations =
        get_matching_source_attestations(state, previous_epoch);
    List<PendingAttestation> matching_target_attestations =
        get_matching_target_attestations(state, previous_epoch);
    List<PendingAttestation> matching_head_attestations =
        get_matching_head_attestations(state, previous_epoch);

    /*  for attestations in (matching_source_attestations, matching_target_attestations, matching_head_attestations):
    unslashed_attesting_indices = get_unslashed_attesting_indices(state, attestations)
    attesting_balance = get_total_balance(state, unslashed_attesting_indices)
    for index in eligible_validator_indices:
        if index in unslashed_attesting_indices:
            rewards[index] += get_base_reward(state, index) * attesting_balance // total_balance
        else:
            penalties[index] += get_base_reward(state, index) */
    for (List<PendingAttestation> attestations :
        Arrays.asList(
            matching_source_attestations,
            matching_target_attestations,
            matching_head_attestations)) {
      List<ValidatorIndex> unslashed_attesting_indices =
          get_unslashed_attesting_indices(state, attestations);
      Gwei attesting_balance = get_total_balance(state, unslashed_attesting_indices);
      for (ValidatorIndex index : eligible_validator_indices) {
        if (unslashed_attesting_indices.contains(index)) {
          rewards[index.getIntValue()] =
              rewards[index.getIntValue()].plus(
                  get_base_reward(state, index).times(attesting_balance).dividedBy(total_balance));
        } else {
          penalties[index.getIntValue()] =
              penalties[index.getIntValue()].plus(get_base_reward(state, index));
        }
      }
    }

    /* # Proposer and inclusion delay micro-rewards
    for index in get_unslashed_attesting_indices(state, matching_source_attestations):
       attestation = min([
           a for a in matching_source_attestations
           if index in get_attesting_indices(state, a.data, a.aggregation_bits)
       ], key=lambda a: a.inclusion_delay)
       proposer_reward = Gwei(get_base_reward(state, index) // PROPOSER_REWARD_QUOTIENT)
       rewards[attestation.proposer_index] += proposer_reward
       max_attester_reward = get_base_reward(state, index) - proposer_reward
       rewards[index] += Gwei(
           max_attester_reward
           * (SLOTS_PER_EPOCH + MIN_ATTESTATION_INCLUSION_DELAY - attestation.inclusion_delay)
           // SLOTS_PER_EPOCH
       ) */
    for (ValidatorIndex index :
        get_unslashed_attesting_indices(state, matching_source_attestations)) {
      PendingAttestation attestation =
          matching_source_attestations.stream()
              .filter(
                  a ->
                      get_attesting_indices(state, a.getData(), a.getAggregationBits())
                          .contains(index))
              .min(Comparator.comparing(PendingAttestation::getInclusionDelay))
              .get();
      Gwei proposer_reward =
          get_base_reward(state, index).dividedBy(getConstants().getProposerRewardQuotient());
      rewards[attestation.getProposerIndex().getIntValue()] =
          rewards[attestation.getProposerIndex().getIntValue()].plus(proposer_reward);
      Gwei max_attester_reward = get_base_reward(state, index).minus(proposer_reward);
      rewards[index.getIntValue()] =
          rewards[index.getIntValue()].plus(
              max_attester_reward
                  .times(
                      getConstants()
                          .getSlotsPerEpoch()
                          .plus(
                              getConstants()
                                  .getMinAttestationInclusionDelay()
                                  .minus(attestation.getInclusionDelay())))
                  .dividedBy(getConstants().getSlotsPerEpoch()));
    }

    /* Inactivity penalty
    finality_delay = previous_epoch - state.finalized_epoch
    if finality_delay > MIN_EPOCHS_TO_INACTIVITY_PENALTY:
        matching_target_attesting_indices = get_unslashed_attesting_indices(state, matching_target_attestations)
        for index in eligible_validator_indices:
            penalties[index] += BASE_REWARDS_PER_EPOCH * get_base_reward(state, index)
            if index not in matching_target_attesting_indices:
                penalties[index] += state.validator_registry[index].effective_balance * finality_delay // INACTIVITY_PENALTY_QUOTIENT */
    EpochNumber finality_delay = previous_epoch.minus(state.getFinalizedCheckpoint().getEpoch());
    if (finality_delay.greater(getConstants().getMinEpochsToInactivityPenalty())) {
      List<ValidatorIndex> matching_target_attesting_indices =
          get_unslashed_attesting_indices(state, matching_target_attestations);
      for (ValidatorIndex index : eligible_validator_indices) {
        penalties[index.getIntValue()] =
            penalties[index.getIntValue()].plus(
                get_base_reward(state, index).times(getConstants().getBaseRewardsPerEpoch()));
        if (!matching_target_attesting_indices.contains(index)) {
          penalties[index.getIntValue()] =
              penalties[index.getIntValue()].plus(
                  state
                      .getValidators()
                      .get(index)
                      .getEffectiveBalance()
                      .times(finality_delay)
                      .dividedBy(getConstants().getInactivityPenaltyQuotient()));
        }
      }
    }

    return new Gwei[][] {rewards, penalties};
  }

  /*
   def get_crosslink_deltas(state: BeaconState) -> Tuple[List[Gwei], List[Gwei]]:
    rewards = [0 for index in range(len(state.validator_registry))]
    penalties = [0 for index in range(len(state.validator_registry))]
    epoch = get_previous_epoch(state)
    for offset in range(get_committee_count(state, epoch)):
        shard = (get_start_shard(state, epoch) + offset) % SHARD_COUNT
        crosslink_committee = get_crosslink_committee(state, epoch, shard)
        winning_crosslink, attesting_indices = get_winning_crosslink_and_attesting_indices(state, epoch, shard)
        attesting_balance = get_total_balance(state, attesting_indices)
        committee_balance = get_total_balance(state, crosslink_committee)
        for index in crosslink_committee:
            base_reward = get_base_reward(state, index)
            if index in attesting_indices:
                rewards[index] += base_reward * attesting_balance // committee_balance
            else:
                penalties[index] += base_reward
    return rewards, penalties
  */
  default Gwei[][] get_crosslink_deltas(BeaconState state) {
    Gwei[] rewards = new Gwei[state.getValidators().size().getIntValue()];
    Gwei[] penalties = new Gwei[state.getValidators().size().getIntValue()];
    Arrays.fill(rewards, Gwei.ZERO);
    Arrays.fill(penalties, Gwei.ZERO);

    EpochNumber epoch = get_previous_epoch(state);
    for (UInt64 offset : UInt64s.iterate(UInt64.ZERO, get_committee_count(state, epoch))) {
      ShardNumber shard =
          get_start_shard(state, epoch).plusModulo(offset, getConstants().getShardCount());
      List<ValidatorIndex> crosslink_committee = get_crosslink_committee(state, epoch, shard);
      Pair<Crosslink, List<ValidatorIndex>> winner =
          get_winning_crosslink_and_attesting_indices(state, epoch, shard);
      List<ValidatorIndex> attesting_indices = winner.getValue1();
      Gwei attesting_balance = get_total_balance(state, attesting_indices);
      Gwei committee_balance = get_total_balance(state, crosslink_committee);
      for (ValidatorIndex index : crosslink_committee) {
        Gwei base_reward = get_base_reward(state, index);
        if (attesting_indices.contains(index)) {
          rewards[index.getIntValue()] =
              rewards[index.getIntValue()].plus(
                  base_reward.times(attesting_balance).dividedBy(committee_balance));
        } else {
          penalties[index.getIntValue()] = penalties[index.getIntValue()].plus(base_reward);
        }
      }
    }

    return new Gwei[][] {rewards, penalties};
  }

  /*
   def process_rewards_and_penalties(state: BeaconState) -> None:
     if get_current_epoch(state) == GENESIS_EPOCH:
         return

     rewards1, penalties1 = get_attestation_deltas(state)
     rewards2, penalties2 = get_crosslink_deltas(state)
     for i in range(len(state.validator_registry)):
         increase_balance(state, i, rewards1[i] + rewards2[i])
         decrease_balance(state, i, penalties1[i] + penalties2[i])
  */
  default void process_rewards_and_penalties(MutableBeaconState state) {
    if (get_current_epoch(state).equals(getConstants().getGenesisEpoch())) {
      return;
    }

    Gwei[][] deltas1 = get_attestation_deltas(state);
    Gwei[] rewards1 = deltas1[0], penalties1 = deltas1[1];
    Gwei[][] deltas2 = get_crosslink_deltas(state);
    Gwei[] rewards2 = deltas2[0], penalties2 = deltas2[1];
    for (ValidatorIndex i : state.getValidators().size()) {
      increase_balance(state, i, rewards1[i.getIntValue()].plus(rewards2[i.getIntValue()]));
      decrease_balance(state, i, penalties1[i.getIntValue()].plus(penalties2[i.getIntValue()]));
    }
  }

  /*
   def process_registry_updates(state: BeaconState) -> None:
  */
  default List<ValidatorIndex> process_registry_updates(MutableBeaconState state) {
    /* Process activation eligibility and ejections
    for index, validator in enumerate(state.validator_registry):
        if validator.activation_eligibility_epoch == FAR_FUTURE_EPOCH and validator.effective_balance == MAX_EFFECTIVE_BALANCE:
            validator.activation_eligibility_epoch = get_current_epoch(state)

        if is_active_validator(validator, get_current_epoch(state)) and validator.effective_balance <= EJECTION_BALANCE:
            initiate_validator_exit(state, index) */
    List<ValidatorIndex> ejected = new ArrayList<>();
    for (ValidatorIndex index : state.getValidators().size()) {
      ValidatorRecord validator = state.getValidators().get(index);
      if (validator.getActivationEligibilityEpoch().equals(getConstants().getFarFutureEpoch())
          && validator.getEffectiveBalance().equals(getConstants().getMaxEffectiveBalance())) {
        state
            .getValidators()
            .update(
                index,
                v ->
                    ValidatorRecord.Builder.fromRecord(v)
                        .withActivationEligibilityEpoch(get_current_epoch(state))
                        .build());
      }

      if (is_active_validator(validator, get_current_epoch(state))
          && validator.getEffectiveBalance().lessEqual(getConstants().getEjectionBalance())) {
        initiate_validator_exit(state, index);
        ejected.add(index);
      }
    }

    /* Queue validators eligible for activation and not dequeued for activation prior to finalized epoch
    activation_queue = sorted([
        index for index, validator in enumerate(state.validator_registry) if
        validator.activation_eligibility_epoch != FAR_FUTURE_EPOCH and
        validator.activation_epoch >= compute_activation_exit_epoch(state.finalized_epoch)
    ], key=lambda index: state.validator_registry[index].activation_eligibility_epoch) */
    List<Pair<ValidatorIndex, ValidatorRecord>> activation_queue = new ArrayList<>();
    for (ValidatorIndex index : state.getValidators().size()) {
      ValidatorRecord v = state.getValidators().get(index);
      if (!v.getActivationEligibilityEpoch().equals(getConstants().getFarFutureEpoch())
          && v.getActivationEpoch()
              .greaterEqual(
                  compute_activation_exit_epoch(state.getFinalizedCheckpoint().getEpoch()))) {
        activation_queue.add(Pair.with(index, v));
      }
    }
    activation_queue.sort(Comparator.comparing(p -> p.getValue1().getActivationEligibilityEpoch()));

    /* Dequeued validators for activation up to churn limit (without resetting activation epoch)
    for index in activation_queue[:get_validator_churn_limit(state)]:
        if validator.activation_epoch == FAR_FUTURE_EPOCH:
            validator.activation_epoch = compute_activation_exit_epoch(get_current_epoch(state)) */
    int limit = get_validator_churn_limit(state).getIntValue();
    List<Pair<ValidatorIndex, ValidatorRecord>> limited_activation_queue =
        activation_queue.size() > limit ? activation_queue.subList(0, limit) : activation_queue;

    for (Pair<ValidatorIndex, ValidatorRecord> p : limited_activation_queue) {
      if (p.getValue1().getActivationEpoch().equals(getConstants().getFarFutureEpoch())) {
        state
            .getValidators()
            .update(
                p.getValue0(),
                v ->
                    ValidatorRecord.Builder.fromRecord(v)
                        .withActivationEpoch(
                            compute_activation_exit_epoch(get_current_epoch(state)))
                        .build());
      }
    }

    return ejected;
  }

  /*
   def process_slashings(state: BeaconState) -> None:
  */
  default void process_slashings(MutableBeaconState state) {
    /* epoch = get_current_epoch(state)
    total_balance = get_total_active_balance(state) */
    EpochNumber epoch = get_current_epoch(state);
    Gwei total_balance = get_total_active_balance(state);

    /* for index, validator in enumerate(state.validators):
    if validator.slashed and epoch + EPOCHS_PER_SLASHINGS_VECTOR // 2 == validator.withdrawable_epoch:
        increment = EFFECTIVE_BALANCE_INCREMENT  # Factored out from penalty numerator to avoid uint64 overflow
        penalty_numerator = ( validator.effective_balance // increment ) * min(sum(state.slashings) * 3, total_balance)
        penalty = penalty_numerator // total_balance * increment
        decrease_balance(state, ValidatorIndex(index), penalty) */
    for (ValidatorIndex index : state.getValidators().size()) {
      ValidatorRecord validator = state.getValidators().get(index);
      if (validator.getSlashed()
          && epoch
              .plus(getConstants().getEpochsPerSlashingsVector().half())
              .equals(validator.getWithdrawableEpoch())) {
        Gwei increment = getConstants().getEffectiveBalanceIncrement();
        Gwei stateSlashings = state.getSlashings().stream().reduce(Gwei::plus).orElse(Gwei.ZERO);
        Gwei penalty_numerator =
            validator
                .getEffectiveBalance()
                .dividedBy(increment)
                .times(UInt64s.min(stateSlashings.times(3), total_balance));
        Gwei penalty = penalty_numerator.dividedBy(total_balance).times(increment);
        decrease_balance(state, index, penalty);
      }
    }
  }

  /*
   def process_final_updates(state: BeaconState) -> None:
  */
  default void process_final_updates(MutableBeaconState state) {
    /* current_epoch = get_current_epoch(state)
    next_epoch = current_epoch + 1 */
    EpochNumber current_epoch = get_current_epoch(state);
    EpochNumber next_epoch = current_epoch.increment();

    /* Reset eth1 data votes
    if (state.slot + 1) % SLOTS_PER_ETH1_VOTING_PERIOD == 0:
        state.eth1_data_votes = [] */
    if (state
        .getSlot()
        .increment()
        .modulo(getConstants().getSlotsPerEth1VotingPeriod())
        .equals(SlotNumber.ZERO)) {
      state.getEth1DataVotes().clear();
    }

    /* Update effective balances with hysteresis
    for index, validator in enumerate(state.validator_registry):
        balance = state.balances[index]
        HALF_INCREMENT = EFFECTIVE_BALANCE_INCREMENT // 2
        if balance < validator.effective_balance or validator.effective_balance + 3 * HALF_INCREMENT < balance:
            validator.effective_balance = min(balance - balance % EFFECTIVE_BALANCE_INCREMENT, MAX_EFFECTIVE_BALANCE) */
    Gwei half_increment = getConstants().getEffectiveBalanceIncrement().dividedBy(2);
    for (ValidatorIndex index : state.getValidators().size()) {
      ValidatorRecord validator = state.getValidators().get(index);
      Gwei balance = state.getBalances().get(index);
      if (balance.less(validator.getEffectiveBalance())
          || validator.getEffectiveBalance().plus(half_increment.times(3)).less(balance)) {
        state
            .getValidators()
            .update(
                index,
                v ->
                    ValidatorRecord.Builder.fromRecord(v)
                        .withEffectiveBalance(
                            UInt64s.min(
                                balance.minus(
                                    Gwei.castFrom(
                                        balance.modulo(
                                            getConstants().getEffectiveBalanceIncrement()))),
                                getConstants().getMaxEffectiveBalance()))
                        .build());
      }
    }

    /* # Set active index root
    index_epoch = Epoch(next_epoch + ACTIVATION_EXIT_DELAY)
    index_root_position = index_epoch % EPOCHS_PER_HISTORICAL_VECTOR
    indices_list = List[ValidatorIndex, VALIDATOR_REGISTRY_LIMIT](get_active_validator_indices(state, index_epoch))
    state.active_index_roots[index_root_position] = hash_tree_root(indices_list) */
    EpochNumber index_epoch = next_epoch.plus(getConstants().getActivationExitDelay());
    EpochNumber index_root_position =
        index_epoch.modulo(getConstants().getEpochsPerHistoricalVector());
    ReadList<Integer, ValidatorIndex> indices_list =
        get_active_validator_indices_list(state, index_epoch);
    state.getActiveIndexRoots().set(index_root_position, hash_tree_root(indices_list));

    /* # Set committees root
    committee_root_position = next_epoch % EPOCHS_PER_HISTORICAL_VECTOR
    state.compact_committees_roots[committee_root_position] = get_compact_committees_root(state, next_epoch) */
    EpochNumber committee_root_position =
        next_epoch.modulo(getConstants().getEpochsPerHistoricalVector());
    state
        .getCompactCommitteesRoots()
        .set(committee_root_position, get_compact_committees_root(state, next_epoch));

    /* # Reset slashings
    state.slashings[next_epoch % EPOCHS_PER_SLASHINGS_VECTOR] = Gwei(0) */
    state
        .getSlashings()
        .set(next_epoch.modulo(getConstants().getEpochsPerSlashingsVector()), Gwei.ZERO);

    /* # Set randao mix
    state.randao_mixes[next_epoch % EPOCHS_PER_HISTORICAL_VECTOR] = get_randao_mix(state, current_epoch */
    state
        .getRandaoMixes()
        .set(
            next_epoch.modulo(getConstants().getEpochsPerHistoricalVector()),
            get_randao_mix(state, current_epoch));

    /* # Set historical root accumulator
    if next_epoch % (SLOTS_PER_HISTORICAL_ROOT // SLOTS_PER_EPOCH) == 0:
        historical_batch = HistoricalBatch(block_roots=state.block_roots, state_roots=state.state_roots)
        state.historical_roots.append(hash_tree_root(historical_batch)) */
    if (next_epoch
        .modulo(
            getConstants().getSlotsPerHistoricalRoot().dividedBy(getConstants().getSlotsPerEpoch()))
        .equals(EpochNumber.ZERO)) {
      HistoricalBatch historical_batch =
          new HistoricalBatch(
              state.getBlockRoots().vectorCopy(), state.getStateRoots().vectorCopy());
      state.getHistoricalRoots().add(hash_tree_root(historical_batch));
    }

    /* # Update start shard
    state.start_shard = Shard((state.start_shard + get_shard_delta(state, current_epoch)) % SHARD_COUNT) */
    state.setStartShard(
        state
            .getStartShard()
            .plusModulo(get_shard_delta(state, current_epoch), getConstants().getShardCount()));

    /* # Rotate current/previous epoch attestations
    state.previous_epoch_attestations = state.current_epoch_attestations
    state.current_epoch_attestations = [] */
    state.getPreviousEpochAttestations().replaceAll(state.getCurrentEpochAttestations().listCopy());
    state.getCurrentEpochAttestations().clear();
  }

  /*
   def process_epoch(state: BeaconState) -> None:
     process_justification_and_finalization(state)
     process_crosslinks(state)
     process_rewards_and_penalties(state)
     process_registry_updates(state)
     # @process_reveal_deadlines
     # @process_challenge_deadlines
     process_slashings(state)
     process_final_updates(state)
     # @after_process_final_updates
  */
  default void process_epoch(MutableBeaconState state) {
    process_justification_and_finalization(state);
    process_crosslinks(state);
    process_rewards_and_penalties(state);
    process_registry_updates(state);
    // @process_reveal_deadlines
    // @process_challenge_deadlines
    process_slashings(state);
    process_final_updates(state);
    // @after_process_final_updates
  }
}
