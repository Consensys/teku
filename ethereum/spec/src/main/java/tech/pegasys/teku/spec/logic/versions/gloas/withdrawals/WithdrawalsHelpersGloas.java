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

package tech.pegasys.teku.spec.logic.versions.gloas.withdrawals;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.withdrawals.WithdrawalsHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class WithdrawalsHelpersGloas extends WithdrawalsHelpersElectra {

  private final PredicatesGloas predicatesGloas;
  private final SchemaDefinitionsGloas schemaDefinitionsGloas;

  public WithdrawalsHelpersGloas(
      final SchemaDefinitionsGloas schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final BeaconStateMutatorsElectra beaconStateMutators) {
    super(schemaDefinitions, miscHelpers, specConfig, predicates, beaconStateMutators);
    this.predicatesGloas = predicates;
    this.schemaDefinitionsGloas = schemaDefinitions;
  }

  @Override
  public void processWithdrawals(final MutableBeaconState state) {
    // Return early if the parent block is empty
    if (!predicatesGloas.isParentBlockFull(state)) {
      return;
    }
    super.processWithdrawals(state);
  }

  @Override
  protected int processBuilderWithdrawals(
      final BeaconState state, final List<Withdrawal> withdrawals) {
    UInt64 withdrawalIndex = getNextWithdrawalIndex(state, withdrawals);

    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());

    int processedBuilderWithdrawalsCount = 0;

    for (BuilderPendingWithdrawal withdrawal :
        BeaconStateGloas.required(state).getBuilderPendingWithdrawals()) {
      if (withdrawal.getWithdrawableEpoch().isGreaterThan(epoch)
          || withdrawals.size() + 1 == specConfig.getMaxWithdrawalsPerPayload()) {
        break;
      }
      if (isBuilderPaymentWithdrawable(state, withdrawal)) {
        final UInt64 builderIndex = withdrawal.getBuilderIndex();
        final Validator builder = state.getValidators().get(builderIndex.intValue());
        final UInt64 withdrawn = WithdrawalsHelpers.getWithdrawnAmount(withdrawals, builderIndex);
        final UInt64 remainingBalance =
            state.getBalances().get(builderIndex.intValue()).get().minusMinZero(withdrawn);
        final UInt64 withdrawableBalance;
        if (builder.isSlashed()) {
          withdrawableBalance = remainingBalance.min(withdrawal.getAmount());
        } else if (remainingBalance.isGreaterThan(specConfigElectra.getMinActivationBalance())) {
          withdrawableBalance =
              remainingBalance
                  .minusMinZero(specConfigElectra.getMinActivationBalance())
                  .min(withdrawal.getAmount());
        } else {
          withdrawableBalance = UInt64.ZERO;
        }
        if (withdrawableBalance.isGreaterThan(UInt64.ZERO)) {
          withdrawals.add(
              schemaDefinitions
                  .getWithdrawalSchema()
                  .create(
                      withdrawalIndex,
                      builderIndex,
                      withdrawal.getFeeRecipient(),
                      withdrawableBalance));
          withdrawalIndex = withdrawalIndex.increment();
        }
      }
      processedBuilderWithdrawalsCount++;
    }

    return processedBuilderWithdrawalsCount;
  }

  @Override
  protected int getBoundForPendingPartialWithdrawals(final List<Withdrawal> withdrawals) {
    return Math.min(
        withdrawals.size() + specConfigElectra.getMaxPendingPartialsPerWithdrawalsSweep(),
        specConfig.getMaxWithdrawalsPerPayload() - 1);
  }

  @Override
  protected void updatePayloadExpectedWithdrawals(
      final MutableBeaconState state, final List<Withdrawal> withdrawals) {
    MutableBeaconStateGloas.required(state)
        .setPayloadExpectedWithdrawals(
            schemaDefinitionsGloas
                .getExecutionPayloadSchema()
                .getWithdrawalsSchemaRequired()
                .createFromElements(withdrawals));
  }

  @Override
  protected void updateBuilderPendingWithdrawals(
      final MutableBeaconState state, final int processedBuilderWithdrawalsCount) {
    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
    final SszListSchema<BuilderPendingWithdrawal, ?> schema =
        stateGloas.getBuilderPendingWithdrawals().getSchema();
    final List<BuilderPendingWithdrawal> builderPendingWithdrawals =
        stateGloas.getBuilderPendingWithdrawals().asList();
    if (stateGloas.getBuilderPendingWithdrawals().size() == processedBuilderWithdrawalsCount) {
      final List<BuilderPendingWithdrawal> updatedBuilderPendingWithdrawals =
          builderPendingWithdrawals.stream()
              .filter(withdrawal -> !isBuilderPaymentWithdrawable(state, withdrawal))
              .toList();
      stateGloas.setBuilderPendingWithdrawals(
          schema.createFromElements(updatedBuilderPendingWithdrawals));
    } else {
      final List<BuilderPendingWithdrawal> updatedBuilderPendingWithdrawals =
          new ArrayList<>(
              builderPendingWithdrawals.subList(0, processedBuilderWithdrawalsCount).stream()
                  .filter(withdrawal -> !isBuilderPaymentWithdrawable(state, withdrawal))
                  .toList());
      updatedBuilderPendingWithdrawals.addAll(
          builderPendingWithdrawals.subList(
              processedBuilderWithdrawalsCount, builderPendingWithdrawals.size()));
      stateGloas.setBuilderPendingWithdrawals(
          schema.createFromElements(updatedBuilderPendingWithdrawals));
    }
  }

  private boolean isBuilderPaymentWithdrawable(
      final BeaconState state, final BuilderPendingWithdrawal withdrawal) {
    final Validator builder = state.getValidators().get(withdrawal.getBuilderIndex().intValue());
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    return currentEpoch.isGreaterThanOrEqualTo(builder.getWithdrawableEpoch())
        || !builder.isSlashed();
  }
}
