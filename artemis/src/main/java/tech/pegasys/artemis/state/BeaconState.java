/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.state;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.Constants.ACTIVATION;
import static tech.pegasys.artemis.Constants.ACTIVE;
import static tech.pegasys.artemis.Constants.ACTIVE_PENDING_EXIT;
import static tech.pegasys.artemis.Constants.COLLECTIVE_PENALTY_CALCULATION_PERIOD;
import static tech.pegasys.artemis.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.Constants.EXIT;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;
import static tech.pegasys.artemis.Constants.EXITED_WITH_PENALTY;
import static tech.pegasys.artemis.Constants.GWEI_PER_ETH;
import static tech.pegasys.artemis.Constants.MAX_DEPOSIT;
import static tech.pegasys.artemis.Constants.PENDING_ACTIVATION;
import static tech.pegasys.artemis.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.ethereum.core.TreeHash.hash_tree_root;

import tech.pegasys.artemis.datastructures.BeaconChainState.CandidatePoWReceiptRootRecord;
import tech.pegasys.artemis.datastructures.BeaconChainState.CrosslinkRecord;
import tech.pegasys.artemis.datastructures.BeaconChainState.ForkData;
import tech.pegasys.artemis.datastructures.BeaconChainState.PendingAttestationRecord;
import tech.pegasys.artemis.datastructures.BeaconChainState.ShardCommittee;
import tech.pegasys.artemis.datastructures.BeaconChainState.ShardReassignmentRecord;
import tech.pegasys.artemis.datastructures.BeaconChainState.ValidatorRecord;
import tech.pegasys.artemis.datastructures.BeaconChainState.ValidatorRegistryDeltaBlock;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;



public class BeaconState {

  // Misc
  public UInt64 slot;
  private UInt64 genesis_time;
  private ForkData fork_data;

  // Validator registry
  public ArrayList<ValidatorRecord> validator_registry;
  private UInt64 validator_registry_latest_change_slot;
  public UInt64 validator_registry_exit_count;
  public Hash validator_registry_delta_chain_tip;

  // Randomness and committees
  private Hash randao_mix;
  private Hash next_seed;
  public ArrayList<ArrayList<ShardCommittee>> shard_committees_at_slots;
  public ArrayList<ArrayList<Integer>> persistent_committees;
  private ArrayList<ShardReassignmentRecord> persistent_committee_reassignments;

  // Finality
  private UInt64 previous_justified_slot;
  private UInt64 justified_slot;
  private UInt64 justification_bitfield;
  private UInt64 finalized_slot;

  // Recent state
  private ArrayList<CrosslinkRecord> latest_crosslinks;
  private ArrayList<Hash> latest_block_roots;
  public ArrayList<Double> latest_penalized_exit_balances;
  private ArrayList<PendingAttestationRecord> latest_attestations;
  private ArrayList<Hash> batched_block_roots;

  // PoW receipt root
  private Hash processed_pow_receipt_root;
  private ArrayList<CandidatePoWReceiptRootRecord> candidate_pow_receipt_roots;


  // Default Constructor
  public BeaconState()
  {
    //TODO: temp to allow it to run in demo mode
    this.slot = UInt64.MIN_VALUE;
  }

  public BeaconState(BeaconState state){
    // deep copy
    this.slot = state.slot;
  }

