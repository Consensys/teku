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

public class AttestationUtil {

  public static ArrayList<PendingAttestation> get_current_epoch_attestations(BeaconState state) {
    List<PendingAttestation> latest_attestations = state.getLatest_attestations();
    ArrayList<PendingAttestation> current_epoch_attestations = new ArrayList<>();
    if (latest_attestations != null) {
      for (PendingAttestation record : latest_attestations) {
        if (isAttestationCurrentEpoch(state, record)) current_epoch_attestations.add(record);
      }
    }
    return current_epoch_attestations;
  }

  public static ArrayList<PendingAttestation> get_previous_epoch_attestations(BeaconState state) {
    ArrayList<PendingAttestation> previous_epoch_attestations = new ArrayList<>();
    ArrayList<PendingAttestation> current_epoch_attestation = get_current_epoch_attestations(state);

    for (PendingAttestation record : current_epoch_attestation) {
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

  private static boolean isAttestationCurrentEpoch(BeaconState state, PendingAttestation record) {
    // TODO: Replace longValue with UnsignedLong
    long epoch_lower_boundary = state.getSlot().longValue() - EPOCH_LENGTH;
    long epoch_upper_boundary = state.getSlot().longValue();
    return (record.getData().getSlot().compareTo(UnsignedLong.valueOf(epoch_lower_boundary)) <= 0
        && record.getData().getSlot().compareTo(UnsignedLong.valueOf(epoch_upper_boundary)) > 0);
  }

  public static ArrayList<PendingAttestation> get_current_epoch_boundary_attestations(
      BeaconState state, ArrayList<PendingAttestation> current_epoch_attestations)
      throws Exception {
    ArrayList<PendingAttestation> current_epoch_boundary_attestations = new ArrayList<>();
    if (current_epoch_attestations != null) {
      // TODO: current_epoch_boundary_attestations is always empty. Update this.
      for (PendingAttestation record : current_epoch_boundary_attestations) {
        if (record
                .getData()
                .getEpoch_boundary_root()
                .equals(
                    BeaconStateUtil.get_block_root(
                        state,
                        record
                            .getData()
                            .getSlot()
                            .minus(UnsignedLong.valueOf(EPOCH_LENGTH))
                            .longValue()))
            && record.getData().getJustified_epoch().equals(state.getJustified_epoch()))
          current_epoch_attestations.add(record);
      }
    }
    return current_epoch_boundary_attestations;
  }

  public static UnsignedLong get_previous_epoch_boundary_attesting_balance(BeaconState state)
      throws Exception {
    // todo
    return UnsignedLong.ZERO;
  }

  public static UnsignedLong get_current_epoch_boundary_attesting_balance(BeaconState state) {
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
}
