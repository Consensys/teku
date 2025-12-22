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

package tech.pegasys.teku.spec.logic.versions.capella.withdrawals;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BeaconStateMutatorsBellatrix;
import tech.pegasys.teku.spec.logic.versions.capella.helpers.MiscHelpersCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class WithdrawalsHelpersCapella implements WithdrawalsHelpers {

  protected final SchemaDefinitionsCapella schemaDefinitions;
  protected final MiscHelpersCapella miscHelpers;
  protected final SpecConfigCapella specConfig;
  protected final Predicates predicates;
  protected final BeaconStateMutatorsBellatrix beaconStateMutators;

  public WithdrawalsHelpersCapella(
      final SchemaDefinitionsCapella schemaDefinitions,
      final MiscHelpersCapella miscHelpers,
      final SpecConfigCapella specConfig,
      final Predicates predicates,
      final BeaconStateMutatorsBellatrix beaconStateMutators) {
    this.schemaDefinitions = schemaDefinitions;
    this.miscHelpers = miscHelpers;
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.beaconStateMutators = beaconStateMutators;
  }

  @Override
  public ExpectedWithdrawals getExpectedWithdrawals(final BeaconState state) {
    final List<Withdrawal> withdrawals = new ArrayList<>();

    final int processedBuilderWithdrawalsCount = processBuilderWithdrawals(state, withdrawals);

    final int processedPartialWithdrawalsCount =
        processPendingPartialWithdrawals(state, withdrawals);

    final int processedValidatorsSweepCount = processValidatorsSweepWithdrawals(state, withdrawals);

    return new ExpectedWithdrawals(
        withdrawals,
        processedBuilderWithdrawalsCount,
        processedPartialWithdrawalsCount,
        processedValidatorsSweepCount);
  }

  // NO-OP
  protected int processBuilderWithdrawals(
      final BeaconState state, final List<Withdrawal> withdrawals) {
    return 0;
  }

  // NO-OP
  protected int processPendingPartialWithdrawals(
      final BeaconState state, final List<Withdrawal> withdrawals) {
    return 0;
  }

  protected int processValidatorsSweepWithdrawals(
      final BeaconState state, final List<Withdrawal> withdrawals) {
    final WithdrawalSchema withdrawalSchema = schemaDefinitions.getWithdrawalSchema();
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final SszList<Validator> validators = state.getValidators();
    final SszUInt64List balances = state.getBalances();
    final int validatorCount = validators.size();
    final int maxWithdrawalsPerPayload = specConfig.getMaxWithdrawalsPerPayload();

    UInt64 withdrawalIndex = getNextWithdrawalIndex(state, withdrawals);
    int validatorIndex =
        BeaconStateCapella.required(state).getNextWithdrawalValidatorIndex().intValue();

    int processedValidatorsSweepCount = 0;

    final int bound = Math.min(validatorCount, specConfig.getMaxValidatorsPerWithdrawalSweep());

    for (int i = 0; i < bound; i++) {
      if (withdrawals.size() == maxWithdrawalsPerPayload) {
        return processedValidatorsSweepCount;
      }
      final Validator validator = validators.get(validatorIndex);
      final UInt64 withdrawn =
          WithdrawalsHelpers.getWithdrawnAmount(withdrawals, UInt64.valueOf(validatorIndex));
      final UInt64 balance = balances.get(validatorIndex).get().minusMinZero(withdrawn);

      if (predicates.isFullyWithdrawableValidatorCredentialsChecked(validator, balance, epoch)) {
        withdrawals.add(
            withdrawalSchema.create(
                withdrawalIndex,
                UInt64.valueOf(validatorIndex),
                WithdrawalsHelpers.getEthAddressFromWithdrawalCredentials(validator),
                balance));
        withdrawalIndex = withdrawalIndex.increment();
      } else if (predicates.isPartiallyWithdrawableValidatorEth1CredentialsChecked(
          validator, balance)) {
        withdrawals.add(
            withdrawalSchema.create(
                withdrawalIndex,
                UInt64.valueOf(validatorIndex),
                WithdrawalsHelpers.getEthAddressFromWithdrawalCredentials(validator),
                balance.minusMinZero(miscHelpers.getMaxEffectiveBalance(validator))));
        withdrawalIndex = withdrawalIndex.increment();
      }

      validatorIndex = (validatorIndex + 1) % validatorCount;
      processedValidatorsSweepCount++;
    }
    return processedValidatorsSweepCount;
  }

  protected UInt64 getNextWithdrawalIndex(
      final BeaconState state, final List<Withdrawal> withdrawals) {
    return withdrawals.isEmpty()
        ? BeaconStateCapella.required(state).getNextWithdrawalIndex()
        // if we have already done withdrawals sweeping, we get the last withdrawal index in the
        // list and increment it
        : withdrawals.getLast().getIndex().increment();
  }

  @Override
  public void processWithdrawals(
      final MutableBeaconState state, final ExecutionPayloadSummary payload)
      throws BlockProcessingException {
    final ExpectedWithdrawals expectedWithdrawals = getExpectedWithdrawals(state);
    assertWithdrawalsInExecutionPayloadMatchExpected(payload, expectedWithdrawals.withdrawals());
    processWithdrawalsUnchecked(
        state,
        expectedWithdrawals.withdrawals(),
        expectedWithdrawals.processedPartialWithdrawalsCount(),
        expectedWithdrawals.processedBuilderWithdrawalsCount(),
        expectedWithdrawals.processedValidatorsSweepCount());
  }

  private void assertWithdrawalsInExecutionPayloadMatchExpected(
      final ExecutionPayloadSummary payloadSummary, final List<Withdrawal> expectedWithdrawals)
      throws BlockProcessingException {
    // the spec does an element-to-element comparison but Teku is comparing the hash of the tree
    final Bytes32 expectedWithdrawalsRoot =
        schemaDefinitions
            .getExecutionPayloadSchema()
            .getWithdrawalsSchemaRequired()
            .createFromElements(expectedWithdrawals)
            .hashTreeRoot();
    if (payloadSummary.getOptionalWithdrawalsRoot().isEmpty()
        || !expectedWithdrawalsRoot.equals(payloadSummary.getOptionalWithdrawalsRoot().get())) {
      final String msg =
          String.format(
              "Withdrawals in execution payload are different from expected (expected withdrawals root is %s but was "
                  + "%s)",
              expectedWithdrawalsRoot,
              payloadSummary
                  .getOptionalWithdrawalsRoot()
                  .map(Bytes::toHexString)
                  .orElse("MISSING"));
      throw new BlockProcessingException(msg);
    }
  }

  @Override
  public void processWithdrawals(final MutableBeaconState state) {
    final ExpectedWithdrawals expectedWithdrawals = getExpectedWithdrawals(state);
    processWithdrawalsUnchecked(
        state,
        expectedWithdrawals.withdrawals(),
        expectedWithdrawals.processedPartialWithdrawalsCount(),
        expectedWithdrawals.processedBuilderWithdrawalsCount(),
        expectedWithdrawals.processedValidatorsSweepCount());
  }

  private void processWithdrawalsUnchecked(
      final MutableBeaconState state,
      final List<Withdrawal> withdrawals,
      final int processedPartialWithdrawalsCount,
      final int processedBuilderWithdrawalsCount,
      final int processedValidatorsSweepCount) {

    applyWithdrawals(state, withdrawals);

    updateNextWithdrawalIndex(state, withdrawals);

    updatePayloadExpectedWithdrawals(state, withdrawals);

    if (processedBuilderWithdrawalsCount > 0) {
      updateBuilderPendingWithdrawals(state, processedBuilderWithdrawalsCount);
    }

    if (processedPartialWithdrawalsCount > 0) {
      updatePendingPartialWithdrawals(state, processedPartialWithdrawalsCount);
    }

    updateNextWithdrawalValidatorIndex(state, processedValidatorsSweepCount);
  }

  protected void applyWithdrawals(
      final MutableBeaconState state, final List<Withdrawal> withdrawals) {
    for (final Withdrawal withdrawal : withdrawals) {
      beaconStateMutators.decreaseBalance(
          state, withdrawal.getValidatorIndex().intValue(), withdrawal.getAmount());
    }
  }

  protected void updateNextWithdrawalIndex(
      final MutableBeaconState state, final List<Withdrawal> withdrawals) {
    // Update the next withdrawal index if this block contained withdrawals
    if (!withdrawals.isEmpty()) {
      final Withdrawal latestWithdrawal = withdrawals.getLast();
      MutableBeaconStateCapella.required(state)
          .setNextWithdrawalIndex(latestWithdrawal.getIndex().increment());
    }
  }

  // NO-OP
  protected void updatePayloadExpectedWithdrawals(
      final MutableBeaconState state, final List<Withdrawal> withdrawals) {}

  // NO-OP
  protected void updateBuilderPendingWithdrawals(
      final MutableBeaconState state, final int processedBuilderWithdrawalsCount) {}

  // NO-OP
  protected void updatePendingPartialWithdrawals(
      final MutableBeaconState state, final int processedPartialWithdrawalsCount) {}

  protected void updateNextWithdrawalValidatorIndex(
      final MutableBeaconState state, final int processedValidatorsSweepCount) {
    // Update the next validator index to start the next withdrawal sweep
    final MutableBeaconStateCapella stateCapella = MutableBeaconStateCapella.required(state);
    final UInt64 nextIndex =
        stateCapella.getNextWithdrawalValidatorIndex().plus(processedValidatorsSweepCount);
    final UInt64 nextValidatorIndex = nextIndex.mod(state.getValidators().size());
    stateCapella.setNextWithdrawalValidatorIndex(nextValidatorIndex);
  }
}
