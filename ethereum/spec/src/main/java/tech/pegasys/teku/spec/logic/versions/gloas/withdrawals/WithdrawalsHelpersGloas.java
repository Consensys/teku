/*
 * Copyright Consensys Software Inc., 2026
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

import com.google.common.base.Preconditions;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.withdrawals.WithdrawalsHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class WithdrawalsHelpersGloas extends WithdrawalsHelpersElectra {

  private final MiscHelpersGloas miscHelpersGloas;
  private final PredicatesGloas predicatesGloas;
  private final SchemaDefinitionsGloas schemaDefinitionsGloas;
  private final SpecConfigGloas specConfigGloas;

  public WithdrawalsHelpersGloas(
      final SchemaDefinitionsGloas schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final BeaconStateMutatorsElectra beaconStateMutators) {
    super(schemaDefinitions, miscHelpers, specConfig, predicates, beaconStateMutators);
    this.miscHelpersGloas = miscHelpers;
    this.predicatesGloas = predicates;
    this.schemaDefinitionsGloas = schemaDefinitions;
    this.specConfigGloas = specConfig;
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
    int processedBuilderWithdrawalsCount = 0;

    final int withdrawalsLimit = specConfig.getMaxWithdrawalsPerPayload() - 1;

    Preconditions.checkArgument(withdrawals.size() <= withdrawalsLimit);

    for (BuilderPendingWithdrawal withdrawal :
        BeaconStateGloas.required(state).getBuilderPendingWithdrawals()) {
      if (withdrawals.size() >= withdrawalsLimit) {
        break;
      }
      final UInt64 builderIndex = withdrawal.getBuilderIndex();
      withdrawals.add(
          schemaDefinitions
              .getWithdrawalSchema()
              .create(
                  withdrawalIndex,
                  miscHelpersGloas.convertBuilderIndexToValidatorIndex(builderIndex),
                  withdrawal.getFeeRecipient(),
                  withdrawal.getAmount()));

      withdrawalIndex = withdrawalIndex.increment();
      processedBuilderWithdrawalsCount++;
    }

    return processedBuilderWithdrawalsCount;
  }

  @Override
  protected int processBuildersSweepWithdrawals(
      final BeaconState state, final List<Withdrawal> withdrawals) {
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);
    final WithdrawalSchema withdrawalSchema = schemaDefinitions.getWithdrawalSchema();
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final SszList<Builder> builders = stateGloas.getBuilders();
    final int buildersCount = builders.size();

    UInt64 withdrawalIndex = getNextWithdrawalIndex(state, withdrawals);
    UInt64 builderIndex = stateGloas.getNextWithdrawalBuilderIndex();

    int processedBuildersSweepCount = 0;

    final int withdrawalsLimit = specConfig.getMaxWithdrawalsPerPayload() - 1;

    Preconditions.checkArgument(withdrawals.size() <= withdrawalsLimit);

    final int buildersLimit =
        Math.min(buildersCount, specConfigGloas.getMaxBuildersPerWithdrawalsSweep());

    for (int i = 0; i < buildersLimit; i++) {
      if (withdrawals.size() >= withdrawalsLimit) {
        break;
      }
      final Builder builder = builders.get(builderIndex.intValue());
      if (builder.getWithdrawableEpoch().isLessThanOrEqualTo(epoch)
          && builder.getBalance().isGreaterThan(UInt64.ZERO)) {
        withdrawals.add(
            withdrawalSchema.create(
                withdrawalIndex,
                miscHelpersGloas.convertBuilderIndexToValidatorIndex(builderIndex),
                builder.getExecutionAddress(),
                builder.getBalance()));
        withdrawalIndex = withdrawalIndex.increment();
      }

      builderIndex = builderIndex.plus(1).mod(buildersCount);
      processedBuildersSweepCount++;
    }

    return processedBuildersSweepCount;
  }

  @Override
  protected void applyWithdrawals(
      final MutableBeaconState state, final List<Withdrawal> withdrawals) {
    for (final Withdrawal withdrawal : withdrawals) {
      final UInt64 validatorIndex = withdrawal.getValidatorIndex();
      if (predicatesGloas.isBuilderIndex(validatorIndex)) {
        final UInt64 builderIndex =
            miscHelpersGloas.convertValidatorIndexToBuilderIndex(validatorIndex);
        final SszMutableList<Builder> builders =
            MutableBeaconStateGloas.required(state).getBuilders();
        final Builder builder = builders.get(builderIndex.intValue());
        final UInt64 builderBalance = builder.getBalance();
        builders.set(
            builderIndex.intValue(),
            builder.copyWithNewBalance(builderBalance.minusMinZero(withdrawal.getAmount())));
      } else {
        beaconStateMutators.decreaseBalance(
            state, validatorIndex.intValue(), withdrawal.getAmount());
      }
    }
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
    stateGloas.setBuilderPendingWithdrawals(
        schema.createFromElements(
            builderPendingWithdrawals.subList(
                processedBuilderWithdrawalsCount, builderPendingWithdrawals.size())));
  }

  @Override
  protected void updateNextWithdrawalBuilderIndex(
      final MutableBeaconState state, final int processedBuildersSweepCount) {
    final MutableBeaconStateGloas stateGloas = MutableBeaconStateGloas.required(state);
    if (!stateGloas.getBuilders().isEmpty()) {
      // Update the next builder index to start the next withdrawal sweep
      final UInt64 nextIndex =
          stateGloas.getNextWithdrawalBuilderIndex().plus(processedBuildersSweepCount);
      final UInt64 nextBuilderIndex = nextIndex.mod(stateGloas.getBuilders().size());
      stateGloas.setNextWithdrawalBuilderIndex(nextBuilderIndex);
    }
  }
}
