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

package tech.pegasys.artemis.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.*;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.crypto.Hash;
import org.checkerframework.checker.signedness.qual.Unsigned;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataVote;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositInput;
import tech.pegasys.artemis.datastructures.state.CrosslinkRecord;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.PendingAttestationRecord;
import tech.pegasys.artemis.datastructures.state.ShardCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.state.ValidatorRegistryDeltaBlock;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.util.CrosslinkCommitteeUtil;
import tech.pegasys.artemis.statetransition.util.TreeHashUtil;
import tech.pegasys.artemis.statetransition.util.ValidatorsUtil;

public class BeaconState {
  // Misc
  UnsignedLong slot;
  UnsignedLong genesis_time;
  Fork fork; //For versioning hard forks

  //Validator registry
  Validators validator_registry;
  ArrayList<UnsignedLong> validator_balances;
  UnsignedLong validator_registry_update_epoch;

  //Randomness and committees
  ArrayList<Bytes32> latest_randao_mixes;
  UnsignedLong previous_epoch_start_shard;
  UnsignedLong current_epoch_start_shard;
  UnsignedLong previous_calculation_epoch;
  UnsignedLong current_calculation_epoch;

  //Finality
  Bytes32 previous_epoch_seed;
  Bytes32 current_epoch_seed;
  UnsignedLong previous_justified_epoch;
  UnsignedLong justified_epoch;
  UnsignedLong justification_bitfield;
  UnsignedLong finalized_epoch;

  //Recent state
  ArrayList<CrosslinkRecord> latest_crosslinks;
  ArrayList<Bytes32> latest_block_roots;
  ArrayList<Bytes32> latest_index_roots;
  ArrayList<UnsignedLong> latest_penalized_balances; //Balances penalized at every withdrawal period
  ArrayList<PendingAttestationRecord> latest_attestations;
  ArrayList<Bytes32> batched_block_roots;

  //Ethereum 1.0 chain data
  Eth1Data latest_eth1_data;
  ArrayList<Eth1DataVote> eth1_data_votes;

  public static BeaconState deepCopy(BeaconState state) {
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(Bytes32.class, new InterfaceAdapter<Bytes32>())
            .registerTypeAdapter(Bytes48.class, new InterfaceAdapter<Bytes48>())
            .create();
    return gson.fromJson(gson.toJson(state), BeaconState.class);
  }

  public BeaconState(
      // Misc
      UnsignedLong slot,
      UnsignedLong genesis_time,
      Fork fork, //For versioning hard forks

      //Validator registry
      Validators validator_registry,
      ArrayList<UnsignedLong> validator_balances,
      UnsignedLong validator_registry_update_epoch,

      //Randomness and committees
      ArrayList<Bytes32> latest_randao_mixes,
      UnsignedLong previous_epoch_start_shard,
      UnsignedLong current_epoch_start_shard,
      UnsignedLong previous_calculation_epoch,
      UnsignedLong current_calculation_epoch,

      //Finality
      Bytes32 previous_epoch_seed,
      Bytes32 current_epoch_seed,
      UnsignedLong previous_justified_epoch,
      UnsignedLong justified_epoch,
      UnsignedLong justification_bitfield,
      UnsignedLong finalized_epoch,

      //Recent state
      ArrayList<CrosslinkRecord> latest_crosslinks,
      ArrayList<Bytes32> latest_block_roots,
      ArrayList<Bytes32> latest_index_roots,
      ArrayList<UnsignedLong> latest_penalized_balances, //Balances penalized at every withdrawal period
      ArrayList<PendingAttestationRecord> latest_attestations,
      ArrayList<Bytes32> batched_block_roots,

      //Ethereum 1.0 chain data
      Eth1Data latest_eth1_data,
      ArrayList<Eth1DataVote> eth1_data_votes)
  {
    this.slot = slot;
    this.genesis_time = genesis_time;
    this.fork = fork;

    this.validator_registry = validator_registry;
    this.validator_balances = validator_balances;
    this.validator_registry_update_epoch = validator_registry_update_epoch;

    this.latest_randao_mixes = latest_randao_mixes;
    this.previous_epoch_start_shard = previous_epoch_start_shard;
    this.current_epoch_start_shard = current_epoch_start_shard;
    this.previous_calculation_epoch = previous_calculation_epoch;
    this.current_calculation_epoch = current_calculation_epoch;

    this.previous_epoch_seed = previous_epoch_seed;
    this.current_epoch_seed = current_epoch_seed;
    this.previous_justified_epoch = previous_justified_epoch;
    this.justified_epoch = justified_epoch;
    this.justification_bitfield = justification_bitfield;
    this.finalized_epoch = finalized_epoch;

    this.latest_crosslinks = latest_crosslinks;
    this.latest_block_roots = latest_block_roots;
    this.latest_index_roots = latest_index_roots;
    this.latest_penalized_balances = latest_penalized_balances;
    this.latest_attestations = latest_attestations;
    this.batched_block_roots = batched_block_roots;

    this.latest_eth1_data = latest_eth1_data;
    this.eth1_data_votes = eth1_data_votes;
  }

