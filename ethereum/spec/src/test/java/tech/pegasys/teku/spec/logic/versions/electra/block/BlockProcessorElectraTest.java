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

package tech.pegasys.teku.spec.logic.versions.electra.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigElectra.FULL_EXIT_REQUEST_AMOUNT;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerWithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDenebTest;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

class BlockProcessorElectraTest extends BlockProcessorDenebTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetElectra();
  }

  @Test
  public void verifiesOutstandingEth1DepositsAreProcessed() {
    final BeaconState state =
        createBeaconState()
            .updated(
                mutableState -> {
                  final UInt64 eth1DepositCount = UInt64.valueOf(25);
                  mutableState.setEth1Data(
                      new Eth1Data(
                          dataStructureUtil.randomBytes32(),
                          eth1DepositCount,
                          dataStructureUtil.randomBytes32()));
                  final UInt64 eth1DepositIndex = UInt64.valueOf(13);
                  mutableState.setEth1DepositIndex(eth1DepositIndex);
                  final UInt64 depositReceiptsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositReceiptsStartIndex(depositReceiptsStartIndex);
                });

    final BeaconBlockBody body =
        dataStructureUtil.randomBeaconBlockBody(
            // 20 - 13 = 7
            builder -> builder.deposits(dataStructureUtil.randomSszDeposits(7)));

    getBlockProcessor(state).verifyOutstandingDepositsAreProcessed(state, body);
  }

  @Test
  public void
      verifiesNoOutstandingEth1DepositsAreProcessedWhenFormerDepositMechanismHasBeenDisabled() {
    final BeaconState state =
        createBeaconState()
            .updated(
                mutableState -> {
                  final UInt64 eth1DepositCount = UInt64.valueOf(25);
                  mutableState.setEth1Data(
                      new Eth1Data(
                          dataStructureUtil.randomBytes32(),
                          eth1DepositCount,
                          dataStructureUtil.randomBytes32()));
                  final UInt64 eth1DepositIndex = UInt64.valueOf(20);
                  mutableState.setEth1DepositIndex(eth1DepositIndex);
                  final UInt64 depositReceiptsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositReceiptsStartIndex(depositReceiptsStartIndex);
                });

    final BeaconBlockBody body =
        dataStructureUtil.randomBeaconBlockBody(
            // 20 - 20 = 0
            modifier -> modifier.deposits(dataStructureUtil.randomSszDeposits(0)));

    getBlockProcessor(state).verifyOutstandingDepositsAreProcessed(state, body);
  }

  @Test
  public void processesDepositReceipts() throws BlockProcessingException {
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState ->
                        MutableBeaconStateElectra.required(mutableState)
                            .setDepositReceiptsStartIndex(
                                SpecConfigElectra.UNSET_DEPOSIT_RECEIPTS_START_INDEX)));
    final int firstElectraDepositReceiptIndex = preState.getValidators().size();

    final SszListSchema<DepositReceipt, ? extends SszList<DepositReceipt>> depositReceiptsSchema =
        SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
            .getExecutionPayloadSchema()
            .getDepositReceiptsSchemaRequired();
    final int depositReceiptsCount = 3;
    final List<DepositReceipt> depositReceipts =
        IntStream.range(0, depositReceiptsCount)
            .mapToObj(
                i ->
                    dataStructureUtil.randomDepositReceiptWithValidSignature(
                        UInt64.valueOf(firstElectraDepositReceiptIndex + i)))
            .toList();

    final BeaconStateElectra state =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processDepositReceipts(
                            MutableBeaconStateElectra.required(mutableState),
                            depositReceiptsSchema.createFromElements(depositReceipts))));

    // verify deposit_receipts_start_index has been set
    assertThat(state.getDepositReceiptsStartIndex())
        .isEqualTo(UInt64.valueOf(firstElectraDepositReceiptIndex));
    // verify validators have been added to the state
    assertThat(state.getValidators().size())
        .isEqualTo(firstElectraDepositReceiptIndex + depositReceiptsCount);
  }

  @Test
  public void processExecutionLayerWithdrawalRequests_WithEmptyWithdrawalRequestsList_DoesNothing()
      throws BlockProcessingException {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());

    final Optional<ExecutionPayload> executionPayloadWithWithdrawalRequests =
        createExecutionPayloadWithWithdrawalRequests(preState.getSlot(), List.of());

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithWithdrawalRequests,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerWithdrawalRequests_ExitForAbsentValidator_DoesNothing()
      throws BlockProcessingException {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());
    final ExecutionLayerWithdrawalRequest executionLayerWithdrawalRequest =
        dataStructureUtil.randomExecutionLayerWithdrawalRequest();
    final Optional<ExecutionPayload> executionPayloadWithWithdrawalRequests =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(), List.of(executionLayerWithdrawalRequest));

    // Assert the request does not correspond to an existing validator
    assertThat(
            preState.getValidators().stream()
                .filter(
                    validator ->
                        validator
                            .getPublicKey()
                            .equals(executionLayerWithdrawalRequest.getValidatorPublicKey())))
        .isEmpty();

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithWithdrawalRequests,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void
      processExecutionLayerWithdrawalRequests_WithdrawalRequestForValidatorWithoutEth1Credentials_DoesNothing()
          throws BlockProcessingException {
    final Validator validator =
        dataStructureUtil.validatorBuilder().withRandomBlsWithdrawalCredentials().build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                    }));

    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(),
            List.of(dataStructureUtil.executionLayerWithdrawalRequest(validator)));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void
      processExecutionLayerWithdrawalRequests_WithdrawalRequestWithWrongSourceAddress_DoesNothing()
          throws BlockProcessingException {
    final Validator validator =
        dataStructureUtil.validatorBuilder().withRandomEth1WithdrawalCredentials().build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                    }));

    final ExecutionLayerWithdrawalRequest withdrawalRequestWithInvalidSourceAddress =
        dataStructureUtil.executionLayerWithdrawalRequest(
            dataStructureUtil.randomEth1Address(), validator.getPublicKey(), UInt64.ZERO);
    final Optional<ExecutionPayload> executionPayloadWithWithdrawalRequests =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(), List.of(withdrawalRequestWithInvalidSourceAddress));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithWithdrawalRequests,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void
      processExecutionLayerWithdrawalRequests_WithdrawalRequestForInactiveValidator_DoesNothing()
          throws BlockProcessingException {
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(FAR_FUTURE_EPOCH)
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                    }));

    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(),
            List.of(dataStructureUtil.executionLayerWithdrawalRequest(validator)));
    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void
      processExecutionLayerWithdrawalRequests_WithdrawalRequestForValidatorAlreadyExiting_DoesNothing()
          throws BlockProcessingException {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(UInt64.valueOf(1_001))
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));
                    }));

    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(),
            List.of(dataStructureUtil.executionLayerWithdrawalRequest(validator)));
    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void
      processExecutionLayerWithdrawalRequests_ExitForValidatorNotActiveLongEnough_DoesNothing()
          throws BlockProcessingException {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(UInt64.valueOf(999))
            .exitEpoch(FAR_FUTURE_EPOCH)
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));
                    }));

    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(),
            List.of(dataStructureUtil.executionLayerWithdrawalRequest(validator)));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerWithdrawalRequests_ExitForEligibleValidator()
      throws BlockProcessingException {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomEth1WithdrawalCredentials()
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(FAR_FUTURE_EPOCH)
            .build();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));
                    }));
    // The validator we created was the last one added to the list of validators
    int validatorIndex = preState.getValidators().size() - 1;

    // Before processing the exit, the validator has FAR_FUTURE_EPOCH for exit_epoch and
    // withdrawable_epoch
    assertThat(preState.getValidators().get(validatorIndex).getExitEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(preState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);

    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(),
            List.of(
                dataStructureUtil.executionLayerWithdrawalRequest(
                    validator, FULL_EXIT_REQUEST_AMOUNT)));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    // After processing the exit, the validator has exit_epoch and withdrawable_epoch set
    assertThat(postState.getValidators().get(validatorIndex).getExitEpoch())
        .isLessThan(FAR_FUTURE_EPOCH);
    assertThat(postState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isLessThan(FAR_FUTURE_EPOCH);
  }

  @Override
  @Test
  public void processDepositTopsUpValidatorBalanceWhenPubkeyIsFoundInRegistry()
      throws BlockProcessingException {
    // Create a deposit
    final Bytes32 withdrawalCredentials =
        dataStructureUtil.randomCompoundingWithdrawalCredentials();
    DepositData depositInput = dataStructureUtil.randomDepositData(withdrawalCredentials);
    BLSPublicKey pubkey = depositInput.getPubkey();
    UInt64 amount = depositInput.getAmount();

    Validator knownValidator = makeValidator(pubkey, withdrawalCredentials);

    BeaconState preState = createBeaconState(amount, knownValidator);

    int originalValidatorRegistrySize = preState.getValidators().size();
    int originalValidatorBalancesSize = preState.getBalances().size();

    BeaconState postState = processDepositHelper(preState, depositInput);

    assertEquals(
        postState.getValidators().size(),
        originalValidatorRegistrySize,
        "A new validator was added to the validator registry, but should not have been.");
    assertEquals(
        postState.getBalances().size(),
        originalValidatorBalancesSize,
        "A new balance was added to the validator balances, but should not have been.");
    assertEquals(knownValidator, postState.getValidators().get(originalValidatorRegistrySize - 1));
    // as of electra, the balance increase is in the queue, and yet to be applied to the validator.
    assertEquals(amount, postState.getBalances().getElement(originalValidatorBalancesSize - 1));
    assertThat(BeaconStateElectra.required(postState).getPendingBalanceDeposits().get(0).getIndex())
        .isEqualTo(originalValidatorBalancesSize - 1);
    assertThat(
            BeaconStateElectra.required(postState).getPendingBalanceDeposits().get(0).getAmount())
        .isEqualTo(UInt64.THIRTY_TWO_ETH);
  }

  @Test
  public void processExecutionLayerWithdrawalRequests_PartialWithdrawalRequestForEligibleValidator()
      throws BlockProcessingException {
    final UInt64 currentEpoch = UInt64.valueOf(1_000);
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withRandomCompoundingWithdrawalCredentials()
            .activationEpoch(UInt64.ZERO)
            .exitEpoch(FAR_FUTURE_EPOCH)
            .build();

    final SpecConfigElectra specConfigElectra =
        spec.getSpecConfig(currentEpoch).toVersionElectra().orElseThrow();

    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState -> {
                      final SszMutableList<Validator> validators = mutableState.getValidators();
                      validators.append(validator);
                      mutableState.setValidators(validators);
                      mutableState.setSlot(spec.computeStartSlotAtEpoch(currentEpoch));

                      // Give the validator an excessBalance
                      int validatorIndex = mutableState.getValidators().size() - 1;
                      mutableState
                          .getBalances()
                          .set(
                              validatorIndex,
                              SszUInt64.of(
                                  specConfigElectra
                                      .getMinActivationBalance()
                                      .plus(UInt64.valueOf(123_456_789))));
                    }));
    // The validator we created was the last one added to the list of validators
    int validatorIndex = preState.getValidators().size() - 1;

    // Before processing the withdrawal, the validator has FAR_FUTURE_EPOCH for exit_epoch and
    // withdrawable_epoch
    assertThat(preState.getValidators().get(validatorIndex).getExitEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(preState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);

    // Set withdrawal request amount to the validator's excess balance
    final UInt64 amount =
        preState
            .getBalances()
            .get(validatorIndex)
            .get()
            .minus(specConfigElectra.getMinActivationBalance());
    assertThat(amount).isEqualTo(UInt64.valueOf(123_456_789));

    final Optional<ExecutionPayload> processExecutionLayerWithdrawalRequests =
        createExecutionPayloadWithWithdrawalRequests(
            preState.getSlot(),
            List.of(dataStructureUtil.executionLayerWithdrawalRequest(validator, amount)));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerWithdrawalRequests(
                            mutableState,
                            processExecutionLayerWithdrawalRequests,
                            validatorExitContextSupplier(preState))));

    // After processing the request, the validator hasn't updated these
    assertThat(postState.getValidators().get(validatorIndex).getExitEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(postState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isEqualTo(FAR_FUTURE_EPOCH);

    // The validator's balance should be equal to the minimum activation balance
    assertThat(postState.getPendingPartialWithdrawals().size()).isEqualTo(1);
    final PendingPartialWithdrawal mostRecentPendingPartialWithdrawal =
        postState
            .getPendingPartialWithdrawals()
            .get(postState.getPendingPartialWithdrawals().size() - 1);

    assertThat(mostRecentPendingPartialWithdrawal.getIndex()).isEqualTo(validatorIndex);
    assertThat(mostRecentPendingPartialWithdrawal.getAmount())
        .isEqualTo(UInt64.valueOf(123_456_789));
    assertThat(mostRecentPendingPartialWithdrawal.getWithdrawableEpoch())
        .isEqualTo(UInt64.valueOf(1_261));
  }

  private Supplier<ValidatorExitContext> validatorExitContextSupplier(final BeaconState state) {
    return spec.getGenesisSpec().beaconStateMutators().createValidatorExitContextSupplier(state);
  }

  private Optional<ExecutionPayload> createExecutionPayloadWithWithdrawalRequests(
      final UInt64 slot, final List<ExecutionLayerWithdrawalRequest> withdrawalRequests) {
    return Optional.of(
        dataStructureUtil.randomExecutionPayload(
            slot, builder -> builder.withdrawalRequests(() -> withdrawalRequests)));
  }

  private BlockProcessorElectra getBlockProcessor(final BeaconState state) {
    return (BlockProcessorElectra) spec.getBlockProcessor(state.getSlot());
  }
}
