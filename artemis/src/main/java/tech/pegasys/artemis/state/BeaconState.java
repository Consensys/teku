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
import static tech.pegasys.artemis.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.Constants.EMPTY_SIGNATURE;
import static tech.pegasys.artemis.Constants.EPOCH_LENGTH;
import static tech.pegasys.artemis.Constants.EXIT;
import static tech.pegasys.artemis.Constants.EXITED_WITHOUT_PENALTY;
import static tech.pegasys.artemis.Constants.EXITED_WITH_PENALTY;
import static tech.pegasys.artemis.Constants.GWEI_PER_ETH;
import static tech.pegasys.artemis.Constants.MAX_DEPOSIT;
import static tech.pegasys.artemis.Constants.PENDING_ACTIVATION;
import static tech.pegasys.artemis.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.Constants.ZERO_BALANCE_VALIDATOR_TTL;
import static tech.pegasys.artemis.ethereum.core.TreeHash.hash_tree_root;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;

import tech.pegasys.artemis.datastructures.beaconchainoperations.DepositInput;
import tech.pegasys.artemis.datastructures.beaconchainstate.CandidatePoWReceiptRootRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.CrosslinkRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.ForkData;
import tech.pegasys.artemis.datastructures.beaconchainstate.PendingAttestationRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.ShardCommittee;
import tech.pegasys.artemis.datastructures.beaconchainstate.ShardReassignmentRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRecord;
import tech.pegasys.artemis.datastructures.beaconchainstate.ValidatorRegistryDeltaBlock;
import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;



public class BeaconState {

  // Misc
  private UInt64 slot;
  private UInt64 genesis_time;
  private ForkData fork_data;

  // Validator registry
  private ArrayList<ValidatorRecord> validator_registry;
  private ArrayList<Double> validator_balances;
  private UInt64 validator_registry_latest_change_slot;
  private UInt64 validator_registry_exit_count;
  private Hash validator_registry_delta_chain_tip;

  // Randomness and committees
  private ArrayList<Hash> latest_randao_mixes;
  private ArrayList<Hash> latest_vdf_outputs;
  private ArrayList<ArrayList<ShardCommittee>> shard_committees_at_slots;
  private ArrayList<ArrayList<Integer>> persistent_committees;
  private ArrayList<ShardReassignmentRecord> persistent_committee_reassignments;

  // Finality
  private UInt64 previous_justified_slot;
  private UInt64 justified_slot;
  private UInt64 justification_bitfield;
  private UInt64 finalized_slot;

  // Recent state
  private ArrayList<CrosslinkRecord> latest_crosslinks;
  private ArrayList<Hash> latest_block_roots;
  private ArrayList<Double> latest_penalized_exit_balances;
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

  public static BeaconState deepCopy(BeaconState state){
    Gson gson = new GsonBuilder().registerTypeAdapter(Bytes32.class, new InterfaceAdapter<Bytes32>()).create();
    BeaconState deepCopy = gson.fromJson(gson.toJson(state), BeaconState.class);
    return deepCopy;
  }

