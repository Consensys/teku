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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.deneb.block.BlockProcessorDenebTest;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
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
                  final UInt64 depositRequestsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositRequestsStartIndex(depositRequestsStartIndex);
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
                  final UInt64 depositRequestsStartIndex = UInt64.valueOf(20);
                  MutableBeaconStateElectra.required(mutableState)
                      .setDepositRequestsStartIndex(depositRequestsStartIndex);
                });

    final BeaconBlockBody body =
        dataStructureUtil.randomBeaconBlockBody(
            // 20 - 20 = 0
            modifier -> modifier.deposits(dataStructureUtil.randomSszDeposits(0)));

    getBlockProcessor(state).verifyOutstandingDepositsAreProcessed(state, body);
  }

  @Test
  public void processesDepositRequests() {
    final BeaconStateElectra preState =
        BeaconStateElectra.required(
            createBeaconState()
                .updated(
                    mutableState ->
                        MutableBeaconStateElectra.required(mutableState)
                            .setDepositRequestsStartIndex(
                                SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX)));
    final int firstElectraDepositRequestIndex = preState.getValidators().size();

    final int depositRequestsCount = 3;
    final List<DepositRequest> depositRequests =
        IntStream.range(0, depositRequestsCount)
            .mapToObj(
                i ->
                    dataStructureUtil.randomDepositRequestWithValidSignature(
                        UInt64.valueOf(firstElectraDepositRequestIndex + i)))
            .toList();

    final BeaconStateElectra state =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processDepositRequests(
                            MutableBeaconStateElectra.required(mutableState), depositRequests)));

    // verify deposit_requests_start_index has been set
    assertThat(state.getDepositRequestsStartIndex())
        .isEqualTo(UInt64.valueOf(firstElectraDepositRequestIndex));
    // verify validators have been added to the state
    assertThat(state.getPendingDeposits()).hasSize(depositRequestsCount);
  }

  @Test
  public void processWithdrawalRequests_WithEmptyWithdrawalRequestsList_DoesNothing() {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState, List.of(), validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_ExitForAbsentValidator_DoesNothing() {
    final BeaconStateElectra preState = BeaconStateElectra.required(createBeaconState());
    final WithdrawalRequest withdrawalRequest = dataStructureUtil.randomWithdrawalRequest();

    // Assert the request does not correspond to an existing validator
    assertThat(
            preState.getValidators().stream()
                .filter(
                    validator ->
                        validator.getPublicKey().equals(withdrawalRequest.getValidatorPubkey())))
        .isEmpty();

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(withdrawalRequest),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void
      processWithdrawalRequests_WithdrawalRequestForValidatorWithoutEth1Credentials_DoesNothing() {
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

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_WithdrawalRequestWithWrongSourceAddress_DoesNothing() {
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

    final WithdrawalRequest withdrawalRequestWithInvalidSourceAddress =
        dataStructureUtil.withdrawalRequest(
            dataStructureUtil.randomEth1Address(), validator.getPublicKey(), UInt64.ZERO);

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(withdrawalRequestWithInvalidSourceAddress),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_WithdrawalRequestForInactiveValidator_DoesNothing() {
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

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_WithdrawalRequestForValidatorAlreadyExiting_DoesNothing() {
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

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_ExitForValidatorNotActiveLongEnough_DoesNothing() {
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

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator)),
                            validatorExitContextSupplier(preState))));

    assertThat(postState.hashTreeRoot()).isEqualTo(preState.hashTreeRoot());
  }

  @Test
  public void processWithdrawalRequests_ExitForEligibleValidator() throws BlockProcessingException {
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

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(
                                dataStructureUtil.withdrawalRequest(
                                    validator, FULL_EXIT_REQUEST_AMOUNT)),
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
    assertThat(BeaconStateElectra.required(postState).getPendingDeposits().get(0).getPublicKey())
        .isEqualTo(postState.getValidators().get(originalValidatorBalancesSize - 1).getPublicKey());
    assertThat(BeaconStateElectra.required(postState).getPendingDeposits().get(0).getAmount())
        .isEqualTo(UInt64.THIRTY_TWO_ETH);
  }

  @Test
  public void processWithdrawalRequests_PartialWithdrawalRequestForEligibleValidator() {
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

    final BeaconStateElectra postState =
        BeaconStateElectra.required(
            preState.updated(
                mutableState ->
                    getBlockProcessor(preState)
                        .processWithdrawalRequests(
                            mutableState,
                            List.of(dataStructureUtil.withdrawalRequest(validator, amount)),
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

    assertThat(mostRecentPendingPartialWithdrawal.getValidatorIndex()).isEqualTo(validatorIndex);
    assertThat(mostRecentPendingPartialWithdrawal.getAmount())
        .isEqualTo(UInt64.valueOf(123_456_789));
    assertThat(mostRecentPendingPartialWithdrawal.getWithdrawableEpoch())
        .isEqualTo(UInt64.valueOf(1_261));
  }

  @Test
  void shouldUseElectraAttestationUtil() {
    assertThat(spec.getGenesisSpec().getAttestationUtil())
        .isInstanceOf(AttestationUtilElectra.class);
  }

  @Test
  public void shouldCreateNewPayloadRequestWithExecutionRequestsHash() throws Exception {
    final BeaconState preState = createBeaconState();
    final BeaconBlockBodyElectra blockBody =
        BeaconBlockBodyElectra.required(dataStructureUtil.randomBeaconBlockBodyWithCommitments(3));
    final MiscHelpers miscHelpers = spec.atSlot(UInt64.ONE).miscHelpers();
    final List<VersionedHash> expectedVersionedHashes =
        blockBody.getOptionalBlobKzgCommitments().orElseThrow().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .map(miscHelpers::kzgCommitmentToVersionedHash)
            .collect(Collectors.toList());
    final List<Bytes> expectedExecutionRequests =
        getExecutionRequestsDataCodec().encode(blockBody.getExecutionRequests());

    final NewPayloadRequest newPayloadRequest =
        spec.getBlockProcessor(UInt64.ONE)
            .computeNewPayloadRequest(preState, blockBody, Optional.empty());

    assertThat(newPayloadRequest.getExecutionPayload())
        .isEqualTo(blockBody.getOptionalExecutionPayload().orElseThrow());
    assertThat(newPayloadRequest.getVersionedHashes()).isPresent();
    assertThat(newPayloadRequest.getVersionedHashes().get())
        .hasSize(3)
        .allSatisfy(
            versionedHash ->
                assertThat(versionedHash.getVersion())
                    .isEqualTo(SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG))
        .hasSameElementsAs(expectedVersionedHashes);
    assertThat(newPayloadRequest.getParentBeaconBlockRoot()).isPresent();
    assertThat(newPayloadRequest.getParentBeaconBlockRoot().get())
        .isEqualTo(preState.getLatestBlockHeader().getParentRoot());
    assertThat(newPayloadRequest.getExecutionRequests()).hasValue(expectedExecutionRequests);
  }

  private Supplier<ValidatorExitContext> validatorExitContextSupplier(final BeaconState state) {
    return spec.getGenesisSpec().beaconStateMutators().createValidatorExitContextSupplier(state);
  }

  private BlockProcessorElectra getBlockProcessor(final BeaconState state) {
    return (BlockProcessorElectra) spec.getBlockProcessor(state.getSlot());
  }

  private ExecutionRequestsDataCodec getExecutionRequestsDataCodec() {
    return new ExecutionRequestsDataCodec(
        SchemaDefinitionsElectra.required(
                spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions())
            .getExecutionRequestsSchema());
  }
}
