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

package tech.pegasys.artemis.state.util;

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.Constants.ACTIVE;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.crypto.Hash;
import tech.pegasys.artemis.Constants;
import tech.pegasys.artemis.datastructures.beaconchainstate.ShardCommittee;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.state.BeaconState;

public class BeaconStateUtil {

  public static double calc_total_balance(BeaconState state) {
    return 0.0d;
  }

  public static ArrayList<HashMap<Long, ShardCommittee>> get_crosslink_committees_at_slot(
      BeaconState state, long slot) throws BlockValidationException {
    long state_epoch_slot = slot - (slot % Constants.EPOCH_LENGTH);

    if (isOffsetEqualToSlot(slot, state_epoch_slot)) {
      // problems with get_shuffling implementation. Should take 3 parameters and this version takes
      // 4
      // 0 has been entered as a place holder parameter to cover the difference
      long committees_per_slot = get_previous_epoch_committee_count_per_slot(state);
      ArrayList<ArrayList<ShardCommittee>> shuffling =
          BeaconState.get_shuffling(
              state.getPrevious_epoch_randao_mix(),
              state.getValidator_registry(),
              0,
              state.getPrevious_epoch_calculation_slot());
      long offset = slot % Constants.EPOCH_LENGTH;
      long slot_start_shard = 0l;

      if (slot < state_epoch_slot)
        slot_start_shard =
            (getPrevious_epoch_start_shard(state) + committees_per_slot * offset)
                % Constants.EPOCH_LENGTH;
      else
        slot_start_shard =
            (getCurrent_epoch_start_shard(state) + committees_per_slot * offset)
                % Constants.EPOCH_LENGTH;

      ArrayList<HashMap<Long, ShardCommittee>> crosslink_committees_at_slot =
          new ArrayList<HashMap<Long, ShardCommittee>>();
      Iterator<ArrayList<ShardCommittee>> itr = shuffling.iterator();
      for (ArrayList<ShardCommittee> committees : shuffling) {
        for (int i = 0; i < committees_per_slot; i++) {
          HashMap<Long, ShardCommittee> committee = new HashMap<Long, ShardCommittee>();
          committee.put(
              committees_per_slot * offset + i,
              committees.get(toIntExact(slot_start_shard + i) % Constants.SHARD_COUNT));
          crosslink_committees_at_slot.add(committee);
        }
      }
      return crosslink_committees_at_slot;
    } else
      throw new BlockValidationException(
          "calc_total_balance: Exception was thrown for failure of isOffsetEqualToSlot checking slot offset could not be calculated with values provided.");
  }

  private static long getCurrent_epoch_start_shard(BeaconState state) {
    // todo
    return 0l;
  }

  private static long get_previous_epoch_committee_count_per_slot(BeaconState state) {
    // todo
    return 0l;
  }

  private static long getPrevious_epoch_start_shard(BeaconState state) {
    // todo
    return 0l;
  }

  private static boolean isOffsetEqualToSlot(long slot, long state_epoch_slot) {
    return (state_epoch_slot <= slot + Constants.EPOCH_LENGTH)
        && (slot < state_epoch_slot + Constants.EPOCH_LENGTH);
  }

  public static Bytes32 getShard_block_root(BeaconState state, Long shard) {
    return state.getLatest_crosslinks().get(toIntExact(shard)).getShard_block_hash();
  }

  public static ArrayList<ValidatorRecord> get_active_validator_indices(
      ArrayList<ValidatorRecord> validators) {
    ArrayList<ValidatorRecord> activeValidators = new ArrayList<ValidatorRecord>();
    for (ValidatorRecord record : validators) {
      if (isActiveValidator(record)) activeValidators.add(record);
    }
    return activeValidators;
  }

  private static boolean isActiveValidator(ValidatorRecord validator) {
    return validator.getStatus().equals(UnsignedLong.valueOf(ACTIVE))
        || validator.getStatus().equals(UnsignedLong.valueOf(ACTIVE_PENDING_EXIT));
  }

  public static double get_effective_balance(BeaconState state, ValidatorRecord record) {
    // hacky work around for pass by index spec
    int index = state.getValidator_registry().indexOf(record);
    return Math.min(
        state.getValidator_balances().get(index).intValue(),
        Constants.MAX_DEPOSIT * Constants.GWEI_PER_ETH);
  }

  // https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#get_block_root
  public static Bytes32 get_block_root(BeaconState state, long slot) throws Exception {
    long slot_upper_bound = slot + state.getLatest_block_roots().size();
    if ((state.getSlot() <= slot_upper_bound) || slot < state.getSlot())
      return state
          .getLatest_block_roots()
          .get(toIntExact(slot) % state.getLatest_block_roots().size());
    throw new BlockValidationException("Desired block root not within the provided bounds");
  }

  /*
   * @param values
   * @return The merkle root.
   */
  public static Bytes32 merkle_root(ArrayList<Bytes32> list) {
    // https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#merkle_root
    Bytes32[] o = new Bytes32[list.size() * 2];
    for (int i = 0; i < list.size(); i++) {
      o[i + list.size()] = list.get(i);
    }
    for (int i = list.size() - 1; i > 0; i--) {
      o[i] = Hash.keccak256(Bytes.wrap(o[i * 2], o[i * 2 + 1]));
    }
    return o[1];
  }
}