  BeaconState(
      // Misc
      UInt64 slot, UInt64 genesis_time, ForkData fork_data,
      // Validator registry
      ArrayList<ValidatorRecord> validator_registry, ArrayList<Double> validator_balances,
      UInt64 validator_registry_latest_change_slot, UInt64 validator_registry_exit_count,
      Hash validator_registry_delta_chain_tip,
      // Randomness and committees
      ArrayList<Hash> latest_randao_mixes, ArrayList<Hash> latest_vdf_outputs, ArrayList<ArrayList<ShardCommittee>> shard_committees_at_slots,
      ArrayList<ArrayList<Integer>> persistent_committees,
      ArrayList<ShardReassignmentRecord> persistent_committee_reassignments,
      // Finality
      UInt64 previous_justified_slot, UInt64 justified_slot, UInt64 justification_bitfield,
      UInt64 finalized_slot,
      // Recent state
      ArrayList<CrosslinkRecord> latest_crosslinks, ArrayList<Hash> latest_block_roots,
      ArrayList<Double> latest_penalized_exit_balances, ArrayList<PendingAttestationRecord> latest_attestations,
      ArrayList<Hash> batched_block_roots,
      // PoW receipt root
      Hash processed_pow_receipt_root, ArrayList<CandidatePoWReceiptRootRecord> candidate_pow_receipt_roots) {

    // Misc
    this.slot = slot;
    this.genesis_time = genesis_time;
    this.fork_data = fork_data;

    // Validator registry
    this.validator_registry = validator_registry;
    this.validator_balances = validator_balances;
    this.validator_registry_latest_change_slot = validator_registry_latest_change_slot;
    this.validator_registry_exit_count = validator_registry_exit_count;
    this.validator_registry_delta_chain_tip = validator_registry_delta_chain_tip;

    // Randomness and committees
    this.latest_randao_mixes = latest_randao_mixes;
    this.latest_vdf_outputs = latest_vdf_outputs;
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
   *
   * @param validators
   * @param current_slot
   * @return
   */
  private int min_empty_validator_index(ArrayList<ValidatorRecord> validators, ArrayList<Double> validator_balances,
                                        int current_slot) {
    for (int i = 0; i < validators.size(); i++) {
      ValidatorRecord v = validators.get(i);
      double vbal = validator_balances.get(i);
      if (vbal == 0 && v.getLatest_status_change_slot().getValue() + ZERO_BALANCE_VALIDATOR_TTL
          <= current_slot) {
        return i;
      }
    }
    return validators.size();
  }

  /**
   *
   * @param state
   * @param pubkey
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @param randao_commitment
   * @return
   */
  private boolean validate_proof_of_possession(BeaconState state, int pubkey, Bytes32 proof_of_possession,
                                               Hash withdrawal_credentials, Hash randao_commitment) {
    DepositInput proof_of_possession_data = new DepositInput(UInt384.valueOf(pubkey), withdrawal_credentials,
        randao_commitment, EMPTY_SIGNATURE);

    UInt384 signature = UInt384.valueOf(BytesValue.wrap(proof_of_possession.extractArray()).getInt(0));
    UInt64 domain = UInt64.valueOf(get_domain(state.fork_data, toIntExact(state.slot.getValue()), DOMAIN_DEPOSIT));
    return bls_verify(UInt384.valueOf(pubkey), hash_tree_root(proof_of_possession_data), signature, domain);

  }

  /**
   * Process a deposit from Ethereum 1.0.
   * Note that this function mutates ``state``.
   * @param state
   * @param pubkey
   * @param deposit
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @param randao_commitment
   * @return
   */
  public int process_deposit(BeaconState state, int pubkey, double deposit, Bytes32 proof_of_possession,
                              Hash withdrawal_credentials, Hash randao_commitment) {
    assert validate_proof_of_possession(state, pubkey, proof_of_possession, withdrawal_credentials, randao_commitment);

    UInt384[] validator_pubkeys = new UInt384[state.validator_registry.size()];
    boolean validatorsPubkeysContainPubkey = false;
    for (int i=0; i < validator_pubkeys.length; i++) {
      validator_pubkeys[i] = state.validator_registry.get(i).getPubkey();
    }

    int index = -1;

    if (indexOfPubkey(validator_pubkeys, pubkey) == -1) {
      // Add new validator
      ValidatorRecord validator = new ValidatorRecord(pubkey, withdrawal_credentials, randao_commitment,
          UInt64.MIN_VALUE, UInt64.valueOf(PENDING_ACTIVATION), state.slot, UInt64.MIN_VALUE, UInt64.MIN_VALUE,
          UInt64.MIN_VALUE);

      ArrayList<ValidatorRecord> validators_copy = new ArrayList<ValidatorRecord>();
      validators_copy.addAll(validator_registry);
      index = min_empty_validator_index(validators_copy, validator_balances, toIntExact(slot.getValue()));
      if (index == validators_copy.size()) {
        state.validator_registry.add(validator);
        state.validator_balances.add(deposit);
        index = state.validator_registry.size() - 1;
      } else {
        state.validator_registry.set(index, validator);
        state.validator_balances.set(index, deposit);
      }
    } else {
      // Increase balance by deposit
      index = indexOfPubkey(validator_pubkeys, pubkey);
      ValidatorRecord validator = state.validator_registry.get(index);
      assert validator.getWithdrawal_credentials().equals(withdrawal_credentials);

      state.validator_balances.set(index, state.validator_balances.get(index) + deposit);
    }

    return index;
  }


  /**
   * Helper function to find the index of the pubkey the array of validators' pubkeys.
   * @param validator_pubkeys
   * @param pubkey
   * @return
   */
  private int indexOfPubkey(UInt384[] validator_pubkeys, int pubkey) {
    for (int i = 0; i < validator_pubkeys.length; i++) {
      if (validator_pubkeys[i].getValue() == pubkey) {
        return i;
      }
    }
    return -1;
  }

  /**
   *
   * @param fork_data
   * @param slot
   * @param domain_type
   * @return
   */
  private int get_domain(ForkData fork_data, int slot, int domain_type) {
    return get_fork_version(fork_data, slot) * (int) Math.pow(2, 32) + domain_type;
  }

  /**
   *
   * @param fork_data
   * @param slot
   * @return
   */
  private int get_fork_version(ForkData fork_data, int slot) {
    if (slot < fork_data.getFork_slot().getValue()) {
      return toIntExact(fork_data.getPre_fork_version().getValue());
    } else {
      return toIntExact(fork_data.getPost_fork_version().getValue());
    }
  }

      /**
       * Update the validator status with the given ``index`` to ``new_status``.
       * Handle other general accounting related to this status update.
       * Note that this function mutates ``state``.
       * @param index
       * @param new_status
       */
  private void update_validator_status(BeaconState state, int index, int new_status) {
    if (new_status == ACTIVE) {
      activate_validator(index);
    }
    if (new_status == ACTIVE_PENDING_EXIT) {
      initiate_validator_exit(index);
    }
    if (new_status == EXITED_WITH_PENALTY || new_status == EXITED_WITHOUT_PENALTY) {
      exit_validator(state, index, new_status);
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
    if (validator.getStatus().getValue() != PENDING_ACTIVATION) {
      return;
    }

    validator.setStatus(UInt64.valueOf(ACTIVE));
    validator.setLatest_status_change_slot(slot);
    validator_registry_delta_chain_tip =
        get_new_validator_registry_delta_chain_tip(validator_registry_delta_chain_tip,
        index, toIntExact(validator.getPubkey().getValue()), ACTIVATION);
  }


  /**
   * Initiate exit for the validator with the given ``index``.
   * Note that this function mutates ``state``.
   * @param index
   */
  @VisibleForTesting
  public void initiate_validator_exit(int index) {
    ValidatorRecord validator = validator_registry.get(index);
    if (validator.getStatus().getValue() != ACTIVE) {
      return;
    }

    validator.setStatus(UInt64.valueOf(ACTIVE_PENDING_EXIT));
    validator.setLatest_status_change_slot(slot);
  }


  /**
   * Exit the validator with the given ``index``.
   * Note that this function mutates ``state``.
   * @param index
   * @param new_status
   */
  @VisibleForTesting
  public void exit_validator(BeaconState state, int index, int new_status) {
    ValidatorRecord validator = state.validator_registry.get(index);
    long prev_status = validator.getStatus().getValue();

    if (prev_status == EXITED_WITH_PENALTY) {
      return;
    }

    validator.setStatus(UInt64.valueOf(new_status));
    validator.setLatest_status_change_slot(state.slot);

    if (new_status == EXITED_WITH_PENALTY) {
      int lpeb_index = toIntExact(state.slot.getValue()) / COLLECTIVE_PENALTY_CALCULATION_PERIOD;
      latest_penalized_exit_balances.set(lpeb_index,
          latest_penalized_exit_balances.get(lpeb_index) + get_effective_balance(state, index));

      int whistleblower_index = get_beacon_proposer_index(state, toIntExact(state.slot.getValue()));
      double whistleblower_reward = get_effective_balance(state, index) / WHISTLEBLOWER_REWARD_QUOTIENT;

      double new_whistleblower_balance = state.validator_balances.get(whistleblower_index) + whistleblower_reward;

      state.validator_balances.set(whistleblower_index, new_whistleblower_balance);
      double new_balance = state.validator_balances.get(index) - whistleblower_reward;
      state.validator_balances.set(index, new_balance);
      System.out.println("set");
    }

    if (prev_status == EXITED_WITHOUT_PENALTY){
      return;
    }

    // The following updates only occur if not previous exited
    state.setValidator_registry_exit_count(state.getValidator_registry_exit_count().increment());
    validator.setExit_count(state.getValidator_registry_exit_count());
    state.setValidator_registry_delta_chain_tip(get_new_validator_registry_delta_chain_tip(
        state.getValidator_registry_delta_chain_tip(), index, toIntExact(validator.getPubkey().getValue()), EXIT));

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
   * @param state
   * @param slot
   * @return
   */
  private int get_beacon_proposer_index(BeaconState state, int slot) {
    int[] first_committee = get_shard_committees_at_slot(state, slot).get(0).getCommittee();
    return first_committee[slot % first_committee.length];
  }

  /**
   * Returns the ``ShardCommittee`` for the ``slot``.
   * @param slot
   * @return
   */
  private ArrayList<ShardCommittee> get_shard_committees_at_slot(BeaconState state, int slot) {
    int earliest_slot_in_array = toIntExact(state.slot.getValue()) - (toIntExact(state.slot.getValue()) % EPOCH_LENGTH)
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
   * Returns the effective balance (also known as "balance at stake") for a ``validator`` with the given ``index``.
   * @param state
   * @param index
   * @return
   */
  private double get_effective_balance(BeaconState state, int index) {
    return Math.min(state.validator_balances.get(index).intValue(), MAX_DEPOSIT * GWEI_PER_ETH);
  }

  public ArrayList<Hash> getLatest_randao_mixes() {
    return latest_randao_mixes;
  }

  public void setLatest_randao_mixes(ArrayList<Hash> latest_randao_mixes) {
    this.latest_randao_mixes = latest_randao_mixes;
  }

  public ArrayList<Hash> getLatest_vdf_outputs() {
    return latest_vdf_outputs;
  }

  public void setLatest_vdf_outputs(ArrayList<Hash> latest_vdf_outputs) {
    this.latest_vdf_outputs = latest_vdf_outputs;
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



  public UInt64 getGenesis_time() {
    return genesis_time;
  }

  public void setGenesis_time(UInt64 genesis_time) {
    this.genesis_time = genesis_time;
  }

  public ForkData getFork_data() {
    return fork_data;
  }

  public void setFork_data(ForkData fork_data) {
    this.fork_data = fork_data;
  }

  public ArrayList<ValidatorRecord> getValidator_registry() { return validator_registry; }

  public void setValidator_registry(ArrayList<ValidatorRecord> validator_registry) {
    this.validator_registry = validator_registry;
  }

  public ArrayList<Double> getValidator_balances() { return validator_balances; }

  public void setValidator_balances(ArrayList<Double> validator_balances) {
    this.validator_balances = validator_balances;
  }

  public UInt64 getValidator_registry_exit_count() {
    return validator_registry_exit_count;
  }

  public void setValidator_registry_exit_count(UInt64 validator_registry_exit_count) {
    this.validator_registry_exit_count = validator_registry_exit_count;
  }

  public Hash getValidator_registry_delta_chain_tip() { return validator_registry_delta_chain_tip; }

  public void setValidator_registry_delta_chain_tip(Hash validator_registry_delta_chain_tip) {
    this.validator_registry_delta_chain_tip = validator_registry_delta_chain_tip;
  }

  public UInt64 getValidator_registry_latest_change_slot() {
    return validator_registry_latest_change_slot;
  }

  public void setValidator_registry_latest_change_slot(UInt64 validator_registry_latest_change_slot) {
    this.validator_registry_latest_change_slot = validator_registry_latest_change_slot;
  }

  public ArrayList<ShardReassignmentRecord> getPersistent_committee_reassignments() {
    return persistent_committee_reassignments;
  }

  public void setPersistent_committee_reassignments(ArrayList<ShardReassignmentRecord> persistent_committee_reassignments) {
    this.persistent_committee_reassignments = persistent_committee_reassignments;
  }

  public UInt64 getPrevious_justified_slot() {
    return previous_justified_slot;
  }

  public void setPrevious_justified_slot(UInt64 previous_justified_slot) {
    this.previous_justified_slot = previous_justified_slot;
  }

  public UInt64 getJustification_bitfield() {
    return justification_bitfield;
  }

  public void setJustification_bitfield(UInt64 justification_bitfield) {
    this.justification_bitfield = justification_bitfield;
  }

  public ArrayList<CrosslinkRecord> getLatest_crosslinks() {
    return latest_crosslinks;
  }

  public void setLatest_crosslinks(ArrayList<CrosslinkRecord> latest_crosslinks) {
    this.latest_crosslinks = latest_crosslinks;
  }

  public ArrayList<Hash> getLatest_block_roots() {
    return latest_block_roots;
  }

  public void setLatest_block_roots(ArrayList<Hash> latest_block_roots) {
    this.latest_block_roots = latest_block_roots;
  }

  public ArrayList<PendingAttestationRecord> getLatest_attestations() {
    return latest_attestations;
  }

  public void setLatest_attestations(ArrayList<PendingAttestationRecord> latest_attestations) {
    this.latest_attestations = latest_attestations;
  }

  public ArrayList<Hash> getBatched_block_roots() {
    return batched_block_roots;
  }

  public void setBatched_block_roots(ArrayList<Hash> batched_block_roots) {
    this.batched_block_roots = batched_block_roots;
  }

  public UInt64 getJustified_slot() {
    return justified_slot;
  }

  public void setJustified_slot(UInt64 justified_slot) {
    this.justified_slot = justified_slot;
  }

  public UInt64 getFinalized_slot() {
    return finalized_slot;
  }

  public void setFinalized_slot(UInt64 finalized_slot) {
    this.finalized_slot = finalized_slot;
  }

  public Hash getProcessed_pow_receipt_root() {
    return processed_pow_receipt_root;
  }

  public void setProcessed_pow_receipt_root(Hash processed_pow_receipt_root) {
    this.processed_pow_receipt_root = processed_pow_receipt_root;
  }

  public ArrayList<CandidatePoWReceiptRootRecord> getCandidate_pow_receipt_roots() {
    return candidate_pow_receipt_roots;
  }

  public void setCandidate_pow_receipt_roots(ArrayList<CandidatePoWReceiptRootRecord> candidate_pow_receipt_roots) {
    this.candidate_pow_receipt_roots = candidate_pow_receipt_roots;
  }

  public ArrayList<ArrayList<ShardCommittee>> getShard_committees_at_slots() {
    return shard_committees_at_slots;
  }

  public void setShard_committees_at_slots(ArrayList<ArrayList<ShardCommittee>> shard_committees_at_slots) {
    this.shard_committees_at_slots = shard_committees_at_slots;
  }

  public ArrayList<ArrayList<Integer>> getPersistent_committees() {
    return persistent_committees;
  }

  public void setPersistent_committees(ArrayList<ArrayList<Integer>> persistent_committees) {
    this.persistent_committees = persistent_committees;
  }

  public ArrayList<Double> getLatest_penalized_exit_balances() {
    return latest_penalized_exit_balances;
  }

  public void setLatest_penalized_exit_balances(ArrayList<Double> latest_penalized_exit_balances) {
    this.latest_penalized_exit_balances = latest_penalized_exit_balances;
  }

}
