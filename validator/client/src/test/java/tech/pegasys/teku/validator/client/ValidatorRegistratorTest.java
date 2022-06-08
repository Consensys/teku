/*
 * Copyright 2022 ConsenSys AG.
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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
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
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class ValidatorRegistratorTest {

  private static final UInt64 CUSTOM_GAS_LIMIT = UInt64.valueOf(29_000_000);

  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private final ProposerConfigProvider proposerConfigProvider = mock(ProposerConfigProvider.class);
  private final ProposerConfig proposerConfig = mock(ProposerConfig.class);
  private final ValidatorConfig validatorConfig = mock(ValidatorConfig.class);
  private final FeeRecipientProvider feeRecipientProvider = mock(FeeRecipientProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final TimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(12);
  private final Signer signer = mock(Signer.class);

  private DataStructureUtil dataStructureUtil;
  private int slotsPerEpoch;

  private Validator validator1;
  private Validator validator2;
  private Validator validator3;

  private Eth1Address eth1Address;

  private ValidatorRegistrator validatorRegistrator;

  @BeforeEach
  void setUp(SpecContext specContext) {
    slotsPerEpoch = specContext.getSpec().getGenesisSpecConfig().getSlotsPerEpoch();
    dataStructureUtil = specContext.getDataStructureUtil();
    validator1 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator2 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator3 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    eth1Address = dataStructureUtil.randomEth1Address();
    validatorRegistrator =
        new ValidatorRegistrator(
            specContext.getSpec(),
            stubTimeProvider,
            ownedValidators,
            proposerConfigProvider,
            validatorConfig,
            feeRecipientProvider,
            validatorApiChannel);
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(SafeFuture.completedFuture(null));

    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerConfig)));

    when(proposerConfig.isValidatorRegistrationEnabledForPubKey(any()))
        .thenReturn(Optional.of(true));
    when(proposerConfig.getValidatorRegistrationGasLimitForPubKey(any()))
        .thenReturn(Optional.of(CUSTOM_GAS_LIMIT));

    when(feeRecipientProvider.getFeeRecipient(any(), any())).thenReturn(Optional.of(eth1Address));

    // random signature for all signings
    doAnswer(invocation -> SafeFuture.completedFuture(dataStructureUtil.randomSignature()))
        .when(signer)
        .signValidatorRegistration(any(ValidatorRegistration.class), any(UInt64.class));
  }

  @TestTemplate
  void doesNotRegisterValidators_ifNotBeginningOfEpoch() {
    when(ownedValidators.getActiveValidators()).thenReturn(List.of());

    // initially validators will be registered anyway since it's the first call
    validatorRegistrator.onSlot(UInt64.ONE);
    verify(ownedValidators).getActiveValidators();

    // after the initial call, registration should not occur if not beginning of epoch
    validatorRegistrator.onSlot(UInt64.valueOf(slotsPerEpoch).plus(UInt64.ONE));
    verifyNoMoreInteractions(ownedValidators);

    verifyNoInteractions(validatorApiChannel, signer);
  }

  @TestTemplate
  void registersValidators_onBeginningOfEpoch() {
    // GIVEN
    when(ownedValidators.getActiveValidators())
        .thenReturn(List.of(validator1, validator2, validator3));

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    // WHEN onSlot called again next epoch, registrations will be cached
    validatorRegistrator.onSlot(UInt64.valueOf(slotsPerEpoch));

    // THEN
    List<SszList<SignedValidatorRegistration>> registrationCalls =
        captureValidatorApiChannelCalls(2);

    registrationCalls.forEach(
        registrationCall ->
            verifyRegistrations(
                registrationCall,
                List.of(
                    validator1.getPublicKey(),
                    validator2.getPublicKey(),
                    validator3.getPublicKey())));

    // signer will be called in total 3 times, since from the 2nd run the signed registrations will
    // be cached
    verify(signer, times(3)).signValidatorRegistration(any(), any());
  }

  @TestTemplate
  void registersNewlyAddedValidators() {
    // GIVEN
    when(ownedValidators.getActiveValidators()).thenReturn(List.of(validator1));

    // Registering only validator1
    validatorRegistrator.onSlot(UInt64.ZERO);

    // WHEN new validators are added
    when(ownedValidators.getActiveValidators())
        .thenReturn(List.of(validator1, validator2, validator3));

    // THEN only validator2 and validator3 should be registered
    validatorRegistrator.onValidatorsAdded();

    List<SszList<SignedValidatorRegistration>> registrationCalls =
        captureValidatorApiChannelCalls(2);

    assertThat(registrationCalls).hasSize(2);

    // first call only has validator1
    verifyRegistrations(registrationCalls.get(0), List.of(validator1.getPublicKey()));

    // second call should have processed validator2 and validator3
    verifyRegistrations(
        registrationCalls.get(1), List.of(validator2.getPublicKey(), validator3.getPublicKey()));
  }

  @TestTemplate
  void skipsValidatorRegistrationIfRegistrationNotEnabled() {
    // GIVEN
    when(ownedValidators.getActiveValidators())
        .thenReturn(List.of(validator1, validator2, validator3));

    // validator registration is disabled for validator2
    when(proposerConfig.isValidatorRegistrationEnabledForPubKey(validator2.getPublicKey()))
        .thenReturn(Optional.of(false));
    // validator registration enabled flag is not present for validator 3, so will fall back to
    // false
    when(proposerConfig.isValidatorRegistrationEnabledForPubKey(validator3.getPublicKey()))
        .thenReturn(Optional.empty());
    when(validatorConfig.isValidatorsRegistrationDefaultEnabled()).thenReturn(false);

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    // THEN
    SszList<SignedValidatorRegistration> registrations = captureValidatorApiChannelCall();

    verifyRegistrations(registrations, List.of(validator1.getPublicKey()));
  }

  @TestTemplate
  void validatorRegistrationsNotSentIfEmpty() {
    // GIVEN
    when(ownedValidators.getActiveValidators())
        .thenReturn(List.of(validator1, validator2, validator3));

    // all validator registrations disabled
    when(proposerConfig.isValidatorRegistrationEnabledForPubKey(any()))
        .thenReturn(Optional.of(false));

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    verifyNoInteractions(validatorApiChannel);
  }

  @TestTemplate
  void retrievesCorrectGasLimitForValidators() {
    // GIVEN
    when(ownedValidators.getActiveValidators())
        .thenReturn(List.of(validator1, validator2, validator3));

    UInt64 validator2GasLimit = UInt64.valueOf(28_000_000);
    UInt64 defaultGasLimit = UInt64.valueOf(27_000_000);

    // validator2 will have custom gas limit
    when(proposerConfig.getValidatorRegistrationGasLimitForPubKey(validator2.getPublicKey()))
        .thenReturn(Optional.of(validator2GasLimit));
    // validator3 gas limit will fall back to a default
    when(proposerConfig.getValidatorRegistrationGasLimitForPubKey(validator3.getPublicKey()))
        .thenReturn(Optional.empty());
    when(validatorConfig.getValidatorsRegistrationDefaultGasLimit()).thenReturn(defaultGasLimit);

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    // THEN
    SszList<SignedValidatorRegistration> registrations = captureValidatorApiChannelCall();

    Consumer<ValidatorRegistration> gasLimitRequirements =
        (validatorRegistration) -> {
          BLSPublicKey publicKey = validatorRegistration.getPublicKey();
          UInt64 gasLimit = validatorRegistration.getGasLimit();
          if (publicKey.equals(validator1.getPublicKey())) {
            // validator1 gas limit hasn't been changed
            assertThat(gasLimit).isEqualTo(CUSTOM_GAS_LIMIT);
          }
          if (publicKey.equals(validator2.getPublicKey())) {
            assertThat(gasLimit).isEqualTo(validator2GasLimit);
          }
          if (publicKey.equals(validator3.getPublicKey())) {
            assertThat(gasLimit).isEqualTo(defaultGasLimit);
          }
        };

    verifyRegistrations(
        registrations,
        List.of(validator1.getPublicKey(), validator2.getPublicKey(), validator3.getPublicKey()),
        Optional.of(gasLimitRequirements));
  }

  @TestTemplate
  void skipsValidatorRegistrationIfFeeRecipientNotSpecified() {
    // GIVEN
    when(ownedValidators.getActiveValidators()).thenReturn(List.of(validator1, validator2));

    when(feeRecipientProvider.getFeeRecipient(
            Optional.of(proposerConfig), validator1.getPublicKey()))
        .thenReturn(Optional.of(eth1Address));
    // no fee recipient provided for validator2
    when(feeRecipientProvider.getFeeRecipient(
            Optional.of(proposerConfig), validator2.getPublicKey()))
        .thenReturn(Optional.empty());

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    // THEN
    SszList<SignedValidatorRegistration> registrations = captureValidatorApiChannelCall();

    verifyRegistrations(registrations, List.of(validator1.getPublicKey()));
  }

  private void verifyRegistrations(
      SszList<SignedValidatorRegistration> validatorRegistrations,
      List<BLSPublicKey> expectedValidatorPublicKeys) {
    verifyRegistrations(validatorRegistrations, expectedValidatorPublicKeys, Optional.empty());
  }

  private void verifyRegistrations(
      SszList<SignedValidatorRegistration> validatorRegistrations,
      List<BLSPublicKey> expectedValidatorPublicKeys,
      Optional<Consumer<ValidatorRegistration>> alternativeRegistrationRequirements) {

    assertThat(validatorRegistrations)
        .hasSize(expectedValidatorPublicKeys.size())
        .allSatisfy(registration -> assertThat(registration.getSignature().isValid()).isTrue())
        .map(SignedValidatorRegistration::getMessage)
        .allSatisfy(
            registration -> {
              if (alternativeRegistrationRequirements.isPresent()) {
                alternativeRegistrationRequirements.get().accept(registration);
              } else {
                assertThat(registration.getFeeRecipient()).isEqualTo(eth1Address);
                assertThat(registration.getTimestamp()).isEqualTo(UInt64.valueOf(12));
                assertThat(registration.getGasLimit()).isEqualTo(CUSTOM_GAS_LIMIT);
              }
            })
        .map(ValidatorRegistration::getPublicKey)
        .containsExactlyInAnyOrderElementsOf(expectedValidatorPublicKeys);
  }

  private SszList<SignedValidatorRegistration> captureValidatorApiChannelCall() {
    return captureValidatorApiChannelCalls(1).get(0);
  }

  private List<SszList<SignedValidatorRegistration>> captureValidatorApiChannelCalls(int times) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(validatorApiChannel, times(times)).registerValidators(argumentCaptor.capture());

    return argumentCaptor.getAllValues();
  }
}