  public BeaconState(
      // Misc
      UInt64 slot, UInt64 genesis_time, ForkData fork_data,
      // Validator registry
      ArrayList<ValidatorRecord> validator_registry, UInt64 validator_registry_latest_change_slot,
      UInt64 validator_registry_exit_count, Hash validator_registry_delta_chain_tip,
      // Randomness and committees
      Hash randao_mix, Hash next_seed, ArrayList<ArrayList<ShardCommittee>> shard_committees_at_slots,
      ArrayList<ArrayList<Integer>> persistent_committees,
      ArrayList<ShardReassignmentRecord> persistent_committee_reassignments,
      // Finality
      UInt64 previous_justified_slot, UInt64 justified_slot, UInt64 justification_bitfield,
      UInt64 finalized_slot,
      // Recent state
      ArrayList<CrosslinkRecord> latest_crosslinks, ArrayList<Hash> latest_block_roots,
      ArrayList<Double>  latest_penalized_exit_balances, ArrayList<PendingAttestationRecord> latest_attestations,
      ArrayList<Hash> batched_block_roots,
      // PoW receipt root
      Hash processed_pow_receipt_root, ArrayList<CandidatePoWReceiptRootRecord> candidate_pow_receipt_roots) {

    // Misc
    this.slot = slot;
    this.genesis_time = genesis_time;
    this.fork_data = fork_data;

    // Validator registry
    this.validator_registry = validator_registry;
    this.validator_registry_latest_change_slot = validator_registry_latest_change_slot;
    this.validator_registry_exit_count = validator_registry_exit_count;
    this.validator_registry_delta_chain_tip = validator_registry_delta_chain_tip;

    // Randomness and committees
    this.randao_mix = randao_mix;
    this.next_seed = next_seed;
    this.shard_committees_at_slots = shard_committees_at_slots;
    this.persistent_committees = persistent_committees;
    this.persistent_committee_reassignments = persistent_committee_reassignments;

    // Finality
    this.previous_justified_slot = previous_justified_slot;
    this.justified_slot = justified_slot;
    this.justification_bitfield = justification_bitfield;
    this.finalized_slot = finalized_slot;

    // Recent state
    this.latest_crosslinks = latest_crosslinks;
    this.latest_block_roots = latest_block_roots;
    this.latest_penalized_exit_balances = latest_penalized_exit_balances;
    this.latest_attestations = latest_attestations;
    this.batched_block_roots = batched_block_roots;

    // PoW receipt root
    this.processed_pow_receipt_root = processed_pow_receipt_root;
    this.candidate_pow_receipt_roots = candidate_pow_receipt_roots;

  }

  /**
  * @return the slot
  */
  public UInt64 getSlot(){
    return this.slot;
  }

  /**
   * @param slot
   */
  public void setSlot(UInt64 slot){
    this.slot = slot;
  }

  public void incrementSlot(){
    this.slot = this.slot.increment();
  }


  /**
   * Update the validator status with the given ``index`` to ``new_status``.
   * Handle other general accounting related to this status update.
   * Note that this function mutates ``state``.
   * @param index
   * @param new_status
   */
  private void update_validator_status(int index, int new_status) {
    if (new_status == ACTIVE) {
      activate_validator(index);
    }
    if (new_status == ACTIVE_PENDING_EXIT) {
      initiate_validator_exit(index);
    }
    if (new_status == EXITED_WITH_PENALTY || new_status == EXITED_WITHOUT_PENALTY) {
      exit_validator(index, new_status);
    }
  }

  /**
   * Activate the validator with the given ``index``.
   * Note that this function mutates ``state``.
   * @param index
   */
  @VisibleForTesting
  public void activate_validator(int index) {
    ValidatorRecord validator = validator_registry.get(index);
    if (validator.status.getValue() != PENDING_ACTIVATION) {
      return;
    }

    validator.status = UInt64.valueOf(ACTIVE);
    validator.latest_status_change_slot = slot;
    validator_registry_delta_chain_tip =
        get_new_validator_registry_delta_chain_tip(validator_registry_delta_chain_tip,
        index, toIntExact(validator.pubkey.getValue()), ACTIVATION);
  }


  /**
   * Initiate exit for the validator with the given ``index``.
   * Note that this function mutates ``state``.
   * @param index
   */
  @VisibleForTesting
  public void initiate_validator_exit(int index) {
    ValidatorRecord validator = validator_registry.get(index);
    if (validator.status.getValue() != ACTIVE) {
      return;
    }

    validator.status = UInt64.valueOf(ACTIVE_PENDING_EXIT);
    validator.latest_status_change_slot = slot;
  }


