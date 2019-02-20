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

package tech.pegasys.artemis.statetransition.util;

import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.statetransition.BeaconState;
import tech.pegasys.artemis.statetransition.StateTransitionException;

public class AttestationUtil {

  /**
   * Returns the attestations specific for the specific epoch.
   *
   * @param state
   * @param epoch
   * @return
   */
  public static List<PendingAttestation> get_epoch_attestations(
      BeaconState state, UnsignedLong epoch) throws Exception {
    List<PendingAttestation> latest_attestations = state.getLatest_attestations();
    List<PendingAttestation> epoch_attestations = new ArrayList<>();

    for (PendingAttestation attestation : latest_attestations) {
      if (epoch.equals(BeaconStateUtil.slot_to_epoch(attestation.getData().getSlot()))) {
        epoch_attestations.add(attestation);
      }
    }
    if (epoch_attestations.isEmpty()) {
      throw new Exception("No pending attestation found for the specified epoch");
    }
    return epoch_attestations;
  }

  public static List<PendingAttestation> get_current_epoch_boundary_attestations(
      BeaconState state, List<PendingAttestation> current_epoch_attestations) throws Exception {

    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);

    List<PendingAttestation> current_epoch_boundary_attestations = new ArrayList<>();

    for (PendingAttestation attestation : current_epoch_attestations) {
      if (attestation
              .getData()
              .getEpoch_boundary_root()
              .equals(
                  BeaconStateUtil.get_block_root(
                      state, EpochProcessorUtil.get_epoch_start_slot(current_epoch)))
          && attestation.getData().getJustified_epoch().equals(state.getJustified_epoch())) {
        current_epoch_boundary_attestations.add(attestation);
      }
    }

    if (current_epoch_boundary_attestations.isEmpty()) {
      throw new StateTransitionException("No current epoch boundary attestation found");
    }

