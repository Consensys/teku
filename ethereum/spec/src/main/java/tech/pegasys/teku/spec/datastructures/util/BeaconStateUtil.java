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

package tech.pegasys.teku.spec.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.datastructures.util.ValidatorsUtil.decrease_balance;
import static tech.pegasys.teku.spec.datastructures.util.ValidatorsUtil.get_active_validator_indices;
import static tech.pegasys.teku.spec.datastructures.util.ValidatorsUtil.increase_balance;
import static tech.pegasys.teku.util.config.Constants.CHURN_LIMIT_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.EFFECTIVE_BALANCE_INCREMENT;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_HISTORICAL_VECTOR;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_SLASHINGS_VECTOR;
import static tech.pegasys.teku.util.config.Constants.MAX_COMMITTEES_PER_SLOT;
import static tech.pegasys.teku.util.config.Constants.MAX_EFFECTIVE_BALANCE;
import static tech.pegasys.teku.util.config.Constants.MAX_SEED_LOOKAHEAD;
import static tech.pegasys.teku.util.config.Constants.MIN_PER_EPOCH_CHURN_LIMIT;
import static tech.pegasys.teku.util.config.Constants.MIN_SEED_LOOKAHEAD;
import static tech.pegasys.teku.util.config.Constants.MIN_SLASHING_PENALTY_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.MIN_VALIDATOR_WITHDRAWABILITY_DELAY;
import static tech.pegasys.teku.util.config.Constants.PROPOSER_REWARD_QUOTIENT;
import static tech.pegasys.teku.util.config.Constants.SHUFFLE_ROUND_COUNT;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.teku.util.config.Constants.TARGET_COMMITTEE_SIZE;
import static tech.pegasys.teku.util.config.Constants.WHISTLEBLOWER_REWARD_QUOTIENT;

import com.google.common.primitives.UnsignedBytes;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.SigningData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.util.config.Constants;