  /**
   * Exit the validator with the given ``index``.
   * Note that this function mutates ``state``.
   * @param index
   * @param new_status
   */
  @VisibleForTesting
  public void exit_validator(int index, int new_status) {
    ValidatorRecord validator = validator_registry.get(index);
    UInt64 prev_status = validator.status;

    if (prev_status.getValue() == EXITED_WITH_PENALTY) {
      return;
    }

    validator.status = UInt64.valueOf(new_status);
    validator.latest_status_change_slot = slot;

    if (new_status == EXITED_WITH_PENALTY) {
      int lpeb_index = toIntExact(slot.getValue()) / COLLECTIVE_PENALTY_CALCULATION_PERIOD;
      latest_penalized_exit_balances.set(lpeb_index,
          latest_penalized_exit_balances.get(lpeb_index) + get_effective_balance(validator));

      ValidatorRecord whistleblower = validator_registry.get(get_beacon_proposer_index(toIntExact(slot.getValue())));

      whistleblower.balance = whistleblower.balance + validator.balance / WHISTLEBLOWER_REWARD_QUOTIENT;
      validator.balance = validator.balance - validator.balance / WHISTLEBLOWER_REWARD_QUOTIENT;
    }

    if (prev_status.getValue() == EXITED_WITHOUT_PENALTY){
      return;
    }

    // The following updates only occur if not previous exited
    validator_registry_exit_count = validator_registry_exit_count.increment();
    validator.exit_count = validator_registry_exit_count;
    validator_registry_delta_chain_tip = get_new_validator_registry_delta_chain_tip(
        validator_registry_delta_chain_tip, index, toIntExact(validator.pubkey.getValue()), EXIT);

    // Remove validator from persistent committees
    for (int i = 0; i < persistent_committees.size(); i++) {
      ArrayList<Integer> committee = persistent_committees.get(i);
      for (int j = 0; j < committee.size(); j++) {
        if (committee.get(j) == index) {
          // Pop validator_index from committee
          ArrayList<Integer> new_committee = new ArrayList<Integer>(committee.subList(0, i));
          new_committee.addAll(committee.subList(i+1, committee.size()));
          persistent_committees.set(i, new_committee);
          break;
        }
      }
    }
  }

  /**
   * Returns the beacon proposer index for the ``slot``.
   * @param slot
   * @return
   */
  private int get_beacon_proposer_index(int slot) {
    int[] first_committee = get_shard_committees_at_slot(slot).get(0).committee;
    return first_committee[slot % first_committee.length];
  }

  /**
   * Returns the ``ShardCommittee`` for the ``slot``.
   * @param slot
   * @return
   */
  private ArrayList<ShardCommittee> get_shard_committees_at_slot(int slot) {
    int earliest_slot_in_array = toIntExact(this.slot.getValue()) - (toIntExact(this.slot.getValue()) % EPOCH_LENGTH)
        - EPOCH_LENGTH;
    assert earliest_slot_in_array <= slot;
    assert slot < (earliest_slot_in_array + EPOCH_LENGTH * 2);

    return shard_committees_at_slots.get(slot - earliest_slot_in_array);
  }

  /**
   * Compute the next root in the validator registry delta chain.
   * @param current_validator_registry_delta_chain_tip
   * @param validator_index
   * @param pubkey
   * @param flag
   * @return
   */
  private Hash get_new_validator_registry_delta_chain_tip(Hash current_validator_registry_delta_chain_tip,
                                                 int validator_index, int pubkey, int flag) {
    return Hash.hash(hash_tree_root(
        new ValidatorRegistryDeltaBlock(current_validator_registry_delta_chain_tip, validator_index,
            UInt384.valueOf(pubkey), UInt64.valueOf(flag))));
  }

