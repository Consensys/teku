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

package tech.pegasys.teku.spec.datastructures.execution;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class ExpectedWithdrawals {
  public static final ExpectedWithdrawals NOOP = new ExpectedWithdrawals(List.of(), 0);
  public final List<Withdrawal> withdrawalList;
  private final int partialWithdrawalCount;

  public ExpectedWithdrawals(
      final List<Withdrawal> withdrawalList, final int partialWithdrawalCount) {
    this.withdrawalList = withdrawalList;
    this.partialWithdrawalCount = partialWithdrawalCount;
  }

  public static ExpectedWithdrawals create(
      final BeaconStateCapella preState,
      final SchemaDefinitionsCapella schemaDefinitionsCapella,
      final MiscHelpers miscHelpers,
      final SpecConfigCapella specConfigCapella,
      final Predicates predicates) {
    final List<Withdrawal> capellaWithdrawals =
        getExpectedWithdrawals(
            preState,
            schemaDefinitionsCapella,
            miscHelpers,
            specConfigCapella,
            predicates,
            new ArrayList<>());
    return new ExpectedWithdrawals(capellaWithdrawals, 0);
  }

  public static ExpectedWithdrawals create(
      final BeaconStateElectra preState,
      final SchemaDefinitionsElectra schemaDefinitions,
      final MiscHelpersElectra miscHelpers,
      final SpecConfigElectra specConfig,
      final PredicatesElectra predicates) {
    final List<Withdrawal> partialPendingWithdrawals =
        getPendingPartialWithdrawals(preState, schemaDefinitions, miscHelpers, specConfig);
    final int partialWithdrawalsCount = partialPendingWithdrawals.size();
    final List<Withdrawal> capellaWithdrawals =
        getExpectedWithdrawals(
            preState,
            schemaDefinitions,
            miscHelpers,
            specConfig,
            predicates,
            partialPendingWithdrawals);
    return new ExpectedWithdrawals(capellaWithdrawals, partialWithdrawalsCount);
  }

  public List<Withdrawal> getWithdrawalList() {
    return withdrawalList;
  }

  public int getPartialWithdrawalCount() {
    return partialWithdrawalCount;
  }

  public Optional<List<Withdrawal>> getExpectedWithdrawals() {
    if (withdrawalList.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(withdrawalList);
  }

  private static List<Withdrawal> getPendingPartialWithdrawals(
      final BeaconStateElectra preState,
      final SchemaDefinitionsElectra schemaDefinitions,
      final MiscHelpersElectra miscHelpers,
      final SpecConfigElectra specConfig) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(preState.getSlot());
    final int maxPendingPartialWithdrawals = specConfig.getMaxPendingPartialsPerWithdrawalsSweep();
    final List<Withdrawal> partialWithdrawals = new ArrayList<>();
    final SszList<PendingPartialWithdrawal> pendingPartialWithdrawals =
        preState.getPendingPartialWithdrawals();
    UInt64 withdrawalIndex = preState.getNextWithdrawalIndex();

    for (int i = 0; i < pendingPartialWithdrawals.size() && i < maxPendingPartialWithdrawals; i++) {
      final PendingPartialWithdrawal pendingPartialWithdrawal = pendingPartialWithdrawals.get(i);
      if (pendingPartialWithdrawal.getWithdrawableEpoch().isGreaterThan(epoch)) {
        break;
      }
      final Validator validator = preState.getValidators().get(pendingPartialWithdrawal.getIndex());
      final boolean hasSufficientBalance =
          validator
              .getEffectiveBalance()
              .isGreaterThanOrEqualTo(specConfig.getMinActivationBalance());
      final UInt64 validatorBalance =
          preState.getBalances().get(pendingPartialWithdrawal.getIndex()).get();
      final boolean hasExcessBalance =
          validatorBalance.isGreaterThan(specConfig.getMinActivationBalance());
      if (validator.getExitEpoch().equals(FAR_FUTURE_EPOCH)
          && hasSufficientBalance
          && hasExcessBalance) {
        final UInt64 withdrawableBalance =
            pendingPartialWithdrawal
                .getAmount()
                .min(validatorBalance.minusMinZero(specConfig.getMinActivationBalance()));
        partialWithdrawals.add(
            schemaDefinitions
                .getWithdrawalSchema()
                .create(
                    withdrawalIndex,
                    UInt64.valueOf(pendingPartialWithdrawal.getIndex()),
                    new Bytes20(validator.getWithdrawalCredentials().slice(12)),
                    withdrawableBalance));
        withdrawalIndex = withdrawalIndex.increment();
      }
    }
    return partialWithdrawals;
  }

  // get_expected_withdrawals
  private static List<Withdrawal> getExpectedWithdrawals(
      final BeaconStateCapella preState,
      final SchemaDefinitionsCapella schemaDefinitionsCapella,
      final MiscHelpers miscHelpers,
      final SpecConfigCapella specConfigCapella,
      final Predicates predicates,
      final List<Withdrawal> partialWithdrawals) {
    final List<Withdrawal> expectedWithdrawals = partialWithdrawals;
    final WithdrawalSchema withdrawalSchema = schemaDefinitionsCapella.getWithdrawalSchema();
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(preState.getSlot());
    final SszList<Validator> validators = preState.getValidators();
    final SszUInt64List balances = preState.getBalances();
    final int validatorCount = validators.size();
    final int maxWithdrawalsPerPayload = specConfigCapella.getMaxWithdrawalsPerPayload();
    final int maxValidatorsPerWithdrawalsSweep =
        specConfigCapella.getMaxValidatorsPerWithdrawalSweep();
    final int bound = Math.min(validatorCount, maxValidatorsPerWithdrawalsSweep);

    final UInt64 maxEffectiveBalance = specConfigCapella.getMaxEffectiveBalance();

    UInt64 withdrawalIndex =
        partialWithdrawals.isEmpty()
            ? preState.getNextWithdrawalIndex()
            : nextWithdrawalAfter(partialWithdrawals);
    int validatorIndex = preState.getNextWithdrawalValidatorIndex().intValue();

    for (int i = 0; i < bound; i++) {
      final Validator validator = validators.get(validatorIndex);
      if (predicates.hasEth1WithdrawalCredential(validator)) {
        final UInt64 balance = balances.get(validatorIndex).get();

        if (predicates.isFullyWithdrawableValidatorCredentialsChecked(validator, balance, epoch)) {
          expectedWithdrawals.add(
              withdrawalSchema.create(
                  withdrawalIndex,
                  UInt64.valueOf(validatorIndex),
                  new Bytes20(validator.getWithdrawalCredentials().slice(12)),
                  balance));
          withdrawalIndex = withdrawalIndex.increment();
        } else if (predicates.isPartiallyWithdrawableValidatorEth1CredentialsChecked(
            validator, balance)) {
          expectedWithdrawals.add(
              withdrawalSchema.create(
                  withdrawalIndex,
                  UInt64.valueOf(validatorIndex),
                  new Bytes20(validator.getWithdrawalCredentials().slice(12)),
                  balance.minus(maxEffectiveBalance)));
          withdrawalIndex = withdrawalIndex.increment();
        }

        if (expectedWithdrawals.size() == maxWithdrawalsPerPayload) {
          break;
        }
      }

      validatorIndex = (validatorIndex + 1) % validatorCount;
    }

    return expectedWithdrawals;
  }

  private static UInt64 nextWithdrawalAfter(final List<Withdrawal> partialWithdrawals) {
    return partialWithdrawals.get(partialWithdrawals.size() - 1).getIndex().increment();
  }
}
