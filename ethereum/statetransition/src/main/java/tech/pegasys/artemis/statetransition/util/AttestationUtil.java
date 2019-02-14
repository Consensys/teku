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

import static tech.pegasys.artemis.datastructures.Constants.EPOCH_LENGTH;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.statetransition.BeaconState;
import tech.pegasys.artemis.statetransition.StateTransitionException;

public class AttestationUtil {

  public static List<PendingAttestationRecord> get_current_epoch_attestations(BeaconState state) {
    // Get current epoch
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);

    List<PendingAttestationRecord> latest_attestations = state.getLatest_attestations();
    List<PendingAttestationRecord> current_epoch_attestations = new ArrayList<>();

    for (PendingAttestationRecord attestation : latest_attestations) {
      if (current_epoch.equals(BeaconStateUtil.slot_to_epoch(attestation.getData().getSlot()))) {
        current_epoch_attestations.add(attestation);
      }
    }
    return current_epoch_attestations;
  }

  /*
  public static ArrayList<PendingAttestationRecord> get_previous_epoch_attestations(
      BeaconState state) {
    ArrayList<PendingAttestationRecord> previous_epoch_attestations = new ArrayList<>();
    ArrayList<PendingAttestationRecord> current_epoch_attestation =
        get_current_epoch_attestations(state);

    for (PendingAttestationRecord record : current_epoch_attestation) {
      if (state
                  .getSlot()
                  .minus(UnsignedLong.valueOf(2))
                  .times(UnsignedLong.valueOf(EPOCH_LENGTH))
                  .compareTo(record.getData().getSlot())
              <= 0
          && record
                  .getData()
                  .getSlot()
                  .compareTo(state.getSlot().minus(UnsignedLong.valueOf(EPOCH_LENGTH)))
              < 0) previous_epoch_attestations.add(record);
    }
    return previous_epoch_attestations;
  }
  */

  public static List<PendingAttestationRecord> get_current_epoch_boundary_attestations(
      BeaconState state, List<PendingAttestationRecord> current_epoch_attestations)
      throws Exception {
    // Get current epoch
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);

    List<PendingAttestationRecord> current_epoch_boundary_attestations = new ArrayList<>();

    for (PendingAttestationRecord attestation : current_epoch_attestations) {
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

    return current_epoch_attestations;
  }

  public static List<Integer> get_current_epoch_boundary_attester_indices(
      BeaconState state, List<PendingAttestationRecord> current_epoch_boundary_attestations) {

    List<ArrayList<Integer>> validator_index_sets = new ArrayList<ArrayList<Integer>>();

    for (PendingAttestationRecord attestation : current_epoch_boundary_attestations) {
      validator_index_sets.add(
          BeaconStateUtil.get_attestation_participants(
              state, attestation.getData(), attestation.getParticipation_bitfield().toArray()));
    }

    List<Integer> current_epoch_boundary_attester_indices = new ArrayList<Integer>();
    for (List<Integer> validator_index_set : validator_index_sets) {
      for (Integer validator_index : validator_index_set) {
        if (!current_epoch_boundary_attester_indices.contains(validator_index)) {
          current_epoch_boundary_attester_indices.add(validator_index);
        }
      }
    }
    return current_epoch_boundary_attester_indices;
  }

  public static UnsignedLong get_previous_epoch_boundary_attesting_balance(BeaconState state)
      throws Exception {
    // todo
    return UnsignedLong.ZERO;
  }

  public static int ceil_div8(int input) {
    return (int) Math.ceil(((double) input) / 8.0d);
  }

  /**
   * return the total balance of all attesting validators
   *
   * @param state
   * @param crosslink_committee
   * @param shard
   * @return
   * @throws BlockValidationException
   */
  public static UnsignedLong total_attesting_balance(
      BeaconState state, CrosslinkCommittee crosslink_committee, Bytes32 shard_block_root)
      throws BlockValidationException {
    List<Integer> attesting_validator_indices =
        attesting_validator_indices(state, crosslink_committee, shard_block_root);
    return BeaconStateUtil.get_total_effective_balance(state, attesting_validator_indices);
  }

  /*
  public static ArrayList<Integer> attesting_validator_indices(
      BeaconState state, CrosslinkCommittee crosslink_committee, Bytes32 shard_block_root)
      throws BlockValidationException {
    ArrayList<PendingAttestation> combined_attestations = get_current_epoch_attestations(state);
    combined_attestations.addAll(get_previous_epoch_attestations(state));

    for (PendingAttestation record : combined_attestations) {
      if (record.getData().getShard().compareTo(crosslink_committee.getShard()) == 0
          && record.getData().getShard_block_root() == shard_block_root) {
        return BeaconStateUtil.get_attestation_participants(
            state, record.getData(), record.getParticipation_bitfield().toArray());
      }
    }
    throw new BlockValidationException("attesting_validator_indicies appear to be empty");
  }
  */
}