  /**
   * Returns the effective balance (also known as "balance at stake") for the ``validator``.
   * @param validator
   * @return
   */
  private double get_effective_balance(ValidatorRecord validator) {
    return Math.min(validator.balance, MAX_DEPOSIT * GWEI_PER_ETH);
  }


  static class BeaconStateHelperFunctions {

    /**
     * Converts byte[] to int.
     *
     * @param src   byte[]
     * @param pos   Index in Byte[] array
     * @return      converted int
     * @throws IllegalArgumentException if pos is a negative value.
     */
    @VisibleForTesting
    static int bytes3ToInt(Hash src, int pos) {
      checkArgument(pos >= 0, "Expected positive pos but got %s", pos);
      return ((src.extractArray()[pos] & 0xF) << 16) |
          ((src.extractArray()[pos + 1] & 0xFF) << 8) |
          (src.extractArray()[pos + 2] & 0xFF);
    }


    /**
     * Returns the shuffled ``values`` with seed as entropy.
     *
     * @param values    The array.
     * @param seed      Initial seed value used for randomization.
     * @return          The shuffled array.
     */
    @VisibleForTesting
    static Object[] shuffle(Object[] values, Hash seed) {

      int values_count = values.length;

      // Entropy is consumed from the seed in 3-byte (24 bit) chunks.
      int rand_bytes = 3;
      // The highest possible result of the RNG.
      int rand_max = (int) Math.pow(2, (rand_bytes * 8) - 1);

      // The range of the RNG places an upper-bound on the size of the list that
      // may be shuffled. It is a logic error to supply an oversized list.
      assert values_count < rand_max;

      Object[] output = values.clone();
      Hash source = seed;
      int index = 0;

      while (index < values_count - 1) {
        // Re-hash the `source` to obtain a new pattern of bytes.
        source = Hash.hash(source);

        // List to hold values for swap below.
        Object tmp;

        // Iterate through the `source` bytes in 3-byte chunks
        for (int position = 0; position < (32 - (32 % rand_bytes)); position += rand_bytes) {
          // Determine the number of indices remaining in `values` and exit
          // once the last index is reached.
          int remaining = values_count - index;
          if (remaining == 1) break;

          // Read 3-bytes of `source` as a 24-bit big-endian integer.
          int sample_from_source = bytes3ToInt(source, position);


          // Sample values greater than or equal to `sample_max` will cause
          // modulo bias when mapped into the `remaining` range.
          int sample_max = rand_max - rand_max % remaining;

          // Perform a swap if the consumed entropy will not cause modulo bias.
          if (sample_from_source < sample_max) {
            // Select a replacement index for the current index
            int replacement_position = (sample_from_source % remaining) + index;
            // Swap the current index with the replacement index.
            tmp = output[index];
            output[index] = output[replacement_position];
            output[replacement_position] = tmp;
            index += 1;
          }

        }

      }

      return output;
    }


    /**
     * Splits ``values`` into ``split_count`` pieces.
     *
     * @param values          The original list of validators.
     * @param split_count     The number of pieces to split the array into.
     * @return                The list of validators split into N pieces.
     */
    static Object[] split(Object[] values, int split_count) {
      checkArgument(split_count > 0, "Expected positive split_count but got %s", split_count);

      int list_length = values.length;

      Object[] split_arr = new Object[split_count];

      for (int i = 0; i < split_count; i++) {
        int startIndex = list_length * i / split_count;
        int endIndex = list_length * (i + 1) / split_count;
        Object[] new_split = Arrays.copyOfRange(values, startIndex, endIndex);
        split_arr[i] = new_split;
      }

      return split_arr;

    }

    /**
     * A helper method for readability.
     */
    static int clamp(int minval, int maxval, int x) {
      if (x <= minval) return minval;
      if (x >= maxval) return maxval;
      return x;
    }



  }

}
