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
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositReceipt;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionLayerExit;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
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

    final BeaconState state =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processDepositReceipts(
                            MutableBeaconStateElectra.required(mutableState),
                            depositReceiptsSchema.createFromElements(depositReceipts))));

    // verify deposit_receipts_start_index has been set
    assertThat(BeaconStateElectra.required(state).getDepositReceiptsStartIndex())
        .isEqualTo(UInt64.valueOf(firstElectraDepositReceiptIndex));
    // verify validators have been added to the state
    assertThat(state.getValidators().size())
        .isEqualTo(firstElectraDepositReceiptIndex + depositReceiptsCount);
  }

  @Test
  public void processExecutionLayerExits_WithEmptyExitsList_DoesNothing()
      throws BlockProcessingException {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());

    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithExits(preState.getSlot(), List.of());

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerExits_ExitForAbsentValidator_DoesNothing()
      throws BlockProcessingException {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());
    final ExecutionLayerExit executionLayerExit = dataStructureUtil.randomExecutionLayerExit();
    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithExits(preState.getSlot(), List.of(executionLayerExit));

    // Assert the exit does not correspond to an existing validator
    assertThat(
            preState.getValidators().stream()
                .filter(
                    validator ->
                        validator
                            .getPublicKey()
                            .equals(executionLayerExit.getValidatorPublicKey())))
        .isEmpty();

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerExits_ExitForValidatorWithoutEth1Credentials_DoesNothing()
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
        createExecutionPayloadWithExits(
            preState.getSlot(), List.of(dataStructureUtil.executionLayerExit(validator)));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerExits_ExitWithWrongSourceAddress_DoesNothing()
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

    final ExecutionLayerExit exitWithInvalidSourceAddress =
        dataStructureUtil.executionLayerExit(
            dataStructureUtil.randomEth1Address(), validator.getPublicKey());
    final Optional<ExecutionPayload> executionPayloadWithExits =
        createExecutionPayloadWithExits(preState.getSlot(), List.of(exitWithInvalidSourceAddress));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerExits_ExitForInactiveValidator_DoesNothing()
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
        createExecutionPayloadWithExits(
            preState.getSlot(), List.of(dataStructureUtil.executionLayerExit(validator)));
    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerExits_ExitForValidatorAlreadyExiting_DoesNothing()
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
        createExecutionPayloadWithExits(
            preState.getSlot(), List.of(dataStructureUtil.executionLayerExit(validator)));
    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerExits_ExitForValidatorNotActiveLongEnough_DoesNothing()
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
        createExecutionPayloadWithExits(
            preState.getSlot(), List.of(dataStructureUtil.executionLayerExit(validator)));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processExecutionLayerExits_ExitForEligibleValidator()
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
        createExecutionPayloadWithExits(
            preState.getSlot(), List.of(dataStructureUtil.executionLayerExit(validator)));

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processExecutionLayerExits(
                            mutableState,
                            executionPayloadWithExits,
                            validatorExitContextSupplier(preState))));

    // After processing the exit, the validator has exit_epoch and withdrawable_epoch set
    assertThat(postState.getValidators().get(validatorIndex).getExitEpoch())
        .isLessThan(FAR_FUTURE_EPOCH);
    assertThat(postState.getValidators().get(validatorIndex).getWithdrawableEpoch())
        .isLessThan(FAR_FUTURE_EPOCH);
  }

  private Supplier<ValidatorExitContext> validatorExitContextSupplier(final BeaconState state) {
    return spec.getGenesisSpec().beaconStateMutators().createValidatorExitContextSupplier(state);
  }

  private Optional<ExecutionPayload> createExecutionPayloadWithExits(
      final UInt64 slot, final List<ExecutionLayerExit> exits) {
    return Optional.of(
        dataStructureUtil.randomExecutionPayload(slot, builder -> builder.exits(() -> exits)));
  }

  private BlockProcessorElectra getBlockProcessor(final BeaconState state) {
    return (BlockProcessorElectra) spec.getBlockProcessor(state.getSlot());
  }
}
