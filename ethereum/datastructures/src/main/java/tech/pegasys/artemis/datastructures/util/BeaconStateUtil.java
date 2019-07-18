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

package tech.pegasys.artemis.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.ACTIVATION_EXIT_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.CHURN_LIMIT_QUOTIENT;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.artemis.datastructures.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.artemis.datastructures.Constants.EPOCHS_PER_SLASHINGS_VECTOR;
import static tech.pegasys.artemis.datastructures.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.artemis.datastructures.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.artemis.datastructures.Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.MIN_GENESIS_TIME;
import static tech.pegasys.artemis.datastructures.Constants.MIN_PER_EPOCH_CHURN_LIMIT;
import static tech.pegasys.artemis.datastructures.Constants.MIN_SEED_LOOKAHEAD;
import static tech.pegasys.artemis.datastructures.Constants.MIN_SLASHING_PENALTY_QUOTIENT;
import static tech.pegasys.artemis.datastructures.Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
import static tech.pegasys.artemis.datastructures.Constants.PROPOSER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.datastructures.Constants.SECONDS_PER_DAY;
import static tech.pegasys.artemis.datastructures.Constants.SHARD_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.datastructures.Constants.TARGET_COMMITTEE_SIZE;
import static tech.pegasys.artemis.datastructures.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.get_compact_committees_root;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_crosslink_committee;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.get_start_shard;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.artemis.datastructures.util.ValidatorsUtil.increase_balance;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.hash_tree_root;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class BeaconStateUtil {

  private static final ALogger LOG = new ALogger(BeaconStateUtil.class.getName());

  public static BeaconState initialize_beacon_state_from_eth1(
      Bytes32 eth1_block_hash, UnsignedLong eth1_timestamp, List<Deposit> deposits) {
    UnsignedLong genesis_time =
        eth1_timestamp.minus(
            eth1_timestamp
                .mod(UnsignedLong.valueOf(SECONDS_PER_DAY))
                .plus(UnsignedLong.valueOf(2).times(UnsignedLong.valueOf(SECONDS_PER_DAY))));
    Eth1Data eth1_data = new Eth1Data();
    eth1_data.setBlock_hash(eth1_block_hash);
    eth1_data.setDeposit_count(UnsignedLong.valueOf(deposits.size()));

    MerkleTree<Deposit> merkleTree = DepositUtil.generateMerkleTree(deposits);
    eth1_data.setDeposit_root(merkleTree.getRoot());
    BeaconState state = new BeaconState();
    state.setGenesis_time(genesis_time);
    state.setEth1_data(eth1_data);
    BeaconBlockHeader beaconBlockHeader = new BeaconBlockHeader();
    Bytes32 latestBlockRoot = new BeaconBlockBody().hash_tree_root();
    beaconBlockHeader.setBody_root(latestBlockRoot);
    state.setLatest_block_header(beaconBlockHeader);

    // Process deposits
    DepositUtil.applyBranchProofs(merkleTree, deposits);
    deposits.forEach(deposit -> process_deposit(state, deposit));

    // Process activations
    IntStream.range(0, state.getValidators().size())
        .forEach(
            index -> {
              Validator validator = state.getValidators().get(index);
              UnsignedLong balance = state.getBalances().get(index);
              UnsignedLong effective_balance =
                  min(
                      balance.minus(balance.mod(UnsignedLong.valueOf(EFFECTIVE_BALANCE_INCREMENT))),
                      UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE));
              validator.setEffective_balance(effective_balance);

              if (validator
                  .getEffective_balance()
                  .equals(UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE))) {
                validator.setActivation_eligibility_epoch(UnsignedLong.valueOf(GENESIS_EPOCH));
                validator.setActivation_epoch(UnsignedLong.valueOf(GENESIS_EPOCH));
              }
            });

    // Populate active_index_roots and compact_committees_roots
    List<Integer> indices_list =
        get_active_validator_indices(state, UnsignedLong.valueOf(GENESIS_EPOCH));
    Bytes32 active_index_root = hash_tree_root(SSZTypes.LIST_OF_BASIC, indices_list);
    Bytes32 committee_root =
        get_compact_committees_root(state, UnsignedLong.valueOf(GENESIS_EPOCH));
    IntStream.range(0, EPOCHS_PER_HISTORICAL_VECTOR)
        .forEach(
            index -> {
              state.getActive_index_roots().set(index, active_index_root);
              state.getCompact_committees_roots().set(index, committee_root);
            });
    return state;
  }

  /**
   * Processes deposits
   *
   * @param state
   * @param deposit
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#deposits</a>
   */
  public static void process_deposit(BeaconState state, Deposit deposit) {
        checkArgument(
            is_valid_merkle_branch(
                deposit.getData().hash_tree_root(),
                deposit.getProof(),
                Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, // Add 1 for the `List` length mix-in
                toIntExact(state.getEth1_deposit_index().longValue()),
                state.getEth1_data().getDeposit_root()),
            "process_deposit: Verify the Merkle branch");

        state.setEth1_deposit_index(state.getEth1_deposit_index().plus(UnsignedLong.ONE));

        BLSPublicKey pubkey = deposit.getData().getPubkey();
        UnsignedLong amount = deposit.getData().getAmount();
        List<BLSPublicKey> validator_pubkeys =
            state.getValidators().stream()
                .map(Validator::getPubkey)
                .collect(Collectors.toList());
        if (!validator_pubkeys.contains(pubkey)) {

          // Verify the deposit signature (proof of possession) for new validators.
          // Note: Deposits are valid across forks, thus the deposit
          // domain is retrieved directly from `compute_domain`
          boolean proof_is_valid =
              bls_verify(
                  pubkey,
                  deposit.getData().signing_root("signature"),
                  deposit.getData().getSignature(),
                  compute_domain(DOMAIN_DEPOSIT));
          if (!proof_is_valid) {
            return;
          }

          state
              .getValidators()
              .add(
                  new Validator(
                      pubkey,
                      deposit.getData().getWithdrawal_credentials(),
                      min(
                          amount.minus(
                              amount.mod(
                                  UnsignedLong.valueOf(Constants.EFFECTIVE_BALANCE_INCREMENT))),
                          UnsignedLong.valueOf(MAX_EFFECTIVE_BALANCE)),
                      false,
                      FAR_FUTURE_EPOCH,
                      FAR_FUTURE_EPOCH,
                      FAR_FUTURE_EPOCH,
                      FAR_FUTURE_EPOCH));
          state.getBalances().add(amount);
        } else {
          int index = validator_pubkeys.indexOf(pubkey);
          increase_balance(state, index, amount);
        }
  }

  public static boolean is_valid_genesis_state(BeaconState state) {
    return !(state.getGenesis_time().compareTo(MIN_GENESIS_TIME) < 0)
        && !(get_active_validator_indices(state, UnsignedLong.valueOf(GENESIS_EPOCH)).size()
            < MIN_GENESIS_ACTIVE_VALIDATOR_COUNT);
  }

  public static boolean is_valid_genesis_stateSim(BeaconState state) {
    return !(get_active_validator_indices(state, UnsignedLong.valueOf(GENESIS_EPOCH)).size()
        < MIN_GENESIS_ACTIVE_VALIDATOR_COUNT);
  }


  /**
   * Verify that the given ``leaf`` is on the merkle branch ``branch`` starting with the given
   * ``root``.
   *
   * @param leaf
   * @param branch
   * @param depth
   * @param index
   * @param root
   * @return A boolean depending on the merkle branch being valid
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_merkle_branch</a>
   */
  public static boolean is_valid_merkle_branch(
      Bytes32 leaf, List<Bytes32> branch, int depth, int index, Bytes32 root) {
    Bytes32 value = leaf;
    for (int i = 0; i < depth; i++) {
      if (Math.floor(index / Math.pow(2, i)) % 2 == 1) {
        value = Hash.sha2_256(Bytes.concatenate(branch.get(i), value));
      } else {
        value = Hash.sha2_256(Bytes.concatenate(value, branch.get(i)));
      }
    }
    return value.equals(root);
  }

  /**
   * Generate a seed for the given ``epoch``.
   *
   * @param state - The BeaconState under consideration.
   * @param epoch - The epoch to generate a seed for.
   * @return A generated seed for the given epoch.
   * @throws IllegalArgumentException
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_seed</a>
   */
  public static Bytes32 get_seed(BeaconState state, UnsignedLong epoch)
      throws IllegalArgumentException {
    UnsignedLong randaoIndex =
        epoch.plus(UnsignedLong.valueOf(EPOCHS_PER_HISTORICAL_VECTOR - MIN_SEED_LOOKAHEAD));
    Bytes32 randao_mix = get_randao_mix(state, randaoIndex);
    Bytes32 active_index_root = state.getActive_index_roots().get(epoch.mod(UnsignedLong.valueOf(EPOCHS_PER_HISTORICAL_VECTOR)).intValue());
    Bytes32 epochBytes = int_to_bytes32(epoch.longValue());
    return Hash.sha2_256(Bytes.wrap(randao_mix, active_index_root, epochBytes));
  }

  /**
   * Return the combined effective balance of the ``indices``. (1 Gwei minimum to avoid divisions by
   * zero.)
   *
   * @param state
   * @param indices
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_total_balance</a>
   */
  public static UnsignedLong get_total_balance(BeaconState state, List<Integer> indices) {
    UnsignedLong sum = UnsignedLong.ZERO;
    Iterator<Integer> itr = indices.iterator();
    List<Validator> validator_registry = state.getValidators();
    while (itr.hasNext()) {
      sum = sum.plus(validator_registry.get(itr.next()).getEffective_balance());
    }
    return max(sum, UnsignedLong.ONE);
  }

  /**
   * Return the combined effective balance of the active validators.
   *
   * @param state - Current BeaconState
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_total_active_balance</a>
   */
  public static UnsignedLong get_total_active_balance(BeaconState state) {
    return get_total_balance(state, get_active_validator_indices(state, get_current_epoch(state)));
  }


  /**
   *  Return the domain for the ``domain_type`` and ``fork_version``.
   *
   * @param domain_type
   * @param fork_version
   * @return domain
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_domain</a>
   */
  public static Bytes compute_domain(Bytes domain_type, Bytes fork_version) {
    checkArgument(domain_type.size() == 4, "domain_type must be of type Bytes4");
    checkArgument(fork_version.size() == 4, "fork_version must be of type Bytes4");
    Bytes domain = Bytes.concatenate(domain_type, fork_version);
    checkArgument(domain_type.size() == 8, "domain must be of type Bytes8");
    return domain;
  }

  /**
   *  Return the domain for the ``domain_type``.
   *
   * @param domain_type
   * @return domain
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_domain</a>
   */
  public static Bytes compute_domain(Bytes domain_type) {
    return compute_domain(domain_type, Bytes.wrap(new byte[4]));
  }


  /**
   * Returns the epoch number of the given slot.
   *
   * @param slot - The slot number under consideration.
   * @return The epoch associated with the given slot number.
   * @see <a> https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_epoch_of_slot</a>
   */
  public static UnsignedLong compute_epoch_of_slot(UnsignedLong slot) {
    return slot.dividedBy(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH));
  }

  /**
   * Return the previous epoch of the given ``state``. Return the current epoch if it's genesis
   * epoch.
   *
   * @param state
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_previous_epoch</a>
   */
  public static UnsignedLong get_previous_epoch(BeaconState state) {
    UnsignedLong current_epoch = get_current_epoch(state);
    return current_epoch.equals(UnsignedLong.valueOf(GENESIS_EPOCH))
        ? UnsignedLong.valueOf(GENESIS_EPOCH)
        : current_epoch.minus(UnsignedLong.ONE);
  }

  /**
   * Return the current epoch of the given state.
   *
   * @param state The beacon state under consideration.
   * @return The current epoch number for the given state.
   * @see <a> https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_current_epoch</a>
   */
  public static UnsignedLong get_current_epoch(BeaconState state) {
    return compute_epoch_of_slot(state.getSlot());
  }

  /**
   * Return the next epoch of the given state.
   *
   * <p><b>This method is no longer in the spec as of v0.4, but is retained here for
   * convenience.</b>
   *
   * @param state The beacon state under consideration.
   * @return The next epoch number.
   */
  public static UnsignedLong get_next_epoch(BeaconState state) {
    return get_current_epoch(state).plus(UnsignedLong.ONE);
  }

  /**
   * Return the starting slot of the given epoch.
   *
   * @param epoch - The epoch under consideration.
   * @return The slot that the given epoch starts at.
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_epoch_of_slot</a>
   */
  public static UnsignedLong compute_start_slot_of_epoch(UnsignedLong epoch) {
    return epoch.times(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
  }

  /**
   * An entry or exit triggered in the ``epoch`` given by the input takes effect at the epoch given
   * by the output.
   *
   * @param epoch
   */
  public static UnsignedLong get_entry_exit_effect_epoch(UnsignedLong epoch) {
    return epoch.plus(UnsignedLong.ONE).plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY));
  }

  /**
   *  Initiate the exit of the validator with index ``index``.
   *
   * @param state
   * @param index
   * @return
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#initiate_validator_exit</a>
   */
  public static void initiate_validator_exit(BeaconState state, int index) {
    Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExit_epoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    // Compute exit queue epoch
    List<UnsignedLong> exit_epochs =
        state.getValidators().stream()
            .filter(v -> !v.getExit_epoch().equals(FAR_FUTURE_EPOCH))
            .map(Validator::getExit_epoch)
            .collect(Collectors.toList());
    exit_epochs.add(compute_activation_exit_epoch(get_current_epoch(state)));
    UnsignedLong exit_queue_epoch = Collections.max(exit_epochs);
    final UnsignedLong final_exit_queue_epoch = exit_queue_epoch;
    UnsignedLong exit_queue_churn =
        UnsignedLong.valueOf(
            state.getValidators().stream()
                .filter(v -> v.getExit_epoch().equals(final_exit_queue_epoch))
                .collect(Collectors.toList())
                .size());

    if (exit_queue_churn.compareTo(get_validator_churn_limit(state)) >= 0) {
      exit_queue_epoch = exit_queue_epoch.plus(UnsignedLong.ONE);
    }

    // Set validator exit epoch and withdrawable epoch
    validator.setExit_epoch(exit_queue_epoch);
    validator.setWithdrawable_epoch(
        validator.getExit_epoch().plus(UnsignedLong.valueOf(MIN_VALIDATOR_WITHDRAWABILITY_DELAY)));
  }

  /**
   *  Slash the validator with index ``slashed_index``.
   *
   * @param state
   * @param slashed_index
   * @param whistleblower_index
   * @return
   * @see <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#slash_validator/a>
   */
  public static void slash_validator(
      BeaconState state, int slashed_index, int whistleblower_index) {
    UnsignedLong epoch = get_current_epoch(state);
    initiate_validator_exit(state, slashed_index);
    Validator validator = state.getValidators().get(slashed_index);
    validator.setSlashed(true);
    validator.setWithdrawable_epoch(max(validator.getWithdrawable_epoch(), epoch.plus(UnsignedLong.valueOf(EPOCHS_PER_SLASHINGS_VECTOR))));
    int index = epoch.mod(UnsignedLong.valueOf(EPOCHS_PER_SLASHINGS_VECTOR)).intValue();
    state.getSlashings().set(index, state.getSlashings().get(index).plus(validator.getEffective_balance()));
    decrease_balance(state, slashed_index, validator.getEffective_balance().dividedBy(UnsignedLong.valueOf(MIN_SLASHING_PENALTY_QUOTIENT)));

    // Apply proposer and whistleblower rewards
    int proposer_index = get_beacon_proposer_index(state);
    if (whistleblower_index == -1) {
      whistleblower_index = proposer_index;
    }

    UnsignedLong whistleblower_reward = validator.getEffective_balance().dividedBy(UnsignedLong.valueOf(WHISTLEBLOWER_REWARD_QUOTIENT));
    UnsignedLong proposer_reward =
        whistleblower_reward.dividedBy(UnsignedLong.valueOf(PROPOSER_REWARD_QUOTIENT));
    increase_balance(state, proposer_index, proposer_reward);
    increase_balance(state, whistleblower_index, whistleblower_reward.minus(proposer_reward));
  }

  public static void slash_validator(BeaconState state, int slashed_index) {
    slash_validator(state, slashed_index,  -1);
  }

  /**
   * Return the block root at a recent ``epoch``.
   *
   * @param state - The BeaconState under consideration.
   * @param epoch
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_block_root</a>
   */
  public static Bytes32 get_block_root(BeaconState state, UnsignedLong epoch)
      throws IllegalArgumentException {
    return get_block_root_at_slot(state, compute_start_slot_of_epoch(epoch));
  }

  /**
   * Return the number of committees at ``epoch``.
   *
   * @param state
   * @param epoch
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_committee_count</a>
   */
  public static UnsignedLong get_committee_count(BeaconState state, UnsignedLong epoch) {
    List<Integer> active_validator_indices = get_active_validator_indices(state, epoch);
    return max(
            UnsignedLong.ONE,
            min(
                UnsignedLong.valueOf(Math.floorDiv(SHARD_COUNT, SLOTS_PER_EPOCH)),
                UnsignedLong.valueOf(active_validator_indices.size())
                    .dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH))
                    .dividedBy(UnsignedLong.valueOf(TARGET_COMMITTEE_SIZE))))
        .times(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
  }

  /**
   *    Return the randao mix at a recent ``epoch``.
   * @param state
   * @param epoch
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_randao_mix</a>
   */
  public static Bytes32 get_randao_mix(BeaconState state, UnsignedLong epoch) {
    int index = epoch.mod(UnsignedLong.valueOf(EPOCHS_PER_HISTORICAL_VECTOR)).intValue();
    return state.getRandao_mixes().get(index);
  }

  /**
   * Return shuffled indices in a pseudorandom permutation `0...list_size-1` with ``seed`` as
   * entropy.
   *
   * <p>Utilizes 'swap or not' shuffling found in
   * https://link.springer.com/content/pdf/10.1007%2F978-3-642-32009-5_1.pdf See the 'generalized
   * domain' algorithm on page 3.
   *
   * <p>The result of this should be the same as calling get_permuted_index() for each index in the
   * list
   *
   * @param list_size The size of the list from which the element is taken. Must not exceed 2^31.
   * @param seed Initial seed value used for randomization.
   * @return The permuted arrays of indices
   */
  public static int[] shuffle(int list_size, Bytes32 seed) {

    if (list_size == 0) {
      return new int[0];
    }

    //  In the following, great care is needed around signed and unsigned values.
    //  Note that the % (modulo) operator in Java behaves differently from the
    //  modulo operator in python:
    //    Python -1 % 13 = 12
    //    Java   -1 % 13 = -1

    //  Using UnsignedLong doesn't help us as some quantities can legitimately be negative.

    // Note: this should be faster than manually creating the list in a for loop
    // https://stackoverflow.com/questions/10242380/how-can-i-generate-a-list-or-array-of-sequential-integers-in-java
    int[] indices = IntStream.rangeClosed(0, list_size - 1).toArray();

    // int[] indices = new int[list_size];
    // for (int i = 0; i < list_size; i++) {
    //   indices[i] = i;
    // }

    byte[] powerOfTwoNumbers = {1, 2, 4, 8, 16, 32, 64, (byte) 128};

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      Bytes hashBytes = Bytes.EMPTY;
      for (int i = 0; i < (list_size + 255) / 256; i++) {
        Bytes iAsBytes4 = int_to_bytes(i, 4);
        hashBytes = Bytes.wrap(hashBytes, Hash.keccak256(Bytes.wrap(seed, roundAsByte, iAsBytes4)));
      }

      // This needs to be unsigned modulo.
      int pivot =
          toIntExact(
              Long.remainderUnsigned(
                  bytes_to_int(Hash.keccak256(Bytes.wrap(seed, roundAsByte)).slice(0, 8)),
                  list_size));

      for (int i = 0; i < list_size; i++) {

        int flip = (pivot - indices[i]) % list_size;
        if (flip < 0) {
          // Account for flip being negative
          flip += list_size;
        }

        int hashPosition = (indices[i] < flip) ? flip : indices[i];
        byte theByte = hashBytes.get(hashPosition / 8);
        byte theMask = powerOfTwoNumbers[hashPosition % 8];
        if ((theByte & theMask) != 0) {
          indices[i] = flip;
        }
      }
    }

    return indices;
  }

  /**
   * Converts byte[] (wrapped by BytesValue) to int.
   *
   * @param src byte[] (wrapped by BytesValue)
   * @param pos Index in Byte[] array
   * @return converted int
   * @throws IllegalArgumentException if pos is a negative value.
   */
  @VisibleForTesting
  public static int bytes3ToInt(Bytes src, int pos) {
    checkArgument(pos >= 0, "Expected positive pos but got %s", pos);
    return ((src.get(pos) & 0xFF) << 16)
        | ((src.get(pos + 1) & 0xFF) << 8)
        | (src.get(pos + 2) & 0xFF);
  }

  /**
   *   Return the beacon proposer index at the current slot.
   *
   * @param state
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_beacon_proposer_index</a>
   */
  public static int get_beacon_proposer_index(BeaconState state) {
    UnsignedLong epoch = get_current_epoch(state);
    UnsignedLong committees_per_slot =
        get_committee_count(state, epoch).dividedBy(UnsignedLong.valueOf(SLOTS_PER_EPOCH));
    UnsignedLong offset =
        committees_per_slot.times(state.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)));
    UnsignedLong shard =
        get_start_shard(state, epoch).plus(offset).mod(UnsignedLong.valueOf(SHARD_COUNT));

    List<Integer> first_committee = get_crosslink_committee(state, epoch, shard);
    long MAX_RANDOM_BYTE = (long) Math.pow(2.0d, 8.0d) - 1;
    Bytes seed = get_seed(state, epoch);
    int i = 0;
    while (true) {
      int index =
          epoch
              .plus(UnsignedLong.valueOf(i))
              .mod(UnsignedLong.valueOf(first_committee.size()))
              .intValue();
      int candidate_index = first_committee.get(index);
      Bytes digest = Hash.sha2_256(Bytes.concatenate(seed, int_to_bytes(Math.floorDiv(i, 32), 8)));
      byte random_byte = digest.get(i % 32);
      UnsignedLong effective_balance =
          state.getValidators().get(candidate_index).getEffective_balance();

      long minRandomBalance = effective_balance.longValue() * MAX_RANDOM_BYTE;
      long maxRandomBalance = MAX_EFFECTIVE_BALANCE * random_byte;
      if (minRandomBalance >= maxRandomBalance) return candidate_index;
      i++;
    }
  }

  /**
   * Return the min of two UnsignedLong values
   *
   * @param value1
   * @param value2
   * @return
   */
  @VisibleForTesting
  public static UnsignedLong min(UnsignedLong value1, UnsignedLong value2) {
    if (value1.compareTo(value2) <= 0) {
      return value1;
    } else {
      return value2;
    }
  }

  /**
   * Return the max of two UnsignedLong values
   *
   * @param value1
   * @param value2
   * @return
   */
  public static UnsignedLong max(UnsignedLong value1, UnsignedLong value2) {
    if (value1.compareTo(value2) >= 0) {
      return value1;
    } else {
      return value2;
    }
  }

  /**
   * Return the signature domain (fork version concatenated with domain type) of a message.
   *
   * @param state
   * @param domain_type
   * @param message_epoch
   * @return The fork version and signature domain. This format ((fork version << 32) +
   *     SignatureDomain) is used to partition BLS signatures.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_domain</a>
   */
  public static Bytes get_domain(BeaconState state, Bytes domain_type, UnsignedLong message_epoch) {
    checkArgument(domain_type.size() == 4, "domain_type must be of type Bytes4");
    UnsignedLong epoch = (message_epoch == null) ? get_current_epoch(state) : message_epoch;
    Bytes fork_version =
        (epoch.compareTo(state.getFork().getEpoch()) < 0)
            ? state.getFork().getPrevious_version()
            : state.getFork().getCurrent_version();
    return compute_domain(domain_type, fork_version);
  }

  /**
   * Return the signature domain (fork version concatenated with domain type) of a message.
   *
   * @param state
   * @param domain_type
   * @return The fork version and signature domain. This format ((fork version << 32) +
   *     SignatureDomain) is used to partition BLS signatures.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_domain</a>
   */
  public static Bytes get_domain(BeaconState state, Bytes domain_type) {
    return get_domain(state, domain_type, null);
  }

  /**
   * Return the bls domain given by the ``domain_type`` and optional 4 byte ``fork_version``
   * (defaults to zero).
   *
   * @param domain_type
   * @param fork_version
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#bls_domain</a>
   */
  public static int bls_domain(int domain_type, Bytes fork_version) {
    return (int) bytes_to_int(Bytes.concatenate(int_to_bytes(domain_type, 4), fork_version));
  }

  /**
   * Return the bls domain given by the ``domain_type`` and optional 4 byte ``fork_version``
   * (defaults to zero).
   *
   * @param domain_type
   * @param fork_version
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.7.1/specs/core/0_beacon-chain.md#bls_domain</a>
   */
  public static int bls_domain(int domain_type) {
    return bls_domain(domain_type, Bytes.wrap(new byte[0]));
  }

  /**
   * Return the churn limit based on the active validator count.
   *
   * @param state
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_validator_churn_limit</a>
   */
  public static UnsignedLong get_validator_churn_limit(BeaconState state) {
    List<Integer> active_validator_indices = get_active_validator_indices(state, get_current_epoch(state));
    return max(
        UnsignedLong.valueOf(MIN_PER_EPOCH_CHURN_LIMIT),
        UnsignedLong.valueOf(active_validator_indices.size() / CHURN_LIMIT_QUOTIENT));
  }

  /**
   * Return the epoch at which an activation or exit triggered in `epoch` takes effect. g
   *
   * @param epoch - The epoch under consideration.
   * @return The epoch at which an activation or exit in the given `epoch` will take effect.
   * @see <a> https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_activation_exit_epoch</a>
   */
  public static UnsignedLong compute_activation_exit_epoch(UnsignedLong epoch) {
    return epoch.plus(UnsignedLong.ONE).plus(UnsignedLong.valueOf(ACTIVATION_EXIT_DELAY));
  }

  /**
   * Extract the bit in ``bitfield`` at position ``i``.
   *
   * @param bitfield - The Bytes value that describes the bitfield to operate on.
   * @param i - The index.
   * @return The bit at bitPosition from the given bitfield.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#get_bitfield_bit">get_bitfield_bit
   *     - Spec v0.4</a>
   */
  public static int get_bitfield_bit(Bytes bitfield, int i) {
    return (bitfield.get(i / 8) >>> (i % 8)) % 2;
  }

  /**
   * Verify ``bitfield`` against the ``committee_size``.
   *
   * @param bitfield - The bitfield under consideration.
   * @param committee_size - The size of the committee associated with the bitfield.
   * @return True if the given bitfield is valid for the given committee_size, false otherwise.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.4.0/specs/core/0_beacon-chain.md#verify_bitfield">verify_bitfield
   *     - Spec v0.4</a>
   */
  public static boolean verify_bitfield(Bytes bitfield, int committee_size) {
    if (bitfield.size() != (committee_size + 7) / 8) {
      return false;
    }

    for (int i = committee_size; i < bitfield.size() * 8; i++) {
      if (get_bitfield_bit(bitfield, i) == 0b1) {
        return false;
      }
    }
    return true;
  }

  // TODO test this function
  public static Bytes setBit(Bytes bits, int pos) {
    byte[] bitsByteArray = bits.toArray();
    byte myByte = bitsByteArray[pos / 8];
    myByte = (byte) (myByte | (1 << (7 - (pos % 8))));
    bitsByteArray[pos / 8] = myByte;
    return Bytes.wrap(bitsByteArray);
  }

  public static boolean all(Bytes bits, int start, int end) {
    for (int i = start; i < end; i++) {
      if (get_bitfield_bit(bits, i) == 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * The largest integer 'x' such that 'x**2' is less than 'n'.
   *
   * @param n - The highest bound of x.
   * @return The largest integer 'x' such that 'x**2' is less than 'n'.
   * @see
   * <a> https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#integer_squareroot</a>
   */
  public static UnsignedLong integer_squareroot(UnsignedLong n) {
    checkArgument(
        n.compareTo(UnsignedLong.ZERO) >= 0,
        "checkArgument threw and exception in integer_squareroot()");
    UnsignedLong TWO = UnsignedLong.valueOf(2L);
    UnsignedLong x = n;
    UnsignedLong y = x.plus(UnsignedLong.ONE).dividedBy(TWO);
    while (y.compareTo(x) < 0) {
      x = y;
      y = x.plus(n.dividedBy(x)).dividedBy(TWO);
    }
    return x;
  }

  /**
   * Convert a long value into a number of bytes, little endian.
   *
   * <p>If numBytes is more than the size of a long then the returned value is right-padded with
   * zero bytes. If numBytes is less than the size of a long then the value is truncated.
   *
   * @param value - The value to be converted to bytes.
   * @param numBytes - The number of bytes to be returned.
   * @return The value represented as the requested number of bytes.
   */
  public static Bytes int_to_bytes(long value, int numBytes) {
    final int longBytes = Long.SIZE / 8;
    Bytes valueBytes = Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
    if (numBytes <= longBytes) {
      return valueBytes.slice(0, numBytes);
    } else {
      return Bytes.wrap(valueBytes, Bytes.wrap(new byte[numBytes - longBytes]));
    }
  }

  public static Bytes32 int_to_bytes32(long value) {
    return Bytes32.wrap(int_to_bytes(value, 32));
  }

  public static Bytes32 int_to_bytes32(UnsignedLong value) {
    return int_to_bytes32(value.longValue());
  }

  /**
   * @param data - The value to be converted to int.
   * @return An integer representation of the bytes value given.
   * @see
   * <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#bytes_to_int</a>
   */
  public static long bytes_to_int(Bytes data) {
    return data.toLong(ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Return the block root at a recent ``slot``.
   *
   * @param state
   * @param slot
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_block_root_at_slot</a>
   */
  public static Bytes32 get_block_root_at_slot(BeaconState state, UnsignedLong slot)
      throws IllegalArgumentException {
    UnsignedLong slotPlusHistoricalRoot =
        slot.plus(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT));
    checkArgument(
        slot.compareTo(state.getSlot()) < 0
            && state.getSlot().compareTo(slotPlusHistoricalRoot) <= 0,
        "BeaconStateUtil.get_block_root_at_slot");
    int latestBlockRootIndex = slot.mod(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT)).intValue();
    return state.getBlock_roots().get(latestBlockRootIndex);
  }

  /**
   * @param m
   * @param n
   * @return
   */
  public static int safeMod(int m, int n) {
    return ((n % m) + m) % m;
  }

  /**
   * @param m
   * @param n
   * @return
   */
  public static long safeMod(long m, long n) {
    return ((n % m) + m) % m;
  }
}
