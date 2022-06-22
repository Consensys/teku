/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistration;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class ValidatorRegistratorTest {

  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private final ProposerConfigProvider proposerConfigProvider = mock(ProposerConfigProvider.class);
  private final ProposerConfig proposerConfig = mock(ProposerConfig.class);
  private final ValidatorConfig validatorConfig = mock(ValidatorConfig.class);
  private final FeeRecipientProvider feeRecipientProvider = mock(FeeRecipientProvider.class);
  private final ValidatorRegistrationBatchSender validatorRegistrationBatchSender =
      mock(ValidatorRegistrationBatchSender.class);
  private final TimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(12);
  private final Signer signer = mock(Signer.class);

  private DataStructureUtil dataStructureUtil;
  private int slotsPerEpoch;

  private Validator validator1;
  private Validator validator2;
  private Validator validator3;

  private Eth1Address eth1Address;
  private UInt64 gasLimit;

  private ValidatorRegistrator validatorRegistrator;

  @BeforeEach
  void setUp(SpecContext specContext) {
    slotsPerEpoch = specContext.getSpec().getGenesisSpecConfig().getSlotsPerEpoch();
    dataStructureUtil = specContext.getDataStructureUtil();
    validator1 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator2 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator3 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);

    eth1Address = dataStructureUtil.randomEth1Address();
    gasLimit = dataStructureUtil.randomUInt64();

    validatorRegistrator =
        new ValidatorRegistrator(
            specContext.getSpec(),
            stubTimeProvider,
            ownedValidators,
            proposerConfigProvider,
            validatorConfig,
            feeRecipientProvider,
            validatorRegistrationBatchSender);
    when(validatorRegistrationBatchSender.sendInBatches(any()))
        .thenReturn(SafeFuture.completedFuture(null));

    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerConfig)));

    when(proposerConfig.isValidatorRegistrationEnabledForPubKey(any()))
        .thenReturn(Optional.of(true));
    when(proposerConfig.getValidatorRegistrationGasLimitForPubKey(any()))
        .thenReturn(Optional.of(gasLimit));

    when(feeRecipientProvider.isReadyToProvideFeeRecipient()).thenReturn(true);
    when(feeRecipientProvider.getFeeRecipient(any())).thenReturn(Optional.of(eth1Address));

    // random signature for all signings
    doAnswer(invocation -> SafeFuture.completedFuture(dataStructureUtil.randomSignature()))
        .when(signer)
        .signValidatorRegistration(any(ValidatorRegistration.class), any(UInt64.class));
  }

  @TestTemplate
  void doesNotRegisterValidators_ifNotReady() {
    when(feeRecipientProvider.isReadyToProvideFeeRecipient()).thenReturn(false);

    runRegistrationFlowForSlot(UInt64.ONE);

    verifyNoInteractions(ownedValidators, validatorRegistrationBatchSender, signer);
  }

  @TestTemplate
  void doesNotRegisterValidators_ifNotBeginningOfEpoch() {
    setActiveValidators(validator1, validator2, validator3);

    // initially validators will be registered since it's the first call
    runRegistrationFlowForSlot(UInt64.ZERO);

    verify(validatorRegistrationBatchSender).sendInBatches(any());

    // after the initial call, registration should not occur if not beginning of epoch
    runRegistrationFlowForSlot(UInt64.valueOf(slotsPerEpoch).plus(UInt64.ONE));

    verifyNoMoreInteractions(validatorRegistrationBatchSender);
  }

  @TestTemplate
  void registersValidators_onBeginningOfEpoch() {
    setActiveValidators(validator1, validator2, validator3);

    runRegistrationFlowForSlot(UInt64.ZERO);
    runRegistrationFlowForSlot(UInt64.valueOf(slotsPerEpoch));

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    registrationCalls.forEach(
        registrationCall ->
            verifyRegistrations(registrationCall, List.of(validator1, validator2, validator3)));

    // signer will be called in total 3 times, since from the 2nd run the registrations will
    // be cached
    verify(signer, times(3)).signValidatorRegistration(any(), any());
  }

  @TestTemplate
  void cleanupsCache_ifValidatorIsNoLongerActive() {
    setActiveValidators(validator1, validator2, validator3);

    runRegistrationFlowForSlot(UInt64.ZERO);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(3);

    // validator1 not active anymore
    setActiveValidators(validator2, validator3);

    runRegistrationFlowForSlot(UInt64.valueOf(slotsPerEpoch));

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);
  }

  @TestTemplate
  void doesNotUseCache_ifRegistrationsNeedUpdating() {
    setActiveValidators(validator1, validator2, validator3);

    runRegistrationFlowForSlot(UInt64.ZERO);

    final Eth1Address otherEth1Address = dataStructureUtil.randomEth1Address();
    final UInt64 otherGasLimit = dataStructureUtil.randomUInt64();

    // fee recipient changed for validator2
    when(feeRecipientProvider.getFeeRecipient(validator2.getPublicKey()))
        .thenReturn(Optional.of(otherEth1Address));

    // gas limit changed for validator3
    when(proposerConfig.getValidatorRegistrationGasLimitForPubKey(validator3.getPublicKey()))
        .thenReturn(Optional.of(otherGasLimit));

    runRegistrationFlowForSlot(UInt64.valueOf(slotsPerEpoch));

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    // first call should use the default fee recipient and gas limit
    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2, validator3));

    final Consumer<ValidatorRegistration> updatedRegistrationsRequirements =
        (validatorRegistration) -> {
          BLSPublicKey publicKey = validatorRegistration.getPublicKey();
          Eth1Address feeRecipient = validatorRegistration.getFeeRecipient();
          UInt64 gasLimit = validatorRegistration.getGasLimit();
          if (publicKey.equals(validator1.getPublicKey())) {
            assertThat(feeRecipient).isEqualTo(eth1Address);
            assertThat(gasLimit).isEqualTo(gasLimit);
          }
          if (publicKey.equals(validator2.getPublicKey())) {
            assertThat(feeRecipient).isEqualTo(otherEth1Address);
            assertThat(gasLimit).isEqualTo(gasLimit);
          }
          if (publicKey.equals(validator3.getPublicKey())) {
            assertThat(feeRecipient).isEqualTo(eth1Address);
            assertThat(gasLimit).isEqualTo(otherGasLimit);
          }
        };

    // second call should use the changed fee recipient and gas limit
    verifyRegistrations(
        registrationCalls.get(1),
        List.of(validator1, validator2, validator3),
        Optional.of(updatedRegistrationsRequirements));

    // signer will be called in total 5 times
    verify(signer, times(5)).signValidatorRegistration(any(), any());
  }

  @TestTemplate
  void doesNotRegisterNewlyAddedValidators_ifNotReady() {
    when(feeRecipientProvider.isReadyToProvideFeeRecipient()).thenReturn(false);

    validatorRegistrator.onValidatorsAdded();

    verifyNoInteractions(ownedValidators, validatorRegistrationBatchSender, signer);
  }

  @TestTemplate
  void registersNewlyAddedValidators() {
    setActiveValidators(validator1);

    runRegistrationFlowForSlot(UInt64.ZERO);

    // new validators are added
    setActiveValidators(validator1, validator2, validator3);

    validatorRegistrator.onValidatorsAdded();

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    assertThat(registrationCalls).hasSize(2);

    // first call only has validator1
    verifyRegistrations(registrationCalls.get(0), List.of(validator1));

    // second call should have processed validator2 and validator3
    verifyRegistrations(registrationCalls.get(1), List.of(validator2, validator3));
  }

  @TestTemplate
  void skipsValidatorRegistrationIfRegistrationNotEnabled() {
    setActiveValidators(validator1, validator2, validator3);

    // validator registration is disabled for validator2
    when(proposerConfig.isValidatorRegistrationEnabledForPubKey(validator2.getPublicKey()))
        .thenReturn(Optional.of(false));
    // validator registration enabled flag is not present for validator 3, so will fall back to
    // false
    when(proposerConfig.isValidatorRegistrationEnabledForPubKey(validator3.getPublicKey()))
        .thenReturn(Optional.empty());
    when(validatorConfig.isValidatorsRegistrationDefaultEnabled()).thenReturn(false);

    runRegistrationFlowForSlot(UInt64.ZERO);

    final List<SignedValidatorRegistration> registrationCalls = captureRegistrationCall();
    verifyRegistrations(registrationCalls, List.of(validator1));
  }

  @TestTemplate
  void retrievesCorrectGasLimitForValidators() {
    setActiveValidators(validator1, validator2, validator3);

    final UInt64 validator2GasLimit = UInt64.valueOf(28_000_000);
    final UInt64 defaultGasLimit = UInt64.valueOf(27_000_000);

    // validator2 will have custom gas limit
    when(proposerConfig.getValidatorRegistrationGasLimitForPubKey(validator2.getPublicKey()))
        .thenReturn(Optional.of(validator2GasLimit));
    // validator3 gas limit will fall back to a default
    when(proposerConfig.getValidatorRegistrationGasLimitForPubKey(validator3.getPublicKey()))
        .thenReturn(Optional.empty());
    when(validatorConfig.getValidatorsRegistrationDefaultGasLimit()).thenReturn(defaultGasLimit);

    runRegistrationFlowForSlot(UInt64.ZERO);

    final List<SignedValidatorRegistration> registrationCalls = captureRegistrationCall();

    final Consumer<ValidatorRegistration> gasLimitRequirements =
        (validatorRegistration) -> {
          BLSPublicKey publicKey = validatorRegistration.getPublicKey();
          UInt64 gasLimit = validatorRegistration.getGasLimit();
          if (publicKey.equals(validator1.getPublicKey())) {
            assertThat(gasLimit).isEqualTo(gasLimit);
          }
          if (publicKey.equals(validator2.getPublicKey())) {
            assertThat(gasLimit).isEqualTo(validator2GasLimit);
          }
          if (publicKey.equals(validator3.getPublicKey())) {
            assertThat(gasLimit).isEqualTo(defaultGasLimit);
          }
        };

    verifyRegistrations(
        registrationCalls,
        List.of(validator1, validator2, validator3),
        Optional.of(gasLimitRequirements));
  }

  @TestTemplate
  void skipsValidatorRegistrationIfFeeRecipientNotSpecified() {
    setActiveValidators(validator1, validator2);

    // no fee recipient provided for validator2
    when(feeRecipientProvider.getFeeRecipient(validator2.getPublicKey()))
        .thenReturn(Optional.empty());

    runRegistrationFlowForSlot(UInt64.ZERO);

    final List<SignedValidatorRegistration> registrationCalls = captureRegistrationCall();
    verifyRegistrations(registrationCalls, List.of(validator1));
  }

  private void setActiveValidators(final Validator... validators) {
    final List<Validator> validatorsAsList = Arrays.stream(validators).collect(Collectors.toList());
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsAsList);
  }

  private void runRegistrationFlowForSlot(final UInt64 slot) {
    validatorRegistrator.onSlot(slot);
  }

  private List<SignedValidatorRegistration> captureRegistrationCall() {
    return captureRegistrationCalls(1).get(0);
  }

  private List<List<SignedValidatorRegistration>> captureRegistrationCalls(final int times) {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(List.class);

    verify(validatorRegistrationBatchSender, times(times)).sendInBatches(argumentCaptor.capture());

    return argumentCaptor.getAllValues();
  }

  private void verifyRegistrations(
      final List<SignedValidatorRegistration> validatorRegistrations,
      final List<Validator> expectedRegisteredValidators) {
    verifyRegistrations(validatorRegistrations, expectedRegisteredValidators, Optional.empty());
  }

  private void verifyRegistrations(
      final List<SignedValidatorRegistration> validatorRegistrations,
      final List<Validator> expectedRegisteredValidators,
      final Optional<Consumer<ValidatorRegistration>> alternativeRegistrationRequirements) {

    assertThat(validatorRegistrations)
        .hasSize(expectedRegisteredValidators.size())
        .allSatisfy(registration -> assertThat(registration.getSignature().isValid()).isTrue())
        .map(SignedValidatorRegistration::getMessage)
        .allSatisfy(
            registration -> {
              if (alternativeRegistrationRequirements.isPresent()) {
                alternativeRegistrationRequirements.get().accept(registration);
              } else {
                assertThat(registration.getFeeRecipient()).isEqualTo(eth1Address);
                assertThat(registration.getTimestamp()).isEqualTo(UInt64.valueOf(12));
                assertThat(registration.getGasLimit()).isEqualTo(gasLimit);
              }
            })
        .map(ValidatorRegistration::getPublicKey)
        .containsExactlyInAnyOrderElementsOf(
            expectedRegisteredValidators.stream()
                .map(Validator::getPublicKey)
                .collect(Collectors.toList()));
  }
}