@Deprecated
public class BeaconStateUtil {

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
  @Deprecated
  private static Bytes32 get_seed(BeaconState state, UInt64 epoch, Bytes4 domain_type)
      throws IllegalArgumentException {
    UInt64 randaoIndex = epoch.plus(EPOCHS_PER_HISTORICAL_VECTOR - MIN_SEED_LOOKAHEAD - 1);
    Bytes32 mix = get_randao_mix(state, randaoIndex);
    Bytes epochBytes = uint_to_bytes(epoch.longValue(), 8);
    return Hash.sha256(Bytes.concatenate(domain_type.getWrappedBytes(), epochBytes, mix));
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
  @Deprecated
  static UInt64 get_total_balance(BeaconState state, Collection<Integer> indices) {
    UInt64 sum = UInt64.ZERO;
    SszList<Validator> validator_registry = state.getValidators();
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
  @Deprecated
  private static Bytes32 compute_fork_data_root(
      Bytes4 current_version, Bytes32 genesis_validators_root) {
    return new ForkData(current_version, genesis_validators_root).hashTreeRoot();
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
  @Deprecated
  private static Bytes32 compute_domain(
      Bytes4 domain_type, Bytes4 fork_version, Bytes32 genesis_validators_root) {
    final Bytes32 fork_data_root = compute_fork_data_root(fork_version, genesis_validators_root);
    return compute_domain(domain_type, fork_data_root);
  }

  @Deprecated
  private static Bytes32 compute_domain(final Bytes4 domain_type, final Bytes32 fork_data_root) {
    return Bytes32.wrap(
        Bytes.concatenate(domain_type.getWrappedBytes(), fork_data_root.slice(0, 28)));
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
  @Deprecated
  public static Bytes compute_signing_root(Merkleizable object, Bytes32 domain) {
    return new SigningData(object.hashTreeRoot(), domain).hashTreeRoot();
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
  @Deprecated
  public static Bytes compute_signing_root(long number, Bytes32 domain) {

    SigningData domain_wrapped_object =
        new SigningData(SszUInt64.of(UInt64.valueOf(number)).hashTreeRoot(), domain);
    return domain_wrapped_object.hashTreeRoot();
  }

  /**
   * Returns the epoch number of the given slot.
   *
   * @param slot - The slot number under consideration.
   * @return The epoch associated with the given slot number.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_epoch_of_slot</a>
   */
  @Deprecated
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
  @Deprecated
  public static UInt64 get_previous_epoch(BeaconState state) {
    UInt64 current_epoch = get_current_epoch(state);
    return current_epoch.equals(SpecConfig.GENESIS_EPOCH)
        ? SpecConfig.GENESIS_EPOCH
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
  @Deprecated
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
  @Deprecated
  static UInt64 get_next_epoch(BeaconState state) {
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
  @Deprecated
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
  @Deprecated
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
  @Deprecated
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
  @Deprecated
  private static void slash_validator(
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
        .setElement(
            index, state.getSlashings().getElement(index).plus(validator.getEffective_balance()));
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

  @Deprecated
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
  @Deprecated
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
  @Deprecated
  public static UInt64 get_committee_count_per_slot(BeaconState state, UInt64 epoch) {
    IntList active_validator_indices = get_active_validator_indices(state, epoch);
    return get_committee_count_per_slot(active_validator_indices.size());
  }

  @Deprecated
  public static UInt64 get_committee_count_per_slot(final int activeValidatorCount) {
    return UInt64.valueOf(
        Math.max(
            1,
            Math.min(
                MAX_COMMITTEES_PER_SLOT,
                Math.floorDiv(
                    Math.floorDiv(activeValidatorCount, SLOTS_PER_EPOCH), TARGET_COMMITTEE_SIZE))));
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
  @Deprecated
  public static Bytes32 get_randao_mix(BeaconState state, UInt64 epoch) {
    int index = epoch.mod(EPOCHS_PER_HISTORICAL_VECTOR).intValue();
    return state.getRandao_mixes().getElement(index);
  }

  /**
   * Return the beacon proposer index at the current slot.
   *
   * @param state
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#get_beacon_proposer_index</a>
   */
  @Deprecated
  public static int get_beacon_proposer_index(BeaconState state) {
    return get_beacon_proposer_index(state, state.getSlot());
  }

  @Deprecated
  public static int get_beacon_proposer_index(BeaconState state, UInt64 requestedSlot) {
    validateStateCanCalculateProposerIndexAtSlot(state, requestedSlot);
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconProposerIndex()
        .get(
            requestedSlot,
            slot -> {
              UInt64 epoch = compute_epoch_at_slot(slot);
              Bytes32 seed =
                  Hash.sha256(
                      Bytes.concatenate(
                          get_seed(state, epoch, Domain.BEACON_PROPOSER),
                          uint_to_bytes(slot.longValue(), 8)));
              IntList indices = get_active_validator_indices(state, epoch);
              return compute_proposer_index(state, indices, seed);
            });
  }

  @Deprecated
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
  @Deprecated
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
  @Deprecated
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
  @Deprecated
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
  @Deprecated
  public static UInt64 get_validator_churn_limit(BeaconState state) {
    final int activeValidatorCount =
        get_active_validator_indices(state, get_current_epoch(state)).size();
    return get_validator_churn_limit(activeValidatorCount);
  }

  @Deprecated
  public static UInt64 get_validator_churn_limit(final int activeValidatorCount) {
    return UInt64.valueOf(MIN_PER_EPOCH_CHURN_LIMIT)
        .max(UInt64.valueOf(activeValidatorCount / CHURN_LIMIT_QUOTIENT));
  }

  /**
   * Return the epoch at which an activation or exit triggered in `epoch` takes effect. g
   *
   * @param epoch - The epoch under consideration.
   * @return The epoch at which an activation or exit in the given `epoch` will take effect.
   * @see <a>
   *     https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#compute_activation_exit_epoch</a>
   */
  @Deprecated
  public static UInt64 compute_activation_exit_epoch(UInt64 epoch) {
    return epoch.plus(UInt64.ONE).plus(MAX_SEED_LOOKAHEAD);
  }

  @Deprecated
  public static boolean all(SszBitvector bitvector, int start, int end) {
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
  @Deprecated
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
  @Deprecated
  public static Bytes uint_to_bytes(long value, int numBytes) {
    int longBytes = Long.SIZE / 8;
    Bytes valueBytes = Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
    if (numBytes <= longBytes) {
      return valueBytes.slice(0, numBytes);
    } else {
      return Bytes.wrap(valueBytes, Bytes.wrap(new byte[numBytes - longBytes]));
    }
  }

  @Deprecated
  public static Bytes32 uint_to_bytes32(long value) {
    return Bytes32.wrap(uint_to_bytes(value, 32));
  }

  @Deprecated
  public static Bytes32 uint_to_bytes32(UInt64 value) {
    return uint_to_bytes32(value.longValue());
  }

  /**
   * @param data - The value to be converted to int.
   * @return An integer representation of the bytes value given.
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#bytes_to_int</a>
   */
  @Deprecated
  public static UInt64 bytes_to_int64(Bytes data) {
    return UInt64.fromLongBits(data.toLong(ByteOrder.LITTLE_ENDIAN));
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
  @Deprecated
  public static Bytes32 get_block_root_at_slot(BeaconState state, UInt64 slot)
      throws IllegalArgumentException {
    checkArgument(
        isBlockRootAvailableFromState(state, slot),
        "Block at slot %s not available from state at slot %s",
        slot,
        state.getSlot());
    int latestBlockRootIndex = slot.mod(SLOTS_PER_HISTORICAL_ROOT).intValue();
    return state.getBlock_roots().getElement(latestBlockRootIndex);
  }

  @Deprecated
  public static boolean isBlockRootAvailableFromState(BeaconState state, UInt64 slot) {
    UInt64 slotPlusHistoricalRoot = slot.plus(SLOTS_PER_HISTORICAL_ROOT);
    return slot.isLessThan(state.getSlot())
        && state.getSlot().isLessThanOrEqualTo(slotPlusHistoricalRoot);
  }

  @Deprecated
  public static boolean isSlotAtNthEpochBoundary(
      final UInt64 blockSlot, final UInt64 parentSlot, final int n) {
    checkArgument(n > 0, "Parameter n must be greater than 0");
    final UInt64 blockEpoch = compute_epoch_at_slot(blockSlot);
    final UInt64 parentEpoch = compute_epoch_at_slot(parentSlot);
    return blockEpoch.dividedBy(n).isGreaterThan(parentEpoch.dividedBy(n));
  }

  /**
   * Return the shuffled validator index corresponding to ``seed`` (and ``index_count``).
   *
   * @param index
   * @param index_count
   * @param seed
   * @deprecated CommitteeUtil should be accessed via Spec.getCommitteeUtil().computeShuffledIndex()
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#is_valid_merkle_branch</a>
   */
  @Deprecated
  static int compute_shuffled_index(int index, int index_count, Bytes32 seed) {
    checkArgument(index < index_count, "index must be less than index_count");

    int indexRet = index;

    for (int round = 0; round < SHUFFLE_ROUND_COUNT; round++) {

      Bytes roundAsByte = Bytes.of((byte) round);

      // This needs to be unsigned modulo.
      int pivot =
          bytes_to_int64(Hash.sha256(Bytes.wrap(seed, roundAsByte)).slice(0, 8))
              .mod(index_count)
              .intValue();
      int flip = Math.floorMod(pivot + index_count - indexRet, index_count);
      int position = Math.max(indexRet, flip);

      Bytes positionDiv256 = uint_to_bytes(Math.floorDiv(position, 256), 4);
      Bytes hashBytes = Hash.sha256(Bytes.wrap(seed, roundAsByte, positionDiv256));

      int bitIndex = position & 0xff;
      int theByte = hashBytes.get(bitIndex / 8);
      int theBit = (theByte >> (bitIndex & 0x07)) & 1;
      if (theBit != 0) {
        indexRet = flip;
      }
    }

    return indexRet;
  }

  /**
   * Return from ``indices`` a random index sampled by effective balance.
   *
   * @param state
   * @param indices
   * @param seed
   * @return
   */
  @Deprecated
  private static int compute_proposer_index(BeaconState state, IntList indices, Bytes32 seed) {
    checkArgument(!indices.isEmpty(), "compute_proposer_index indices must not be empty");
    UInt64 MAX_RANDOM_BYTE = UInt64.valueOf(255); // Math.pow(2, 8) - 1;
    int i = 0;
    final int total = indices.size();
    Bytes32 hash = null;
    while (true) {
      int candidate_index = indices.getInt(compute_shuffled_index(i % total, total, seed));
      if (i % 32 == 0) {
        hash = Hash.sha256(Bytes.concatenate(seed, uint_to_bytes(Math.floorDiv(i, 32), 8)));
      }
      int random_byte = UnsignedBytes.toInt(hash.get(i % 32));
      UInt64 effective_balance = state.getValidators().get(candidate_index).getEffective_balance();
      if (effective_balance
          .times(MAX_RANDOM_BYTE)
          .isGreaterThanOrEqualTo(MAX_EFFECTIVE_BALANCE.times(random_byte))) {
        return candidate_index;
      }
      i++;
    }
  }
}
