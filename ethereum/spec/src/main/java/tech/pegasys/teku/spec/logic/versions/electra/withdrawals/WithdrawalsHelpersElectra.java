/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.electra.withdrawals;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.capella.withdrawals.WithdrawalsHelpersCapella;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class WithdrawalsHelpersElectra extends WithdrawalsHelpersCapella {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfigElectra specConfigElectra;

  public WithdrawalsHelpersElectra(
      final SchemaDefinitionsElectra schemaDefinitions,
      final MiscHelpersElectra miscHelpers,
      final SpecConfigElectra specConfig,
      final PredicatesElectra predicates,
      final BeaconStateMutatorsElectra beaconStateMutators) {
    super(schemaDefinitions, miscHelpers, specConfig, predicates, beaconStateMutators);
    this.specConfigElectra = specConfig;
  }

  @Override
  protected int consumePendingPartialWithdrawals(
      final List<Withdrawal> withdrawals, final BeaconState state) {
    final BeaconStateElectra stateElectra = BeaconStateElectra.required(state);
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final int maxPendingPartialWithdrawals =
        specConfigElectra.getMaxPendingPartialsPerWithdrawalsSweep();
    UInt64 withdrawalIndex = stateElectra.getNextWithdrawalIndex();
    int processedPartialWithdrawalsCount = 0;

    for (PendingPartialWithdrawal withdrawal : stateElectra.getPendingPartialWithdrawals()) {
      if (withdrawal.getWithdrawableEpoch().isGreaterThan(epoch)
          || withdrawals.size() == maxPendingPartialWithdrawals) {
        break;
      }
      final int validatorIndex = withdrawal.getValidatorIndex();
      final Validator validator = state.getValidators().get(validatorIndex);
      final UInt64 partiallyWithdrawnBalance =
          WithdrawalsHelpers.getPartiallyWithdrawnBalance(
              withdrawals, UInt64.valueOf(validatorIndex));
      final UInt64 remainingBalance =
          state
              .getBalances()
              .get(withdrawal.getValidatorIndex())
              .get()
              .minusMinZero(partiallyWithdrawnBalance);
      final boolean hasSufficientEffectiveBalance =
          validator
              .getEffectiveBalance()
              .isGreaterThanOrEqualTo(specConfigElectra.getMinActivationBalance());
      final boolean hasExcessBalance =
          remainingBalance.isGreaterThan(specConfigElectra.getMinActivationBalance());
      LOG.trace(
          "pending withdrawal validator index {}, remaining balance {}, requested amount {}; exitEpoch {}, hasSufficientEffectiveBalance {}, hasExcessBalance {}",
          withdrawal.getValidatorIndex(),
          remainingBalance,
          withdrawal.getAmount(),
          validator.getExitEpoch(),
          hasSufficientEffectiveBalance,
          hasExcessBalance);

      if (validator.getExitEpoch().equals(FAR_FUTURE_EPOCH)
          && hasSufficientEffectiveBalance
          && hasExcessBalance) {
        final UInt64 withdrawableBalance =
            withdrawal
                .getAmount()
                .min(remainingBalance.minusMinZero(specConfigElectra.getMinActivationBalance()));
        withdrawals.add(
            schemaDefinitions
                .getWithdrawalSchema()
                .create(
                    withdrawalIndex,
                    UInt64.valueOf(withdrawal.getValidatorIndex()),
                    WithdrawalsHelpers.getEthAddressFromWithdrawalCredentials(validator),
                    withdrawableBalance));
        withdrawalIndex = withdrawalIndex.increment();
      }
      processedPartialWithdrawalsCount++;
    }
    return processedPartialWithdrawalsCount;
  }

  @Override
  protected void updatePendingPartialWithdrawals(
      final MutableBeaconState state, final int processedPartialWithdrawalsCount) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final SszListSchema<PendingPartialWithdrawal, ?> schema =
        stateElectra.getPendingPartialWithdrawals().getSchema();
    if (stateElectra.getPendingPartialWithdrawals().size() == processedPartialWithdrawalsCount) {
      stateElectra.setPendingPartialWithdrawals(schema.createFromElements(List.of()));
    } else {
      final List<PendingPartialWithdrawal> pendingPartialWithdrawals =
          stateElectra.getPendingPartialWithdrawals().asList();
      stateElectra.setPendingPartialWithdrawals(
          schema.createFromElements(
              pendingPartialWithdrawals.subList(
                  processedPartialWithdrawalsCount, pendingPartialWithdrawals.size())));
    }
  }
}
