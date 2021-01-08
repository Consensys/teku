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

package tech.pegasys.teku.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.CommitteeUtil.compute_proposer_index;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.increase_balance;
import static tech.pegasys.teku.util.config.Constants.CHURN_LIMIT_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_SLASHINGS_VECTOR;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.teku.util.config.Constants.GENESIS_FORK_VERSION;
import static tech.pegasys.teku.util.config.Constants.MAX_COMMITTEES_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.teku.util.config.Constants.MAX_SEED_LOOKAHEAD;
import static tech.pegasys.teku.util.config.Constants.MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
import static tech.pegasys.teku.util.config.Constants.MIN_GENESIS_TIME;
import static tech.pegasys.teku.util.config.Constants.MIN_PER_EPOCH_CHURN_LIMIT;
import static tech.pegasys.teku.util.config.Constants.MIN_SEED_LOOKAHEAD;
import static tech.pegasys.teku.util.config.Constants.MIN_SLASHING_PENALTY_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
import static tech.pegasys.teku.util.config.Constants.PROPOSER_REWARD_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.teku.util.config.Constants.TARGET_COMMITTEE_SIZE;
import static tech.pegasys.teku.util.config.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;

import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateCache;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkData;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.datastructures.state.SigningData;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.util.config.Constants;

public class BeaconStateUtil {

  private static final Logger LOG = LogManager.getLogger(BeaconStateUtil.class);

  /**
   * For debug/test purposes only enables/disables {@link DepositData} BLS signature verification
   * Setting to <code>false</code> significantly speeds up state initialization
   */
  public static boolean BLS_VERIFY_DEPOSIT = true;

  public static BeaconState initialize_beacon_state_from_eth1(
      Bytes32 eth1_block_hash, UInt64 eth1_timestamp, List<? extends Deposit> deposits) {
    final GenesisGenerator genesisGenerator = new GenesisGenerator();
    genesisGenerator.updateCandidateState(eth1_block_hash, eth1_timestamp, deposits);
    return genesisGenerator.getGenesisState();
  }

  /**
   * Processes deposits
   *
   * @param state
   * @param deposit
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#deposits</a>
   */
  public static void process_deposit(MutableBeaconState state, Deposit deposit) {
    checkArgument(
        is_valid_merkle_branch(
            deposit.getData().hash_tree_root(),
            deposit.getProof(),
            Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, // Add 1 for the List length mix-in
            toIntExact(state.getEth1_deposit_index().longValue()),
            state.getEth1_data().getDeposit_root()),
        "process_deposit: Verify the Merkle branch");

    process_deposit_without_checking_merkle_proof(state, deposit, null);
  }