    return current_epoch_boundary_attestations;
  }

  public static List<PendingAttestation> get_previous_epoch_boundary_attestations(
      BeaconState state, List<PendingAttestation> previous_epoch_attestations) throws Exception {

    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    List<PendingAttestation> previous_epoch_boundary_attestations = new ArrayList<>();

    for (PendingAttestation attestation : previous_epoch_attestations) {
      if (attestation
          .getData()
          .getEpoch_boundary_root()
          .equals(
              BeaconStateUtil.get_block_root(
                  state, EpochProcessorUtil.get_epoch_start_slot(previous_epoch)))) {
        previous_epoch_boundary_attestations.add(attestation);
      }
    }

    if (previous_epoch_boundary_attestations.isEmpty()) {
      throw new StateTransitionException("No previous epoch boundary attestation found");
    }

    return previous_epoch_boundary_attestations;
  }

  public static List<PendingAttestation> get_previous_epoch_justified_attestations(
      BeaconState state) throws Exception {
    // Get previous and current epoch
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    // Get previous and current_epoch_attestations
    List<PendingAttestation> attestations = get_epoch_attestations(state, previous_epoch);

    attestations.addAll(get_epoch_attestations(state, current_epoch));

    UnsignedLong justified_epoch = state.getJustified_epoch();
    List<PendingAttestation> previous_epoch_justified_attestations = new ArrayList<>();
    for (PendingAttestation attestation : attestations) {
      if (attestation.getData().getJustified_epoch().equals(justified_epoch)) {
        previous_epoch_justified_attestations.add(attestation);
      }
    }

    return previous_epoch_justified_attestations;
  }

  public static List<Integer> get_previous_epoch_justified_attester_indices(BeaconState state)
      throws Exception {
    // Get previous_epoch_justified_attestations
    List<PendingAttestation> previous_epoch_justified_attestations =
        get_previous_epoch_justified_attestations(state);

    return get_attester_indices(state, previous_epoch_justified_attestations);
  }

  public static UnsignedLong get_previous_epoch_justified_attesting_balance(BeaconState state)
      throws Exception {
    // Get previous_epoch_justified_attester_indices
    List<Integer> previous_epoch_justified_attester_indices =
        get_previous_epoch_justified_attester_indices(state);

    return get_total_attesting_balance(state, previous_epoch_justified_attester_indices);
  }

  public static List<Integer> get_previous_epoch_boundary_attester_indices(BeaconState state)
      throws Exception {
    // Get previous epoch
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    // Get previous_epoch_attestations
    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    // Get previous_epoch_boundary_attestations
    List<PendingAttestation> previous_epoch_boundary_attestations =
        get_previous_epoch_boundary_attestations(state, previous_epoch_attestations);

    return get_attester_indices(state, previous_epoch_boundary_attestations);
  }

  /**
   * Returns the sum of balances for all the attesters that were active at the current epoch
   * boundary
   *
   * @param state
   * @return current_epoch_boundary_attesting_balance
   */
  public static UnsignedLong get_current_epoch_boundary_attesting_balance(BeaconState state)
      throws Exception {

    // Get current epoch
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);

    // Get current_epoch_attestations
    List<PendingAttestation> current_epoch_attestations =
        get_epoch_attestations(state, current_epoch);

    // Get current epoch_boundary_attestations
    List<PendingAttestation> current_epoch_boundary_attestations =
        get_current_epoch_boundary_attestations(state, current_epoch_attestations);

    // Get current_epoch_boundary_attester_indices
    List<Integer> current_epoch_boundary_attester_indices =
        get_attester_indices(state, current_epoch_boundary_attestations);

    return get_total_attesting_balance(state, current_epoch_boundary_attester_indices);
  }

  /**
   * Returns the sum of balances for all the attesters that were active at the previous epoch
   * boundary
   *
   * @param state
   * @return previous_epoch_boundary_attesting_balance
   */
  public static UnsignedLong get_previous_epoch_boundary_attesting_balance(BeaconState state)
      throws Exception {

    List<Integer> previous_epoch_boundary_attester_indices =
        get_previous_epoch_boundary_attester_indices(state);

    return get_total_attesting_balance(state, previous_epoch_boundary_attester_indices);
  }

  public static List<PendingAttestation> get_previous_epoch_head_attestations(BeaconState state)
      throws Exception {
    // Get previous epoch
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    // Get current_epoch_attestations
    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    List<PendingAttestation> previous_epoch_head_attestations = new ArrayList<>();
    for (PendingAttestation attestation : previous_epoch_attestations) {
      if (attestation
          .getData()
          .getBeacon_block_root()
          .equals(BeaconStateUtil.get_block_root(state, attestation.getData().getSlot()))) {
        previous_epoch_head_attestations.add(attestation);
      }
    }
    return previous_epoch_head_attestations;
  }

  public static List<Integer> get_previous_epoch_head_attester_indices(BeaconState state)
      throws Exception {
    List<PendingAttestation> previous_epoch_head_attestations =
        get_previous_epoch_head_attestations(state);

    return get_attester_indices(state, previous_epoch_head_attestations);
  }

  public static UnsignedLong get_previous_epoch_head_attesting_balance(BeaconState state)
      throws Exception {
    List<Integer> previous_epoch_head_attester_indices =
        get_previous_epoch_head_attester_indices(state);

    return get_total_attesting_balance(state, previous_epoch_head_attester_indices);
  }

  public static List<Integer> get_previous_epoch_attester_indices(BeaconState state)
      throws Exception {
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    return get_attester_indices(state, previous_epoch_attestations);
  }

  /**
   * Returns the union of validator index sets, where the sets are the attestation participants of
   * attestations passed in TODO: the union part takes O(n^2) time, where n is the number of
   * validators. OPTIMIZE
   *
   * @param state
   * @param attestations
   * @return attester_indices
   */
  static List<Integer> get_attester_indices(
      BeaconState state, List<PendingAttestation> attestations) throws IllegalStateException {

    List<ArrayList<Integer>> validator_index_sets = new ArrayList<ArrayList<Integer>>();

    for (PendingAttestation attestation : attestations) {
      validator_index_sets.add(
          BeaconStateUtil.get_attestation_participants(
              state, attestation.getData(), attestation.getAggregation_bitfield().toArray()));
    }

    List<Integer> attester_indices = new ArrayList<Integer>();
    for (List<Integer> validator_index_set : validator_index_sets) {
      for (Integer validator_index : validator_index_set) {
        if (!attester_indices.contains(validator_index)) {
          attester_indices.add(validator_index);
        }
      }
    }
    return attester_indices;
  }

  /**
   * Returns the total attesting for the attester indices
   *
   * @param state
   * @param attester_indices
   * @return total_attesting_balance
   */
  public static UnsignedLong get_total_attesting_balance(
      BeaconState state, List<Integer> attester_indices) {
    UnsignedLong attesting_balance = UnsignedLong.ZERO;
    for (Integer attester_index : attester_indices) {
      attesting_balance =
          attesting_balance.plus(BeaconStateUtil.get_effective_balance(state, attester_index));
    }

    return attesting_balance;
  }

  public static int ceil_div8(int input) {
    return (int) Math.ceil(((double) input) / 8.0d);
  }

  /**
   * get indices of validators attesting to state for the given block_root TODO: the union part
   * takes O(n^2) time, where n is the number of validators. OPTIMIZE
   *
   * @param state
   * @param crosslink_committee
   * @param shard_block_root
   * @return
   * @throws BlockValidationException
   */
  public static List<Integer> attesting_validator_indices(
      BeaconState state, CrosslinkCommittee crosslink_committee, Bytes32 shard_block_root)
      throws Exception {
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);
    List<PendingAttestation> combined_attestations = get_epoch_attestations(state, current_epoch);
    combined_attestations.addAll(get_epoch_attestations(state, previous_epoch));

    List<ArrayList<Integer>> validator_index_sets = new ArrayList<>();

    for (PendingAttestation attestation : combined_attestations) {
      if (attestation.getData().getShard().compareTo(crosslink_committee.getShard()) == 0
          && attestation.getData().getShard_block_root() == shard_block_root) {
        validator_index_sets.add(
            BeaconStateUtil.get_attestation_participants(
                state, attestation.getData(), attestation.getParticipation_bitfield().toArray()));
      }
    }

    List<Integer> attesting_validator_indices = new ArrayList<Integer>();
    for (List<Integer> validator_index_set : validator_index_sets) {
      for (Integer validator_index : validator_index_set) {
        if (!attesting_validator_indices.contains(validator_index)) {
          attesting_validator_indices.add(validator_index);
        }
      }
    }
    return attesting_validator_indices;
  }

  /**
   * is the shard_block_root that was voted on by the most validators (by balance).
   *
   * @param state
   * @param crosslink_committee
   * @return
   */
  public static Bytes32 winning_root(BeaconState state, CrosslinkCommittee crosslink_committee)
      throws Exception {
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);
    List<PendingAttestation> combined_attestations = get_epoch_attestations(state, current_epoch);
    combined_attestations.addAll(get_epoch_attestations(state, previous_epoch));

    Map<Bytes32, UnsignedLong> shard_balances = new HashMap<>();
    for (PendingAttestation attestation : combined_attestations) {
      if (attestation.getData().getShard().compareTo(crosslink_committee.getShard()) == 0) {
        List<Integer> attesting_indices =
            BeaconStateUtil.get_attestation_participants(
                state, attestation.getData(), attestation.getParticipation_bitfield().toArray());
        UnsignedLong attesting_balance =
            BeaconStateUtil.get_total_effective_balance(state, attesting_indices);
        shard_balances.put(
            attestation.getData().getShard_block_root(),
            shard_balances
                .get(attestation.getData().getShard_block_root())
                .plus(attesting_balance));
      }
    }

    UnsignedLong winning_root_balance = UnsignedLong.ZERO;
    // The spec currently has no way of handling uninitialized winning_root
    Bytes32 winning_root = Bytes32.ZERO;
    for (Bytes32 shard_block_root : shard_balances.keySet()) {
      if (shard_balances.get(shard_block_root).compareTo(winning_root_balance) > 0) {
        winning_root_balance = shard_balances.get(shard_block_root);
        winning_root = shard_block_root;
      } else if (shard_balances.get(shard_block_root).compareTo(winning_root_balance) == 0) {
        if (shard_block_root
                .toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN)
                .compareTo(winning_root.toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN))
            > 0) {
          winning_root = shard_block_root;
        }
      }
    }
    return winning_root;
  }


  /**
   * get indices of validators attesting to state for the winning block root
   *
   * @param state
   * @param crosslink_committee
   * @return
   */
  public static List<Integer> attesting_validators(
          BeaconState state, CrosslinkCommittee crosslink_committee) throws Exception {
    return attesting_validator_indices(
            state, crosslink_committee, winning_root(state, crosslink_committee));
  }

  /**
   * get total balance of validators attesting to state for the given block_root
   *
   * @param state
   * @param crosslink_committee
   * @param shard
   * @return
   * @throws BlockValidationException
   */
  public static UnsignedLong total_attesting_balance(
      BeaconState state, CrosslinkCommittee crosslink_committee) throws Exception {
    List<Integer> attesting_validators = attesting_validators(state, crosslink_committee);
    return BeaconStateUtil.get_total_effective_balance(state, attesting_validators);
  }

  public static PendingAttestation inclusion_slot_attestation(BeaconState state, Integer index)
      throws Exception {
    UnsignedLong previous_epoch = BeaconStateUtil.get_previous_epoch(state);

    List<PendingAttestation> previous_epoch_attestations =
        get_epoch_attestations(state, previous_epoch);

    List<PendingAttestation> possible_attestations = new ArrayList<>();
    for (PendingAttestation attestation : previous_epoch_attestations) {
      List<Integer> attestation_participants =
          BeaconStateUtil.get_attestation_participants(
              state, attestation.getData(), attestation.getParticipation_bitfield().toArray());
      if (attestation_participants.contains(index)) {
        possible_attestations.add(attestation);
      }
    }

    PendingAttestation lowest_inclusion_slot_attestation =
        Collections.min(possible_attestations, Comparator.comparing(a -> a.getSlot_included()));

    return lowest_inclusion_slot_attestation;
  }

  public static UnsignedLong inclusion_slot(BeaconState state, Integer index) throws Exception {
    PendingAttestation lowest_inclusion_slot_attestation = inclusion_slot_attestation(state, index);
    return lowest_inclusion_slot_attestation.getSlot_included();
  }

  public static UnsignedLong inclusion_distance(BeaconState state, Integer index) throws Exception {
    PendingAttestation lowest_inclusion_slot_attestation = inclusion_slot_attestation(state, index);
    return lowest_inclusion_slot_attestation
        .getSlot_included()
        .minus(lowest_inclusion_slot_attestation.getData().getSlot());
  }
}