  @VisibleForTesting
  @SuppressWarnings("ModifiedButNotUsed")
  public BeaconState get_initial_beacon_state(
    ArrayList<Deposit> initial_validator_deposits, UnsignedLong genesis_time, Eth1Data latest_eth1_data) {

    ArrayList<Bytes32> latest_randao_mixes = new ArrayList<>();
    ArrayList<Bytes32> latest_block_roots = new ArrayList<>();
    ArrayList<CrosslinkRecord> latest_crosslinks = new ArrayList<>(SHARD_COUNT);

    for (int i = 0; i < SHARD_COUNT; i++) {
      latest_crosslinks.add(new CrosslinkRecord(Bytes32.ZERO, UnsignedLong.valueOf(GENESIS_SLOT)));
    }

    // todo after update v0.01 constants no longer exist
    //BeaconState state = new BeaconState();
        BeaconState state =
            new BeaconState(
                // Misc
                UnsignedLong.valueOf(GENESIS_SLOT),
                genesis_time,
                new Fork(
                    UnsignedLong.valueOf(GENESIS_FORK_VERSION),
                    UnsignedLong.valueOf(GENESIS_FORK_VERSION),
                    UnsignedLong.valueOf(GENESIS_EPOCH)),

                // Validator registry
                new Validators(),
                new ArrayList<UnsignedLong>(),
                UnsignedLong.valueOf(GENESIS_EPOCH),

                // Randomness and committees
                latest_randao_mixes,
                UnsignedLong.valueOf(GENESIS_START_SHARD),
                UnsignedLong.valueOf(GENESIS_START_SHARD),
                UnsignedLong.valueOf(GENESIS_EPOCH),
                UnsignedLong.valueOf(GENESIS_EPOCH),
                ZERO_HASH,
                ZERO_HASH,

                // Finality
                UnsignedLong.valueOf(GENESIS_EPOCH),
                UnsignedLong.valueOf(GENESIS_EPOCH),
                UnsignedLong.ZERO,
                UnsignedLong.valueOf(GENESIS_EPOCH),

                // Recent state
                latest_crosslinks,
                latest_block_roots,
                new ArrayList<Bytes32>(),
                new ArrayList<UnsignedLong>(),
                new ArrayList<PendingAttestationRecord>(),
                new ArrayList<Bytes32>(),

                // Ethereum 1.0 chain data
                latest_eth1_data,
                new ArrayList<>());

    //Process initial deposits
    for (Deposit validator_deposit : initial_validator_deposits) {
      DepositInput deposit_input = validator_deposit.getDeposit_data().getDeposit_input();
          process_deposit(
              state,
              deposit_input.getPubkey(),
              validator_deposit.getDeposit_data().getValue(),
              deposit_input.getProof_of_possession(),
              deposit_input.getWithdrawal_credentials());
    }

    //Process initial activations
    for(Validator validator: state.getValidator_registry()){
      if(validator.get_effective_balance().compareTo(UnsignedLong.valueOf(MAX_DEPOSIT_AMOUNT)) >= 0) activate_validator(state, validator, true);
    }

    Bytes32 genesis_active_index_root = TreeHashUtil.hash_tree_root(ValidatorsUtil.get_active_validators(state.getValidator_registry(), UnsignedLong.valueOf(GENESIS_EPOCH)));
    for(Bytes32 root: state.getLatest_index_roots()){
      root = genesis_active_index_root;
    }
    state.setCurrent_epoch_seed(generate_seed(state, UnsignedLong.valueOf(GENESIS_EPOCH)));

    return state;
  }

