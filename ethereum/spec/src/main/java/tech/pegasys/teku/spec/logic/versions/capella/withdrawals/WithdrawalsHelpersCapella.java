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
    final WithdrawalSchema withdrawalSchema = schemaDefinitions.getWithdrawalSchema();
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final SszList<Validator> validators = state.getValidators();
    final SszUInt64List balances = state.getBalances();
    final int validatorCount = validators.size();
    final int maxWithdrawalsPerPayload = specConfig.getMaxWithdrawalsPerPayload();

    final List<Withdrawal> withdrawals = new ArrayList<>();

    final int processedBuilderWithdrawalsCount = sweepForBuilderPayments(withdrawals, state);

    final int processedPartialWithdrawalsCount =
        sweepForPendingPartialWithdrawals(withdrawals, state);

    // Sweep for remaining
    UInt64 withdrawalIndex = getNextWithdrawalIndex(state, withdrawals);
    int validatorIndex =
        BeaconStateCapella.required(state).getNextWithdrawalValidatorIndex().intValue();

    final int bound = Math.min(validatorCount, specConfig.getMaxValidatorsPerWithdrawalSweep());

    for (int i = 0; i < bound; i++) {
      final Validator validator = validators.get(validatorIndex);
      if (predicates.hasExecutionWithdrawalCredential(validator)) {
        final UInt64 partiallyWithdrawnBalance =
            WithdrawalsHelpers.getPartiallyWithdrawnBalance(
                withdrawals, UInt64.valueOf(validatorIndex));
        final UInt64 balance =
            balances.get(validatorIndex).get().minusMinZero(partiallyWithdrawnBalance);

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

        if (withdrawals.size() == maxWithdrawalsPerPayload) {
          break;
        }
      }

      validatorIndex = (validatorIndex + 1) % validatorCount;
    }

    return new ExpectedWithdrawals(
        withdrawals, processedBuilderWithdrawalsCount, processedPartialWithdrawalsCount);
  }

  protected UInt64 getNextWithdrawalIndex(
      final BeaconState state, final List<Withdrawal> withdrawals) {
    return withdrawals.isEmpty()
        ? BeaconStateCapella.required(state).getNextWithdrawalIndex()
        // if we have already done withdrawals sweeping, we get the last withdrawal index in the
        // list and increment it
        : withdrawals.getLast().getIndex().increment();
  }

  // NO-OP
  protected int sweepForBuilderPayments(
      final List<Withdrawal> withdrawals, final BeaconState state) {
    return 0;
  }

  // NO-OP
  protected int sweepForPendingPartialWithdrawals(
      final List<Withdrawal> withdrawals, final BeaconState state) {
    return 0;
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
        expectedWithdrawals.processedBuilderWithdrawalsCount());
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
        expectedWithdrawals.processedBuilderWithdrawalsCount());
  }

  private void processWithdrawalsUnchecked(
      final MutableBeaconState state,
      final List<Withdrawal> expectedWithdrawals,
      final int processedPartialWithdrawalsCount,
      final int processedBuilderWithdrawalsCount) {

    setLatestWithdrawalsRoot(expectedWithdrawals, state);

    for (final Withdrawal withdrawal : expectedWithdrawals) {
      beaconStateMutators.decreaseBalance(
          state, withdrawal.getValidatorIndex().intValue(), withdrawal.getAmount());
    }

    if (processedBuilderWithdrawalsCount > 0) {
      updatePendingBuilderWithdrawals(state, processedBuilderWithdrawalsCount);
    }

    if (processedPartialWithdrawalsCount > 0) {
      updatePendingPartialWithdrawals(state, processedPartialWithdrawalsCount);
    }

    final MutableBeaconStateCapella stateCapella = MutableBeaconStateCapella.required(state);

    final int validatorCount = state.getValidators().size();
    final int maxWithdrawalsPerPayload = specConfig.getMaxWithdrawalsPerPayload();
    final int maxValidatorsPerWithdrawalsSweep = specConfig.getMaxValidatorsPerWithdrawalSweep();
    if (!expectedWithdrawals.isEmpty()) {
      final Withdrawal latestWithdrawal = expectedWithdrawals.getLast();
      stateCapella.setNextWithdrawalIndex(latestWithdrawal.getIndex().increment());
    }

    if (expectedWithdrawals.size() == maxWithdrawalsPerPayload) {
      // Update the next validator index to start the next withdrawal sweep
      final Withdrawal latestWithdrawal = expectedWithdrawals.getLast();
      final int nextWithdrawalValidatorIndex = latestWithdrawal.getValidatorIndex().intValue() + 1;
      stateCapella.setNextWithdrawalValidatorIndex(
          UInt64.valueOf(nextWithdrawalValidatorIndex % validatorCount));
    } else {
      // Advance sweep by the max length of the sweep if there was not a full set of withdrawals
      final int nextWithdrawalValidatorIndex =
          stateCapella.getNextWithdrawalValidatorIndex().intValue()
              + maxValidatorsPerWithdrawalsSweep;
      stateCapella.setNextWithdrawalValidatorIndex(
          UInt64.valueOf(nextWithdrawalValidatorIndex % validatorCount));
    }
  }

  // NO-OP
  protected void setLatestWithdrawalsRoot(
      final List<Withdrawal> expectedWithdrawals, final MutableBeaconState state) {}

  // NO-OP
  protected void updatePendingBuilderWithdrawals(
      final MutableBeaconState state, final int processedBuilderWithdrawalsCount) {}

  // NO-OP
  protected void updatePendingPartialWithdrawals(
      final MutableBeaconState state, final int processedPartialWithdrawalsCount) {}
}
