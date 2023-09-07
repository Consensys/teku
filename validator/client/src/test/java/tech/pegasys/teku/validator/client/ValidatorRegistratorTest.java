/*
 * Copyright Consensys Software Inc., 2022
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class ValidatorRegistratorTest {

  private static final int BATCH_SIZE = 100;
  private static final UInt64 TIMESTAMP = ONE;

  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);
  private final ValidatorRegistrationSigningService validatorRegistrationSigningService =
      mock(ValidatorRegistrationSigningService.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Signer signer = mock(Signer.class);
  private final ValidatorStatusProvider validatorStatusProvider =
      mock(ValidatorStatusProvider.class);

  private SpecContext specContext;
  private DataStructureUtil dataStructureUtil;
  private int slotsPerEpoch;

  private Validator validator1;
  private Validator validator2;
  private Validator validator3;

  private Eth1Address eth1Address;
  private UInt64 gasLimit;

  private ValidatorRegistrator validatorRegistrator;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp(SpecContext specContext) {
    this.specContext = specContext;
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
            ownedValidators,
            validatorStatusProvider,
            proposerConfigPropertiesProvider,
            validatorRegistrationSigningService,
            validatorApiChannel,
            BATCH_SIZE);

    when(validatorRegistrationSigningService.createSignedValidatorRegistration(any(), any(), any()))
        .thenAnswer(
            args -> {
              final Validator validator = (Validator) args.getArguments()[0];
              return Optional.of(
                  SafeFuture.completedFuture(
                      createSignedValidatorRegistration(validator.getPublicKey())));
            });

    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(true);
    when(proposerConfigPropertiesProvider.refresh()).thenReturn(SafeFuture.COMPLETE);

    // random signature for all signings
    doAnswer(invocation -> SafeFuture.completedFuture(dataStructureUtil.randomSignature()))
        .when(signer)
        .signValidatorRegistration(any(ValidatorRegistration.class));

    // all validators are active
    when(validatorStatusProvider.getStatuses())
        .thenReturn(
            Optional.of(
                Map.of(
                    validator1.getPublicKey(),
                    ValidatorStatus.active_ongoing,
                    validator2.getPublicKey(),
                    ValidatorStatus.active_ongoing,
                    validator3.getPublicKey(),
                    ValidatorStatus.active_ongoing)));

    // registration is successful by default
    when(validatorApiChannel.registerValidators(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @TestTemplate
  void doesNotRegisterValidators_ifNotReady() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(false);

    runRegistrationFlowForEpoch(0);

    verifyNoInteractions(
        ownedValidators, validatorApiChannel, validatorRegistrationSigningService, signer);
  }

  @TestTemplate
  void doesNotRegisterValidators_ifNotThreeSlotsInTheEpoch() {
    setOwnedValidators(validator1, validator2, validator3);

    // initially validators will be registered anyway since it's the first call
    runRegistrationFlowForSlot(ZERO);

    // 2 times: isReady + registration
    verify(validatorStatusProvider, times(2)).getStatuses();
    verify(validatorApiChannel).registerValidators(any());

    // after the initial call, registration should not occur if not three slots in the epoch
    runRegistrationFlowForSlot(
        UInt64.valueOf(slotsPerEpoch).plus(UInt64.valueOf(3))); // fourth slot in the epoch

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @TestTemplate
  void cleanupsCache_ifValidatorIsNoLongerOwned() {
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowForEpoch(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(3);

    // validator1 not active anymore
    setOwnedValidators(validator2, validator3);

    runRegistrationFlowForEpoch(1);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);
  }

  @TestTemplate
  void doesNotRegisterValidatorsOnPossibleMissedEvents_ifNotReady() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(false);

    validatorRegistrator.onPossibleMissedEvents();
    verifyNoInteractions(
        ownedValidators, validatorRegistrationSigningService, validatorApiChannel, signer);
  }

  @TestTemplate
  void registerValidatorsOnPossibleMissedEvents() {
    setOwnedValidators(validator1, validator2, validator3);

    validatorRegistrator.onPossibleMissedEvents();

    final List<SignedValidatorRegistration> registrationCall = captureRegistrationCall();

    verifyRegistrations(registrationCall, List.of(validator1, validator2, validator3));
  }

  @TestTemplate
  void doesNotRegisterNewlyAddedValidators_ifNotReady() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(false);

    validatorRegistrator.onValidatorsAdded();

    verifyNoInteractions(
        ownedValidators, validatorRegistrationSigningService, validatorApiChannel, signer);
  }

  @TestTemplate
  void doesNotRegisterNewlyAddedValidators_ifFirstCallHasNotBeenDone() {
    validatorRegistrator.onValidatorsAdded();

    verifyNoInteractions(
        ownedValidators, validatorRegistrationSigningService, validatorApiChannel, signer);
  }

  @TestTemplate
  void registersNewlyAddedValidators() {
    setOwnedValidators(validator1);

    runRegistrationFlowForEpoch(0);

    // new validators are added
    setOwnedValidators(validator1, validator2, validator3);

    validatorRegistrator.onValidatorsAdded();

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    assertThat(registrationCalls).hasSize(2);

    // first call only has validator1
    verifyRegistrations(registrationCalls.get(0), List.of(validator1));

    // second call should have processed validator2 and validator3
    verifyRegistrations(registrationCalls.get(1), List.of(validator2, validator3));
  }

  @TestTemplate
  void registerValidatorsEvenIfOneRegistrationSigningFails() {
    setOwnedValidators(validator1, validator2, validator3);

    reset(validatorRegistrationSigningService);
    when(validatorRegistrationSigningService.createSignedValidatorRegistration(any(), any(), any()))
        .thenAnswer(
            args -> {
              final Validator validator = (Validator) args.getArguments()[0];
              if (validator.equals(validator2)) {
                return Optional.of(SafeFuture.failedFuture(new IllegalStateException("oopsy")));
              }
              return Optional.of(
                  SafeFuture.completedFuture(
                      createSignedValidatorRegistration(validator.getPublicKey())));
            });
    runRegistrationFlowForEpoch(0);

    reset(validatorRegistrationSigningService);
    when(validatorRegistrationSigningService.createSignedValidatorRegistration(any(), any(), any()))
        .thenAnswer(
            args -> {
              final Validator validator = (Validator) args.getArguments()[0];
              return Optional.of(
                  SafeFuture.completedFuture(
                      createSignedValidatorRegistration(validator.getPublicKey())));
            });
    runRegistrationFlowForEpoch(1);

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator3));
    verifyRegistrations(registrationCalls.get(1), List.of(validator1, validator2, validator3));
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  void doesNotRegister_ifValidatorIsExited() {
    setOwnedValidators(validator1, validator2, validator3);

    // only validator2 is active
    when(validatorStatusProvider.getStatuses())
        .thenReturn(
            Optional.of(
                Map.of(
                    validator1.getPublicKey(),
                    ValidatorStatus.pending_initialized,
                    validator2.getPublicKey(),
                    ValidatorStatus.active_ongoing,
                    validator3.getPublicKey(),
                    ValidatorStatus.exited_unslashed)));

    runRegistrationFlowForEpoch(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);

    // validator1, validator2 are active
    when(validatorStatusProvider.getStatuses())
        .thenReturn(
            Optional.of(
                Map.of(
                    validator1.getPublicKey(),
                    ValidatorStatus.active_ongoing,
                    validator2.getPublicKey(),
                    ValidatorStatus.active_exiting,
                    validator3.getPublicKey(),
                    ValidatorStatus.exited_unslashed)));

    runRegistrationFlowForEpoch(1);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2));
    verifyRegistrations(registrationCalls.get(1), List.of(validator1, validator2));
  }

  @TestTemplate
  void doesNotRegister_ifValidatorStatusUnknown() {
    setOwnedValidators(validator1, validator2, validator3);
    // only validator2 information is available
    when(validatorStatusProvider.getStatuses())
        .thenReturn(Optional.of(Map.of(validator2.getPublicKey(), ValidatorStatus.active_ongoing)));

    runRegistrationFlowForEpoch(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(1);
    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(1);
    verifyRegistrations(registrationCalls.get(0), List.of(validator2));
  }

  @TestTemplate
  void noRegistrationsAreSentIfEmpty() {
    setOwnedValidators();

    runRegistrationFlowForSlot(ZERO);

    verifyNoInteractions(validatorApiChannel, signer, validatorRegistrationSigningService);
  }

  @TestTemplate
  void checksStatusesAndSendsRegistrationsInBatches() {
    validatorRegistrator =
        new ValidatorRegistrator(
            specContext.getSpec(),
            ownedValidators,
            validatorStatusProvider,
            proposerConfigPropertiesProvider,
            validatorRegistrationSigningService,
            validatorApiChannel,
            2);
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowForEpoch(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(3);
    // 2 times: isReady + registration
    verify(validatorStatusProvider, times(2)).getStatuses();

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2));
    verifyRegistrations(registrationCalls.get(1), List.of(validator3));
  }

  @TestTemplate
  void stopsToSendBatchesOnFirstFailure() {
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    validatorRegistrator =
        new ValidatorRegistrator(
            specContext.getSpec(),
            ownedValidators,
            validatorStatusProvider,
            proposerConfigPropertiesProvider,
            validatorRegistrationSigningService,
            validatorApiChannel,
            2);
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowForEpoch(0);

    // caching is after signing so still performed for first batch only
    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);
    // 2 times: isReady + registration
    verify(validatorStatusProvider, times(2)).getStatuses();
    verify(validatorApiChannel).registerValidators(any());

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(1);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2));
  }

  @TestTemplate
  void checksValidatorStatusWithPublicKeyOverride() {
    final BLSPublicKey validator2KeyOverride = dataStructureUtil.randomPublicKey();
    when(proposerConfigPropertiesProvider.getBuilderRegistrationPublicKeyOverride(
            validator2.getPublicKey()))
        .thenReturn(Optional.of(validator2KeyOverride));
    when(validatorStatusProvider.getStatuses())
        .thenReturn(
            Optional.of(
                Map.of(
                    validator1.getPublicKey(),
                    ValidatorStatus.active_ongoing,
                    validator2KeyOverride,
                    ValidatorStatus.active_ongoing,
                    validator3.getPublicKey(),
                    ValidatorStatus.active_ongoing)));
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowForEpoch(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(3);
    // 2 times: isReady + registration
    verify(validatorStatusProvider, times(2)).getStatuses();
    verify(validatorApiChannel).registerValidators(any());

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(1);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2, validator3));
  }

  private void setOwnedValidators(final Validator... validators) {
    final List<Validator> validatorsAsList = Arrays.stream(validators).collect(Collectors.toList());
    when(ownedValidators.getActiveValidators()).thenReturn(validatorsAsList);
  }

  private void runRegistrationFlowForSlot(final UInt64 slot) {
    validatorRegistrator.onSlot(slot);
  }

  private void runRegistrationFlowForEpoch(final int epoch) {
    // third slot in the epoch
    final UInt64 slot = UInt64.valueOf(epoch).times(slotsPerEpoch).plus(2);
    validatorRegistrator.onSlot(slot);
  }

  private List<SignedValidatorRegistration> captureRegistrationCall() {
    return captureRegistrationCalls(1).get(0);
  }

  private List<List<SignedValidatorRegistration>> captureRegistrationCalls(final int times) {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(validatorApiChannel, times(times)).registerValidators(argumentCaptor.capture());

    return argumentCaptor.getAllValues().stream().map(SszList::asList).toList();
  }

  private void verifyRegistrations(
      final List<SignedValidatorRegistration> validatorRegistrations,
      final List<Validator> expectedRegisteredValidators) {

    assertThat(validatorRegistrations)
        .hasSize(expectedRegisteredValidators.size())
        .allSatisfy(registration -> assertThat(registration.getSignature().isValid()).isTrue())
        .map(SignedValidatorRegistration::getMessage)
        .allSatisfy(
            registration -> {
              assertThat(registration.getFeeRecipient()).isEqualTo(eth1Address);
              assertThat(registration.getTimestamp()).isEqualTo(TIMESTAMP);
              assertThat(registration.getGasLimit()).isEqualTo(gasLimit);
            })
        .map(ValidatorRegistration::getPublicKey)
        .containsExactlyInAnyOrderElementsOf(
            expectedRegisteredValidators.stream()
                .map(Validator::getPublicKey)
                .collect(Collectors.toList()));
  }

  private SignedValidatorRegistration createSignedValidatorRegistration(
      final BLSPublicKey publicKey) {
    return SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
        VALIDATOR_REGISTRATION_SCHEMA.create(eth1Address, gasLimit, TIMESTAMP, publicKey),
        dataStructureUtil.randomSignature());
  }
}