  static void process_deposit_without_checking_merkle_proof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Map<BLSPublicKey, Integer> pubKeyToIndexMap) {
    state.setEth1_deposit_index(state.getEth1_deposit_index().plus(UInt64.ONE));

    final BLSPublicKey pubkey = deposit.getData().getPubkey();
    final UInt64 amount = deposit.getData().getAmount();

    OptionalInt existingIndex;
    if (pubKeyToIndexMap != null) {
      Integer cachedIndex = pubKeyToIndexMap.putIfAbsent(pubkey, state.getValidators().size());
      existingIndex = cachedIndex == null ? OptionalInt.empty() : OptionalInt.of(cachedIndex);
    } else {
      SSZList<Validator> validators = state.getValidators();

      Function<Integer, BLSPublicKey> validatorPubkey =
          index -> ValidatorsUtil.getValidatorPubKey(state, UInt64.valueOf(index)).orElse(null);

      existingIndex =
          IntStream.range(0, validators.size())
              .filter(index -> pubkey.equals(validatorPubkey.apply(index)))
              .findFirst();
    }

    if (existingIndex.isEmpty()) {

      // Verify the deposit signature (proof of possession) which is not checked by the deposit
      // contract
      if (BLS_VERIFY_DEPOSIT) {
        final DepositMessage deposit_message =
            new DepositMessage(pubkey, deposit.getData().getWithdrawal_credentials(), amount);
        final Bytes32 domain = compute_domain(DOMAIN_DEPOSIT);
        final Bytes signing_root = compute_signing_root(deposit_message, domain);
        boolean proof_is_valid =
            !BLS_VERIFY_DEPOSIT
                || BLS.verify(pubkey, signing_root, deposit.getData().getSignature());
        if (!proof_is_valid) {
          if (deposit instanceof DepositWithIndex) {
            LOG.debug(
                "Skipping invalid deposit with index {} and pubkey {}",
                ((DepositWithIndex) deposit).getIndex(),
                pubkey);
          } else {
            LOG.debug("Skipping invalid deposit with pubkey {}", pubkey);
          }
          if (pubKeyToIndexMap != null) {
            // The validator won't be created so the calculated index won't be correct
            pubKeyToIndexMap.remove(pubkey);
          }
          return;
        }
      }

      if (pubKeyToIndexMap == null) {
        LOG.debug("Adding new validator to state: {}", state.getValidators().size());
      }
      state.getValidators().add(getValidatorFromDeposit(deposit));
      state.getBalances().add(amount);
    } else {
      increase_balance(state, existingIndex.getAsInt(), amount);
    }
  }

  private static Validator getValidatorFromDeposit(Deposit deposit) {
    final UInt64 amount = deposit.getData().getAmount();
    final UInt64 effectiveBalance =
        amount.minus(amount.mod(EFFECTIVE_BALANCE_INCREMENT)).min(MAX_EFFECTIVE_BALANCE);
    return new Validator(
        deposit.getData().getPubkey().toBytesCompressed(),
        deposit.getData().getWithdrawal_credentials(),
        effectiveBalance,
        false,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH,
        FAR_FUTURE_EPOCH);
  }

  public static boolean is_valid_genesis_state(UInt64 genesisTime, int activeValidatorCount) {
    return isItMinGenesisTimeYet(genesisTime)
        && isThereEnoughNumberOfValidators(activeValidatorCount);
  }

  public static boolean isThereEnoughNumberOfValidators(int activeValidatorCount) {
    return activeValidatorCount >= MIN_GENESIS_ACTIVE_VALIDATOR_COUNT;
  }

  public static boolean isItMinGenesisTimeYet(final UInt64 genesisTime) {
    return genesisTime.compareTo(MIN_GENESIS_TIME) >= 0;
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
      Bytes32 leaf, SSZVector<Bytes32> branch, int depth, int index, Bytes32 root) {
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
  public static Bytes32 get_seed(BeaconState state, UInt64 epoch, Bytes4 domain_type)
      throws IllegalArgumentException {
    UInt64 randaoIndex = epoch.plus(EPOCHS_PER_HISTORICAL_VECTOR - MIN_SEED_LOOKAHEAD - 1);
    Bytes32 mix = get_randao_mix(state, randaoIndex);
    Bytes epochBytes = uint_to_bytes(epoch.longValue(), 8);
    return Hash.sha2_256(Bytes.concatenate(domain_type.getWrappedBytes(), epochBytes, mix));
  }

  /**
   * Return the combined effective balance of the ``indices``. (EFFECTIVE_BALANCE_INCREMENT Gwei
   * minimum to avoid divisions by zero.)
   *
   * @param state
   * @param indices
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_total_balance</a>
   */
  public static UInt64 get_total_balance(BeaconState state, Collection<Integer> indices) {
    UInt64 sum = UInt64.ZERO;
    SSZList<Validator> validator_registry = state.getValidators();
    for (Integer index : indices) {
      sum = sum.plus(validator_registry.get(index).getEffective_balance());
    }
    return sum.max(EFFECTIVE_BALANCE_INCREMENT);
  }

  /**
   * Return the 32-byte fork data root for the current_version and genesis_validators_root. This is
   * used primarily in signature domains to avoid collisions across forks/chains.
   *
   * @param current_version
   * @param genesis_validators_root
   * @return
   */
  public static Bytes32 compute_fork_data_root(
      Bytes4 current_version, Bytes32 genesis_validators_root) {
    return new ForkData(current_version, genesis_validators_root).hash_tree_root();
  }

  public static Bytes4 compute_fork_digest(
      Bytes4 current_version, Bytes32 genesis_validators_root) {
    return new Bytes4(compute_fork_data_root(current_version, genesis_validators_root).slice(0, 4));
  }

  /**
   * Return the domain for the ``domain_type`` and ``fork_version``.
   *
   * @param domain_type
   * @param fork_version
   * @param genesis_validators_root
   * @return domain
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_domain</a>
   */
  public static Bytes32 compute_domain(
      Bytes4 domain_type, Bytes4 fork_version, Bytes32 genesis_validators_root) {
    final Bytes32 fork_data_root = compute_fork_data_root(fork_version, genesis_validators_root);
    return compute_domain(domain_type, fork_data_root);
  }

  public static Bytes32 compute_domain(final Bytes4 domain_type, final Bytes32 fork_data_root) {
    return Bytes32.wrap(
        Bytes.concatenate(domain_type.getWrappedBytes(), fork_data_root.slice(0, 28)));
  }

  /**
   * Return the domain for the ``domain_type``.
   *
   * @param domain_type
   * @return domain
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_domain</a>
   */
  public static Bytes32 compute_domain(Bytes4 domain_type) {
    return compute_domain(domain_type, GENESIS_FORK_VERSION, Bytes32.ZERO);
  }

  /**
   * Return the signing root for the corresponding signing data.
   *
   * @param object An object implementing the Merkleizable interface
   * @param domain
   * @return the signing root
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.10.0/specs/phase0/beacon-chain.md#compute_signing_root</a>
   */
  public static Bytes compute_signing_root(Merkleizable object, Bytes32 domain) {
    return new SigningData(object.hash_tree_root(), domain).hash_tree_root();
  }

  /**
   * Return the signing root of a 64-bit integer by calculating the root of the object-domain tree.
   *
   * @param number A long value
   * @param domain
   * @return the signing root
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.10.0/specs/phase0/beacon-chain.md#compute_signing_root</a>
   */
  public static Bytes compute_signing_root(long number, Bytes32 domain) {
    SigningData domain_wrapped_object =
        new SigningData(
            HashTreeUtil.hash_tree_root(HashTreeUtil.SSZTypes.BASIC, SSZ.encodeUInt64(number)),
            domain);
    return domain_wrapped_object.hash_tree_root();
  }

  /**
   * Return the signing root of a Bytes object by calculating the root of the object-domain tree.
   *
   * @param bytes Bytes string
   * @param domain
   * @return the signing root
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.10.0/specs/phase0/beacon-chain.md#compute_signing_root</a>
   */
  public static Bytes compute_signing_root(Bytes bytes, Bytes32 domain) {
    SigningData domain_wrapped_object =
        new SigningData(
            HashTreeUtil.hash_tree_root(HashTreeUtil.SSZTypes.VECTOR_OF_BASIC, bytes), domain);
    return domain_wrapped_object.hash_tree_root();
  }

  /**
   * Returns the epoch number of the given slot.
   *
   * @param slot - The slot number under consideration.
   * @return The epoch associated with the given slot number.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_epoch_of_slot</a>
   */
  public static UInt64 compute_epoch_at_slot(UInt64 slot) {
    return slot.dividedBy(Constants.SLOTS_PER_EPOCH);
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
  public static UInt64 get_previous_epoch(BeaconState state) {
    UInt64 current_epoch = get_current_epoch(state);
    return current_epoch.equals(UInt64.valueOf(GENESIS_EPOCH))
        ? UInt64.valueOf(GENESIS_EPOCH)
        : current_epoch.minus(UInt64.ONE);
  }

  /**
   * Return the current epoch of the given state.
   *
   * @param state The beacon state under consideration.
   * @return The current epoch number for the given state.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_current_epoch</a>
   */
  public static UInt64 get_current_epoch(BeaconState state) {
    return compute_epoch_at_slot(state.getSlot());
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
  public static UInt64 get_next_epoch(BeaconState state) {
    return get_current_epoch(state).plus(UInt64.ONE);
  }

  /**
   * Return the starting slot of the given epoch.
   *
   * @param epoch - The epoch under consideration.
   * @return The slot that the given epoch starts at.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_epoch_of_slot</a>
   */
  public static UInt64 compute_start_slot_at_epoch(UInt64 epoch) {
    return epoch.times(SLOTS_PER_EPOCH);
  }

  /**
   * If the given slot is at an epoch boundary returns the current epoch, otherwise returns the next
   * epoch.
   *
   * @param slot The slot for which we want to calculate the next epoch boundary
   * @return Either the current epoch or next epoch depending on whether the slot is at or before an
   *     epoch boundary
   */
  public static UInt64 compute_next_epoch_boundary(final UInt64 slot) {
    final UInt64 currentEpoch = compute_epoch_at_slot(slot);
    return compute_start_slot_at_epoch(currentEpoch).equals(slot)
        ? currentEpoch
        : currentEpoch.plus(1);
  }

  /**
   * Initiate the exit of the validator with index ``index``.
   *
   * @param state
   * @param index
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#initiate_validator_exit</a>
   */
  public static void initiate_validator_exit(MutableBeaconState state, int index) {
    Validator validator = state.getValidators().get(index);
    // Return if validator already initiated exit
    if (!validator.getExit_epoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }

    // Compute exit queue epoch
    List<UInt64> exit_epochs =
        state.getValidators().stream()
            .map(Validator::getExit_epoch)
            .filter(exitEpoch -> !exitEpoch.equals(FAR_FUTURE_EPOCH))
            .collect(Collectors.toList());
    exit_epochs.add(compute_activation_exit_epoch(get_current_epoch(state)));
    UInt64 exit_queue_epoch = Collections.max(exit_epochs);
    final UInt64 final_exit_queue_epoch = exit_queue_epoch;
    UInt64 exit_queue_churn =
        UInt64.valueOf(
            state.getValidators().stream()
                .filter(v -> v.getExit_epoch().equals(final_exit_queue_epoch))
                .count());

    if (exit_queue_churn.compareTo(get_validator_churn_limit(state)) >= 0) {
      exit_queue_epoch = exit_queue_epoch.plus(UInt64.ONE);
    }

    // Set validator exit epoch and withdrawable epoch
    state
        .getValidators()
        .set(
            index,
            validator
                .withExit_epoch(exit_queue_epoch)
                .withWithdrawable_epoch(
                    exit_queue_epoch.plus(MIN_VALIDATOR_WITHDRAWABILITY_DELAY)));
  }

  /**
   * Slash the validator with index ``slashed_index``.
   *
   * @param state
   * @param slashed_index
   * @param whistleblower_index
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#slash_validator/a>
   */
  public static void slash_validator(
      MutableBeaconState state, int slashed_index, int whistleblower_index) {
    UInt64 epoch = get_current_epoch(state);
    initiate_validator_exit(state, slashed_index);

    Validator validator = state.getValidators().get(slashed_index);

    state
        .getValidators()
        .set(
            slashed_index,
            validator
                .withSlashed(true)
                .withWithdrawable_epoch(
                    validator
                        .getWithdrawable_epoch()
                        .max(epoch.plus(EPOCHS_PER_SLASHINGS_VECTOR))));

    int index = epoch.mod(EPOCHS_PER_SLASHINGS_VECTOR).intValue();
    state
        .getSlashings()
        .set(index, state.getSlashings().get(index).plus(validator.getEffective_balance()));
    decrease_balance(
        state,
        slashed_index,
        validator.getEffective_balance().dividedBy(MIN_SLASHING_PENALTY_QUOTIENT));

    // Apply proposer and whistleblower rewards
    int proposer_index = get_beacon_proposer_index(state);
    if (whistleblower_index == -1) {
      whistleblower_index = proposer_index;
    }

    UInt64 whistleblower_reward =
        validator.getEffective_balance().dividedBy(WHISTLEBLOWER_REWARD_QUOTIENT);
    UInt64 proposer_reward = whistleblower_reward.dividedBy(PROPOSER_REWARD_QUOTIENT);
    increase_balance(state, proposer_index, proposer_reward);
    increase_balance(state, whistleblower_index, whistleblower_reward.minus(proposer_reward));
  }

  public static void slash_validator(MutableBeaconState state, int slashed_index) {
    slash_validator(state, slashed_index, -1);
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
  public static Bytes32 get_block_root(BeaconState state, UInt64 epoch)
      throws IllegalArgumentException {
    return get_block_root_at_slot(state, compute_start_slot_at_epoch(epoch));
  }

  /**
   * return the number of committees in each slot for the given `epoch`.
   *
   * @param state
   * @param epoch
   * @return
   */
  public static UInt64 get_committee_count_per_slot(BeaconState state, UInt64 epoch) {
    List<Integer> active_validator_indices = get_active_validator_indices(state, epoch);
    return UInt64.valueOf(
        Math.max(
            1,
            Math.min(
                MAX_COMMITTEES_PER_SLOT,
                Math.floorDiv(
                    Math.floorDiv(active_validator_indices.size(), SLOTS_PER_EPOCH),
                    TARGET_COMMITTEE_SIZE))));
  }

  /**
   * Return the randao mix at a recent ``epoch``.
   *
   * @param state
   * @param epoch
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_randao_mix</a>
   */
  public static Bytes32 get_randao_mix(BeaconState state, UInt64 epoch) {
    int index = epoch.mod(EPOCHS_PER_HISTORICAL_VECTOR).intValue();
    return state.getRandao_mixes().get(index);
  }

  /**
   * Return the beacon proposer index at the current slot.
   *
   * @param state
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_beacon_proposer_index</a>
   */
  public static int get_beacon_proposer_index(BeaconState state) {
    return get_beacon_proposer_index(state, state.getSlot());
  }

  public static int get_beacon_proposer_index(BeaconState state, UInt64 requestedSlot) {
    validateStateCanCalculateProposerIndexAtSlot(state, requestedSlot);
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconProposerIndex()
        .get(
            requestedSlot,
            slot -> {
              UInt64 epoch = compute_epoch_at_slot(slot);
              Bytes32 seed =
                  Hash.sha2_256(
                      Bytes.concatenate(
                          get_seed(state, epoch, DOMAIN_BEACON_PROPOSER),
                          uint_to_bytes(slot.longValue(), 8)));
              List<Integer> indices = get_active_validator_indices(state, epoch);
              return compute_proposer_index(state, indices, seed);
            });
  }

  private static void validateStateCanCalculateProposerIndexAtSlot(
      final BeaconState state, final UInt64 requestedSlot) {
    UInt64 epoch = compute_epoch_at_slot(requestedSlot);
    final UInt64 stateEpoch = get_current_epoch(state);
    checkArgument(
        epoch.equals(stateEpoch),
        "Cannot calculate proposer index for a slot outside the current epoch. Requested slot %s (in epoch %s), state slot %s (in epoch %s)",
        requestedSlot,
        epoch,
        state.getSlot(),
        stateEpoch);
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
  public static Bytes32 get_domain(BeaconState state, Bytes4 domain_type, UInt64 message_epoch) {
    UInt64 epoch = (message_epoch == null) ? get_current_epoch(state) : message_epoch;
    return get_domain(domain_type, epoch, state.getFork(), state.getGenesis_validators_root());
  }
  /**
   * Return the signature domain (fork version concatenated with domain type) of a message.
   *
   * @param domain_type the domain for the message
   * @param epoch the epoch the message being signed is from
   * @param fork the current fork
   * @param genesis_validators_root the validators root from genesis
   * @return The fork version and signature domain. This format ((fork version << 32) +
   *     SignatureDomain) is used to partition BLS signatures.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_domain</a>
   */
  public static Bytes32 get_domain(
      final Bytes4 domain_type,
      final UInt64 epoch,
      final Fork fork,
      final Bytes32 genesis_validators_root) {
    Bytes4 fork_version =
        (epoch.compareTo(fork.getEpoch()) < 0)
            ? fork.getPrevious_version()
            : fork.getCurrent_version();
    return compute_domain(domain_type, fork_version, genesis_validators_root);
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
  public static Bytes32 get_domain(BeaconState state, Bytes4 domain_type) {
    return get_domain(state, domain_type, null);
  }

  /**
   * Return the churn limit based on the active validator count.
   *
   * @param state
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_validator_churn_limit</a>
   */
  public static UInt64 get_validator_churn_limit(BeaconState state) {
    List<Integer> active_validator_indices =
        get_active_validator_indices(state, get_current_epoch(state));
    return UInt64.valueOf(MIN_PER_EPOCH_CHURN_LIMIT)
        .max(UInt64.valueOf(active_validator_indices.size() / CHURN_LIMIT_QUOTIENT));
  }

  /**
   * Return the epoch at which an activation or exit triggered in `epoch` takes effect. g
   *
   * @param epoch - The epoch under consideration.
   * @return The epoch at which an activation or exit in the given `epoch` will take effect.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_activation_exit_epoch</a>
   */
  public static UInt64 compute_activation_exit_epoch(UInt64 epoch) {
    return epoch.plus(UInt64.ONE).plus(MAX_SEED_LOOKAHEAD);
  }

  public static boolean all(Bitvector bitvector, int start, int end) {
    for (int i = start; i < end; i++) {
      if (!bitvector.getBit(i)) {
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
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#integer_squareroot</a>
   */
  public static UInt64 integer_squareroot(UInt64 n) {
    checkArgument(
        n.compareTo(UInt64.ZERO) >= 0, "checkArgument threw an exception in integer_squareroot()");
    UInt64 x = n;
    UInt64 y = x.plus(UInt64.ONE).dividedBy(2);
    while (y.compareTo(x) < 0) {
      x = y;
      y = x.plus(n.dividedBy(x)).dividedBy(2);
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
  public static Bytes uint_to_bytes(long value, int numBytes) {
    int longBytes = Long.SIZE / 8;
    Bytes valueBytes = Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
    if (numBytes <= longBytes) {
      return valueBytes.slice(0, numBytes);
    } else {
      return Bytes.wrap(valueBytes, Bytes.wrap(new byte[numBytes - longBytes]));
    }
  }

  public static Bytes32 uint_to_bytes32(long value) {
    return Bytes32.wrap(uint_to_bytes(value, 32));
  }

  public static Bytes32 uint_to_bytes32(UInt64 value) {
    return uint_to_bytes32(value.longValue());
  }

  /**
   * @param data - The value to be converted to int.
   * @return An integer representation of the bytes value given.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#bytes_to_int</a>
   */
  public static UInt64 bytes_to_int64(Bytes data) {
    return UInt64.fromLongBits(data.toLong(ByteOrder.LITTLE_ENDIAN));
  }

  public static Bytes32 getCurrentDutyDependentRoot(BeaconState state) {
    final UInt64 slot = compute_start_slot_at_epoch(get_current_epoch(state)).minusMinZero(1);
    // No previous block, use algorithm for calculating the genesis block root
    return slot.equals(state.getSlot())
        ? BeaconBlock.fromGenesisState(state).getRoot()
        : get_block_root_at_slot(state, slot);
  }

  public static Bytes32 getPreviousDutyDependentRoot(BeaconState state) {
    final UInt64 slot = compute_start_slot_at_epoch(get_previous_epoch(state)).minusMinZero(1);
    return slot.equals(state.getSlot())
        // No previous block, use algorithm for calculating the genesis block root
        ? BeaconBlock.fromGenesisState(state).getRoot()
        : get_block_root_at_slot(state, slot);
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
  public static Bytes32 get_block_root_at_slot(BeaconState state, UInt64 slot)
      throws IllegalArgumentException {
    checkArgument(
        isBlockRootAvailableFromState(state, slot),
        "Block at slot %s not available from state at slot %s",
        slot,
        state.getSlot());
    int latestBlockRootIndex = slot.mod(SLOTS_PER_HISTORICAL_ROOT).intValue();
    return state.getBlock_roots().get(latestBlockRootIndex);
  }

  public static boolean isBlockRootAvailableFromState(BeaconState state, UInt64 slot) {
    UInt64 slotPlusHistoricalRoot = slot.plus(SLOTS_PER_HISTORICAL_ROOT);
    return slot.isLessThan(state.getSlot())
        && state.getSlot().isLessThanOrEqualTo(slotPlusHistoricalRoot);
  }

  public static boolean isSlotAtNthEpochBoundary(
      final UInt64 blockSlot, final UInt64 parentSlot, final int n) {
    checkArgument(n > 0, "Parameter n must be greater than 0");
    final UInt64 blockEpoch = compute_epoch_at_slot(blockSlot);
    final UInt64 parentEpoch = compute_epoch_at_slot(parentSlot);
    return blockEpoch.dividedBy(n).isGreaterThan(parentEpoch.dividedBy(n));
  }
}
