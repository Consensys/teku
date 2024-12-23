/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.crypto.Hash.getSha256Instance;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;
import static tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra.MAX_RANDOM_VALUE;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Sha256;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.BeaconStateAccessorsDeneb;

public class BeaconStateAccessorsElectra extends BeaconStateAccessorsDeneb {

  private final SpecConfigElectra configElectra;
  protected PredicatesElectra predicatesElectra;

  public BeaconStateAccessorsElectra(
      final SpecConfig config,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersElectra miscHelpers) {
    super(SpecConfigDeneb.required(config), predicatesElectra, miscHelpers);
    configElectra = config.toVersionElectra().orElseThrow();
    this.predicatesElectra = predicatesElectra;
  }

  /*
   * <spec function="get_activation_exit_churn_limit" fork="electra">
   * def get_activation_exit_churn_limit(state: BeaconState) -> Gwei:
   *     """
   *     Return the churn limit for the current epoch dedicated to activations and exits.
   *     """
   *     return min(MAX_PER_EPOCH_ACTIVATION_EXIT_CHURN_LIMIT, get_balance_churn_limit(state))
   * </spec>
   */
  public UInt64 getActivationExitChurnLimit(final BeaconStateElectra state) {
    return getBalanceChurnLimit(state).min(configElectra.getMaxPerEpochActivationExitChurnLimit());
  }

  /*
   * <spec function="get_pending_balance_to_withdraw" fork="electra">
   * def get_pending_balance_to_withdraw(state: BeaconState, validator_index: ValidatorIndex) -> Gwei:
   *     return sum(
   *         withdrawal.amount for withdrawal in state.pending_partial_withdrawals
   *         if withdrawal.validator_index == validator_index
   *     )
   * </spec>
   */
  public UInt64 getPendingBalanceToWithdraw(
      final BeaconStateElectra state, final int validatorIndex) {
    final List<PendingPartialWithdrawal> partialWithdrawals =
        state.getPendingPartialWithdrawals().asList();
    return partialWithdrawals.stream()
        .filter(z -> z.getValidatorIndex() == validatorIndex)
        .map(PendingPartialWithdrawal::getAmount)
        .reduce(UInt64.ZERO, UInt64::plus);
  }

  /*
   * <spec function="get_balance_churn_limit" fork="electra">
   * def get_balance_churn_limit(state: BeaconState) -> Gwei:
   *     """
   *     Return the churn limit for the current epoch.
   *     """
   *     churn = max(
   *         MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA,
   *         get_total_active_balance(state) // CHURN_LIMIT_QUOTIENT
   *     )
   *     return churn - churn % EFFECTIVE_BALANCE_INCREMENT
   * </spec>
   */
  public UInt64 getBalanceChurnLimit(final BeaconStateElectra state) {
    final UInt64 churn =
        configElectra
            .getMinPerEpochChurnLimitElectra()
            .max(getTotalActiveBalance(state).dividedBy(configElectra.getChurnLimitQuotient()));
    return churn.minusMinZero(churn.mod(configElectra.getEffectiveBalanceIncrement()));
  }

  /*
   * <spec function="get_consolidation_churn_limit" fork="electra">
   * def get_consolidation_churn_limit(state: BeaconState) -> Gwei:
   *     return get_balance_churn_limit(state) - get_activation_exit_churn_limit(state)
   * </spec>
   */
  public UInt64 getConsolidationChurnLimit(final BeaconStateElectra state) {
    return getBalanceChurnLimit(state).minusMinZero(getActivationExitChurnLimit(state));
  }

  public static BeaconStateAccessorsElectra required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsElectra,
        "Expected %s but it was %s",
        BeaconStateAccessorsElectra.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsElectra) beaconStateAccessors;
  }

  @Override
  public IntList getNextSyncCommitteeIndices(final BeaconState state) {
    return getNextSyncCommitteeIndices(state, configElectra.getMaxEffectiveBalanceElectra());
  }

  /*
   * <spec function="get_next_sync_committee_indices" fork="electra">
   * def get_next_sync_committee_indices(state: BeaconState) -> Sequence[ValidatorIndex]:
   *     """
   *     Return the sync committee indices, with possible duplicates, for the next sync committee.
   *     """
   *     epoch = Epoch(get_current_epoch(state) + 1)
   *
   *     MAX_RANDOM_VALUE = 2**16 - 1  # [Modified in Electra]
   *     active_validator_indices = get_active_validator_indices(state, epoch)
   *     active_validator_count = uint64(len(active_validator_indices))
   *     seed = get_seed(state, epoch, DOMAIN_SYNC_COMMITTEE)
   *     i = uint64(0)
   *     sync_committee_indices: List[ValidatorIndex] = []
   *     while len(sync_committee_indices) < SYNC_COMMITTEE_SIZE:
   *         shuffled_index = compute_shuffled_index(uint64(i % active_validator_count), active_validator_count, seed)
   *         candidate_index = active_validator_indices[shuffled_index]
   *         # [Modified in Electra]
   *         random_bytes = hash(seed + uint_to_bytes(i // 16))
   *         offset = i % 16 * 2
   *         random_value = bytes_to_uint64(random_bytes[offset:offset + 2])
   *         effective_balance = state.validators[candidate_index].effective_balance
   *         # [Modified in Electra:EIP7251]
   *         if effective_balance * MAX_RANDOM_VALUE >= MAX_EFFECTIVE_BALANCE_ELECTRA * random_value:
   *             sync_committee_indices.append(candidate_index)
   *         i += 1
   *     return sync_committee_indices
   * </spec>
   */
  @Override
  protected IntList getNextSyncCommitteeIndices(
      final BeaconState state, final UInt64 maxEffectiveBalance) {
    final UInt64 epoch = getCurrentEpoch(state).plus(1);
    final IntList activeValidatorIndices = getActiveValidatorIndices(state, epoch);
    final int activeValidatorCount = activeValidatorIndices.size();
    checkArgument(activeValidatorCount > 0, "Provided state has no active validators");

    final Bytes32 seed = getSeed(state, epoch, Domain.SYNC_COMMITTEE);
    final SszList<Validator> validators = state.getValidators();
    final IntList syncCommitteeIndices = new IntArrayList();
    final int syncCommitteeSize = configElectra.getSyncCommitteeSize();
    final Sha256 sha256 = getSha256Instance();

    int i = 0;
    Bytes randomBytes = null;
    while (syncCommitteeIndices.size() < syncCommitteeSize) {
      if (i % 16 == 0) {
        randomBytes = Bytes.wrap(sha256.digest(seed, uint64ToBytes(Math.floorDiv(i, 16L))));
      }
      final int shuffledIndex =
          miscHelpers.computeShuffledIndex(i % activeValidatorCount, activeValidatorCount, seed);
      final int candidateIndex = activeValidatorIndices.getInt(shuffledIndex);
      final int offset = (i % 16) * 2;
      final UInt64 randomValue = bytesToUInt64(randomBytes.slice(offset, 2));
      final UInt64 effectiveBalance = validators.get(candidateIndex).getEffectiveBalance();
      if (effectiveBalance
          .times(MAX_RANDOM_VALUE)
          .isGreaterThanOrEqualTo(maxEffectiveBalance.times(randomValue))) {
        syncCommitteeIndices.add(candidateIndex);
      }
      i++;
    }

    return syncCommitteeIndices;
  }
}