  public static Bytes32 generate_seed(BeaconState state, UnsignedLong epoch) {
    return state.get_randao_mix(state, epoch.plus(UnsignedLong.valueOf(SEED_LOOKAHEAD))).and(state.get_active_index_root(state, epoch));
  }

  public static Bytes32 get_active_index_root(BeaconState state, UnsignedLong epoch) {
    //Return the index root at a recent ``epoch``.
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    assert (current_epoch.minus(UnsignedLong.valueOf(LATEST_INDEX_ROOTS_LENGTH)).plus(UnsignedLong.valueOf(ENTRY_EXIT_DELAY)).compareTo(epoch) < 0) &&
            (epoch.compareTo(current_epoch.plus(UnsignedLong.valueOf(ENTRY_EXIT_DELAY))) <= 0);
    return state.getLatest_index_roots().get(epoch.mod(UnsignedLong.valueOf(LATEST_INDEX_ROOTS_LENGTH)).intValue());
  }

  public static Bytes32 get_randao_mix(BeaconState state, UnsignedLong epoch) {
    //Return the randao mix at a recent ``epoch``.
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    assert (current_epoch.minus(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)).compareTo(epoch) < 0) &&
            (epoch.compareTo(current_epoch) <= 0);
    return state.getLatest_randao_mixes().get(epoch.mod(UnsignedLong.valueOf(LATEST_RANDAO_MIXES_LENGTH)).intValue());
  }


  /**
   * @param validators
   * @param current_slot
   * @return The minimum empty validator index.
   */
  private int min_empty_validator_index(
      ArrayList<Validator> validators, ArrayList<Double> validator_balances, int current_slot) {
    for (int i = 0; i < validators.size(); i++) {
      Validator v = validators.get(i);
      double vbal = validator_balances.get(i);
      // todo getLatest_status_change_slot method no longer exists following the recent update
      //      if (vbal == 0
      //          && v.getLatest_status_change_slot().longValue() + ZERO_BALANCE_VALIDATOR_TTL
      //              <= current_slot) {
      return i;
      //      }
    }
    return validators.size();
  }

  /**
   * @param state
   * @param pubkey
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @return
   */
  private static boolean validate_proof_of_possession(
          BeaconState state,
          Bytes48 pubkey,
          List<Bytes48> proof_of_possession,
          Bytes32 withdrawal_credentials) {
    //Verify the given ``proof_of_possession``.
    DepositInput proof_of_possession_data =
        new DepositInput(pubkey, withdrawal_credentials, proof_of_possession);

    List<Bytes48> signature =
        Arrays.asList(
            Bytes48.leftPad(proof_of_possession.get(0)),
            Bytes48.leftPad(proof_of_possession.get(1)));
    UnsignedLong domain = get_domain(state.getFork(), state.getSlot(), DOMAIN_DEPOSIT);
    return bls_verify(
        pubkey, TreeHashUtil.hash_tree_root(proof_of_possession_data.toBytes()), signature, domain);
  }

  /**
   * Process a deposit from Ethereum 1.0. Note that this function mutates 'state'.
   *
   * @param state
   * @param pubkey
   * @param proof_of_possession
   * @param withdrawal_credentials
   * @param amount
   * @return
   */
  public static void process_deposit(
      BeaconState state,
      Bytes48 pubkey,
      UnsignedLong amount,
      List<Bytes48> proof_of_possession,
      Bytes32 withdrawal_credentials) {
    assert BeaconState.validate_proof_of_possession(
        state,
        pubkey,
        proof_of_possession,
        withdrawal_credentials);

    Validator validator = getValidatorByPubkey(state, pubkey);
    if(validator == null){
      validator = new Validator(pubkey, withdrawal_credentials, FAR_FUTURE_EPOCH, FAR_FUTURE_EPOCH, FAR_FUTURE_EPOCH, FAR_FUTURE_EPOCH, UnsignedLong.ZERO, amount);
      state.getValidator_registry().add(validator);
    }
    else{
      assert(validator.getWithdrawal_credentials().equals(withdrawal_credentials));
      validator.getBalance().plus(amount);
    }
  }

  private static Validator getValidatorByPubkey(BeaconState state, Bytes48 pubkey) {
    for(Validator validator: state.getValidator_registry()){
      if(validator.getPubkey().equals(pubkey)) return validator;
    }
    return null;
  }

  public static boolean isValidatorKeyRegistered(BeaconState state, Bytes48 pubkey) {
    for(Validator validator: state.getValidator_registry()){
      if(validator.getPubkey().equals(pubkey)) return true;
    }
    return false;
  }

  /**
   * Helper function to find the index of the pubkey in the array of validators' pubkeys.
   *
   * @param validator_pubkeys
   * @param pubkey
   * @return The index of the pubkey.
   */
  private int indexOfPubkey(Bytes48[] validator_pubkeys, Bytes48 pubkey) {
    for (int i = 0; i < validator_pubkeys.length; i++) {
      if (validator_pubkeys[i].equals(pubkey)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * @param fork
   * @param slot
   * @param domain_type
   * @return
   */
  private static UnsignedLong get_domain(Fork fork, UnsignedLong slot, int domain_type) {
    return get_fork_version(fork, slot).times(UnsignedLong.valueOf((long) Math.pow(2, 32))).plus(UnsignedLong.valueOf(domain_type));
  }

  /**
   * @param fork
   * @param epoch
   * @return
   */
  public static UnsignedLong get_fork_version(Fork fork, UnsignedLong epoch) {
    if(epoch.compareTo(fork.getEpoch()) < 0) return fork.getPrevious_version();
    else return fork.getCurrent_version();
  }

  /**
   * Activate the validator with the given 'index'. Note that this function mutates 'state'.
   *
   * @param validator the validator.
   */
  @VisibleForTesting
  public void activate_validator(BeaconState state, Validator validator, boolean is_genesis) {
//    Activate the validator of the given ``index``.
//    Note that this function mutates ``state``.
    validator.setActivation_epoch((is_genesis) ? UnsignedLong.valueOf(GENESIS_EPOCH) : BeaconStateUtil.get_entry_exit_effect_epoch(BeaconStateUtil.get_current_epoch(state));
  }

  /**
   * Returns the beacon proposer index for the 'slot'.
   *
   * @param state
   * @param slot
   * @return
   */
  public static int get_beacon_proposer_index(BeaconState state, int slot) {
    ArrayList<Integer> first_committee =
        get_crosslink_committees_at_slot(state, slot).get(0).getCommittee();
    return first_committee.get(slot % first_committee.size());
  }

  /**
   * Returns the 'ShardCommittee' for the 'slot'.
   *
   * @param slot
   * @return
   */
  public static List<ShardCommittee> get_crosslink_committees_at_slot(
      BeaconState state, UnsignedLong slot, boolean registry_change) {
    //Return the list of ``(committee, shard)`` tuples for the ``slot``

    //Note: There are two possible shufflings for crosslink committees for a
    //``slot`` in the next epoch -- with and without a `registry_change`
    UnsignedLong epoch = BeaconStateUtil.slot_to_epoch(slot);
    UnsignedLong current_epoch = BeaconStateUtil.get_current_epoch(state);
    UnsignedLong previous_epoch = ((current_epoch.compareTo(UnsignedLong.valueOf(GENESIS_EPOCH)) > 0) ? current_epoch.minus(UnsignedLong.ONE) : current_epoch);
    UnsignedLong next_epoch = current_epoch.plus(UnsignedLong.ONE);

    assert(previous_epoch.compareTo(epoch) <= 0 && epoch.compareTo(next_epoch) <= 0);

    int committees_per_epoch = 0;
    int current_committees_per_epoch = 0;
    Bytes32 seed = null;
    UnsignedLong shuffling_epoch = null;
    UnsignedLong shuffling_start_shard = null;


    if(epoch.equals(previous_epoch)){
      committees_per_epoch = CrosslinkCommitteeUtil.get_previous_epoch_committee_count(state);
      seed = state.getPrevious_epoch_seed();
      shuffling_epoch = state.getPrevious_calculation_epoch();
      shuffling_start_shard = state.getPrevious_epoch_start_shard();
    }
    else if(epoch.equals(current_epoch)){
      committees_per_epoch = CrosslinkCommitteeUtil.get_current_epoch_committee_count(state);
      seed = state.getCurrent_epoch_seed();
      shuffling_epoch = state.getCurrent_calculation_epoch();
      shuffling_start_shard = state.getCurrent_epoch_start_shard();
    }
    else if(epoch.equals(next_epoch)){
      current_committees_per_epoch = CrosslinkCommitteeUtil.get_current_epoch_committee_count(state);
      committees_per_epoch = CrosslinkCommitteeUtil.get_next_epoch_committee_count(state);
      shuffling_epoch = next_epoch;
    }

    UnsignedLong epochs_since_last_registry_update = current_epoch.minus(state.getValidator_registry_update_epoch());

    if(registry_change){
      seed = generate_seed(state, next_epoch);
      shuffling_start_shard = state.getCurrent_epoch_start_shard().plus(UnsignedLong.valueOf(current_committees_per_epoch)).mod(UnsignedLong.valueOf(SHARD_COUNT));
    }
    else if(epochs_since_last_registry_update.compareTo(UnsignedLong.ONE) > 0 && epochs_since_last_registry_update.mod(UnsignedLong.valueOf(2)).compareTo(UnsignedLong.ZERO) == 0){
      seed = generate_seed(state, next_epoch);
      shuffling_start_shard = state.getCurrent_epoch_start_shard();
    }
    else{
      seed = state.getCurrent_epoch_seed();
      shuffling_start_shard = state.getCurrent_epoch_start_shard();
    }

    List<ShardCommittee> shuffling = get_shuffling(seed, state.getValidator_registry(), shuffling_epoch);

    UnsignedLong offset = slot.mod(UnsignedLong.valueOf(EPOCH_LENGTH));
    int committees_per_slot = Math.round(committees_per_epoch/EPOCH_LENGTH);
    UnsignedLong slot_start_shard = (shuffling_start_shard.plus(UnsignedLong.valueOf(committees_per_slot).times(offset))).mod(UnsignedLong.valueOf(SHARD_COUNT));

    IntStream.range(committees_per_slot).forEach();

  }

  /**
   * Returns the participant indices at for the 'attestation_data' and 'participation_bitfield'.
   *
   * @param state
   * @param attestation_data
   * @param participation_bitfield
   * @return
   */
  public static ArrayList<ShardCommittee> get_attestation_participants(
      BeaconState state, AttestationData attestation_data, byte[] participation_bitfield) {
    // Find the relevant committee
    ArrayList<ShardCommittee> shard_committees =
        get_crosslink_committees_at_slot(state, toIntExact(attestation_data.getSlot()));
    ArrayList<ShardCommittee> shard_committee = new ArrayList<>();
    for (ShardCommittee curr_shard_committee : shard_committees) {
      if (curr_shard_committee.getShard().equals(attestation_data.getShard())) {
        shard_committee.add(curr_shard_committee);
      }
    }
    assert participation_bitfield.length == ceil_div8(shard_committee.toArray().length);

    // Find the participating attesters in the committee
    ArrayList<ShardCommittee> participants = new ArrayList<>();
    for (int i = 0; i < shard_committee.size(); i++) {
      int participation_bit = (participation_bitfield[i / 8] >> (7 - (i % 8))) % 2;
      if (participation_bit == 1) {
        participants.add(shard_committee.get(i));
      }
    }
    return participants;
  }

  /**
   * Return the smallest integer r such that r * div >= 8.
   *
   * @param div
   * @return
   */
  private static int ceil_div8(int div) {
    checkArgument(div > 0, "Expected positive div but got %s", div);
    return (int) Math.ceil(8.0 / div);
  }

  /**
   * Shuffles 'validators' into shard committees using 'seed' as entropy.
   *
   * @param seed
   * @param validators
   * @param crosslinking_start_shard
   * @return
   */
  public static ArrayList<ShardCommittee> get_shuffling(
      Bytes32 seed, ArrayList<Validator> validators, int crosslinking_start_shard, int slot) {
    // Normalizes slot to start of epoch boundary
    slot -= slot % EPOCH_LENGTH;

    ArrayList<Integer> active_validator_indices =
        ValidatorsUtil.get_active_validator_indices_at_epoch(
            new Validators(validators), BeaconStateUtil.slot_to_epoch(slot));
    int committees_per_slot =
        BeaconStateUtil.clamp(
            1,
            SHARD_COUNT / EPOCH_LENGTH,
            active_validator_indices.size() / EPOCH_LENGTH / TARGET_COMMITTEE_SIZE);

    // Shuffle with seed
    ArrayList<Integer> shuffled_active_validator_indices =
        BeaconStateUtil.shuffle(active_validator_indices, seed);

    // Split the shuffled list into epoch_length pieces
    ArrayList<ArrayList<Integer>> validators_per_slot =
        BeaconStateUtil.split(shuffled_active_validator_indices, EPOCH_LENGTH);

    ArrayList<ArrayList<ShardCommittee>> output = new ArrayList<>();

    for (int slot_position = 0; slot_position < validators_per_slot.size(); slot_position++) {
      // Split the shuffled list into committees_per_slot pieces
      ArrayList<ArrayList<Integer>> shard_indices =
          BeaconStateUtil.split(validators_per_slot.get(slot_position), committees_per_slot);

      long shard_id_start = (long) crosslinking_start_shard + slot_position * committees_per_slot;

      ArrayList<ShardCommittee> shard_committees = new ArrayList<>();

      for (int shard_position = 0; shard_position < shard_indices.size(); shard_position++) {
        shard_committees.add(
            new ShardCommittee(
                UnsignedLong.valueOf((shard_id_start + shard_position) % SHARD_COUNT),
                shard_indices.get(shard_position),
                UnsignedLong.valueOf(active_validator_indices.size())));
      }

      output.add(shard_committees);
    }

    return output;
  }

  /**
   * Assumes 'attestation_data_1' is distinct from 'attestation_data_2'.
   *
   * @param attestation_data_1
   * @param attestation_data_2
   * @return True if the provided 'AttestationData' are slashable due to a 'double vote'.
   */
  private boolean is_double_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long target_epoch_1 = attestation_data_1.getSlot() / EPOCH_LENGTH;
    long target_epoch_2 = attestation_data_2.getSlot() / EPOCH_LENGTH;
    return target_epoch_1 == target_epoch_2;
  }

  /**
   * Note: parameter order matters as this function only checks that 'attestation_data_1' surrounds
   * 'attestation_data_2'.
   *
   * @param attestation_data_1
   * @param attestation_data_2
   * @return True if the provided 'AttestationData' are slashable due to a 'surround vote'.
   */
  private boolean is_surround_vote(
      AttestationData attestation_data_1, AttestationData attestation_data_2) {
    long source_epoch_1 = attestation_data_1.getJustified_slot().longValue() / EPOCH_LENGTH;
    long source_epoch_2 = attestation_data_2.getJustified_slot().longValue() / EPOCH_LENGTH;
    long target_epoch_1 = attestation_data_1.getSlot() / EPOCH_LENGTH;
    long target_epoch_2 = attestation_data_2.getSlot() / EPOCH_LENGTH;
    return source_epoch_1 < source_epoch_2
        && (source_epoch_2 + 1 == target_epoch_2)
        && target_epoch_2 < target_epoch_1;
  }

  /**
   * The largest integer 'x' such that 'x**2' is less than 'n'.
   *
   * @param n highest bound of x.
   * @return x
   */
  private int integer_squareroot(int n) {
    int x = n;
    int y = (x + 1) / 2;
    while (y < x) {
      x = y;
      y = (x + n / x) / 2;
    }
    return x;
  }

  /**
   * Compute the next root in the validator registry delta chain.
   *
   * @param current_validator_registry_delta_chain_tip
   * @param validator_index
   * @param pubkey
   * @param flag
   * @return The next root.
   */
  private Bytes32 get_new_validator_registry_delta_chain_tip(
      Bytes32 current_validator_registry_delta_chain_tip,
      int validator_index,
      Bytes48 pubkey,
      int flag,
      UnsignedLong slot) {
    return Hash.keccak256(
        TreeHashUtil.hash_tree_root(
            new ValidatorRegistryDeltaBlock(
                    UnsignedLong.valueOf(flag),
                    current_validator_registry_delta_chain_tip,
                    pubkey,
                    slot,
                    validator_index)
                .toBytes()));
  }
  /**
   * Returns the effective balance (also known as "balance at stake") for a 'validator' with the
   * given 'index'.
   *
   * @param state The BeaconState.
   * @param index The index at which the validator is at.
   * @return The effective balance.
   */
  private double get_effective_balance(BeaconState state, int index) {
    return Math.min(state.validator_balances.get(index).intValue(), Constants.MAX_DEPOSIT_AMOUNT);
  }

  public Bytes32 getPrevious_epoch_randao_mix() {
    // todo
    return null;
  }

  public int getPrevious_epoch_calculation_slot() {
    // todo
    return 0;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getSlot() {
    return slot;
  }

  public void setSlot(UnsignedLong slot) {
    this.slot = slot;
  }

  public UnsignedLong getGenesis_time() {
    return genesis_time;
  }

  public void setGenesis_time(UnsignedLong genesis_time) {
    this.genesis_time = genesis_time;
  }

  public Fork getFork() {
    return fork;
  }

  public void setFork(Fork fork) {
    this.fork = fork;
  }

  public Validators getValidator_registry() {
    return validator_registry;
  }

  public void setValidator_registry(Validators validator_registry) {
    this.validator_registry = validator_registry;
  }

  public ArrayList<UnsignedLong> getValidator_balances() {
    return validator_balances;
  }

  public void setValidator_balances(ArrayList<UnsignedLong> validator_balances) {
    this.validator_balances = validator_balances;
  }

  public UnsignedLong getValidator_registry_update_epoch() {
    return validator_registry_update_epoch;
  }

  public void setValidator_registry_update_epoch(UnsignedLong validator_registry_update_epoch) {
    this.validator_registry_update_epoch = validator_registry_update_epoch;
  }

  public ArrayList<Bytes32> getLatest_randao_mixes() {
    return latest_randao_mixes;
  }

  public void setLatest_randao_mixes(ArrayList<Bytes32> latest_randao_mixes) {
    this.latest_randao_mixes = latest_randao_mixes;
  }

  public UnsignedLong getPrevious_epoch_start_shard() {
    return previous_epoch_start_shard;
  }

  public void setPrevious_epoch_start_shard(UnsignedLong previous_epoch_start_shard) {
    this.previous_epoch_start_shard = previous_epoch_start_shard;
  }

  public UnsignedLong getCurrent_epoch_start_shard() {
    return current_epoch_start_shard;
  }

  public void setCurrent_epoch_start_shard(UnsignedLong current_epoch_start_shard) {
    this.current_epoch_start_shard = current_epoch_start_shard;
  }

  public UnsignedLong getPrevious_calculation_epoch() {
    return previous_calculation_epoch;
  }

  public void setPrevious_calculation_epoch(UnsignedLong previous_calculation_epoch) {
    this.previous_calculation_epoch = previous_calculation_epoch;
  }

  public UnsignedLong getCurrent_calculation_epoch() {
    return current_calculation_epoch;
  }

  public void setCurrent_calculation_epoch(UnsignedLong current_calculation_epoch) {
    this.current_calculation_epoch = current_calculation_epoch;
  }

  public Bytes32 getPrevious_epoch_seed() {
    return previous_epoch_seed;
  }

  public void setPrevious_epoch_seed(Bytes32 previous_epoch_seed) {
    this.previous_epoch_seed = previous_epoch_seed;
  }

  public Bytes32 getCurrent_epoch_seed() {
    return current_epoch_seed;
  }

  public void setCurrent_epoch_seed(Bytes32 current_epoch_seed) {
    this.current_epoch_seed = current_epoch_seed;
  }

  public UnsignedLong getPrevious_justified_epoch() {
    return previous_justified_epoch;
  }

  public void setPrevious_justified_epoch(UnsignedLong previous_justified_epoch) {
    this.previous_justified_epoch = previous_justified_epoch;
  }

  public UnsignedLong getJustified_epoch() {
    return justified_epoch;
  }

  public void setJustified_epoch(UnsignedLong justified_epoch) {
    this.justified_epoch = justified_epoch;
  }

  public UnsignedLong getJustification_bitfield() {
    return justification_bitfield;
  }

  public void setJustification_bitfield(UnsignedLong justification_bitfield) {
    this.justification_bitfield = justification_bitfield;
  }

  public UnsignedLong getFinalized_epoch() {
    return finalized_epoch;
  }

  public void setFinalized_epoch(UnsignedLong finalized_epoch) {
    this.finalized_epoch = finalized_epoch;
  }

  public ArrayList<CrosslinkRecord> getLatest_crosslinks() {
    return latest_crosslinks;
  }

  public void setLatest_crosslinks(ArrayList<CrosslinkRecord> latest_crosslinks) {
    this.latest_crosslinks = latest_crosslinks;
  }

  public ArrayList<Bytes32> getLatest_block_roots() {
    return latest_block_roots;
  }

  public void setLatest_block_roots(ArrayList<Bytes32> latest_block_roots) {
    this.latest_block_roots = latest_block_roots;
  }

  public ArrayList<Bytes32> getLatest_index_roots() {
    return latest_index_roots;
  }

  public void setLatest_index_roots(ArrayList<Bytes32> latest_index_roots) {
    this.latest_index_roots = latest_index_roots;
  }

  public ArrayList<UnsignedLong> getLatest_penalized_balances() {
    return latest_penalized_balances;
  }

  public void setLatest_penalized_balances(ArrayList<UnsignedLong> latest_penalized_balances) {
    this.latest_penalized_balances = latest_penalized_balances;
  }

  public ArrayList<PendingAttestationRecord> getLatest_attestations() {
    return latest_attestations;
  }

  public void setLatest_attestations(ArrayList<PendingAttestationRecord> latest_attestations) {
    this.latest_attestations = latest_attestations;
  }

  public ArrayList<Bytes32> getBatched_block_roots() {
    return batched_block_roots;
  }

  public void setBatched_block_roots(ArrayList<Bytes32> batched_block_roots) {
    this.batched_block_roots = batched_block_roots;
  }

  public Eth1Data getLatest_eth1_data() {
    return latest_eth1_data;
  }

  public void setLatest_eth1_data(Eth1Data latest_eth1_data) {
    this.latest_eth1_data = latest_eth1_data;
  }

  public ArrayList<Eth1DataVote> getEth1_data_votes() {
    return eth1_data_votes;
  }

  public void setEth1_data_votes(ArrayList<Eth1DataVote> eth1_data_votes) {
    this.eth1_data_votes = eth1_data_votes;
  }
}
