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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class ValidatorRegistratorTest {

  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private final FeeRecipientProvider feeRecipientProvider = mock(FeeRecipientProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final TimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(500);
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
            feeRecipientProvider,
            validatorApiChannel);
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(SafeFuture.completedFuture(null));
    // random signature for all signing
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

    when(feeRecipientProvider.getFeeRecipient(any())).thenReturn(Optional.of(eth1Address));

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    // Make sure all the futures have completed before next call
    verify(validatorApiChannel, timeout(1000)).registerValidators(any());

    // WHEN onSlot called again next epoch, registrations will be cached
    validatorRegistrator.onSlot(UInt64.valueOf(slotsPerEpoch));

    // THEN
    List<SszList<SignedValidatorRegistration>> registrationCalls =
        captureValidatorApiChannelCalls(2);

    registrationCalls.forEach(this::verifyRegistrations);

    // signer will be called in total 3 times, since from the 2nd run the signed registrations will
    // be cached
    verify(signer, times(3)).signValidatorRegistration(any(), any());
  }

  @TestTemplate
  void registersNewlyAddedValidators() {
    // GIVEN
    when(ownedValidators.getActiveValidators()).thenReturn(List.of(validator1));

    when(feeRecipientProvider.getFeeRecipient(any())).thenReturn(Optional.of(eth1Address));

    // Registering only validator1
    validatorRegistrator.onSlot(UInt64.ZERO);

    // Make sure all the futures have completed before next call
    verify(validatorApiChannel, timeout(1000)).registerValidators(any());

    // WHEN new validators are added
    when(ownedValidators.getActiveValidators())
        .thenReturn(List.of(validator1, validator2, validator3));

    // THEN only validator2 and validator3 should be registered
    validatorRegistrator.onValidatorsAdded();

    List<SszList<SignedValidatorRegistration>> registrationCalls =
        captureValidatorApiChannelCalls(2);

    assertThat(registrationCalls).hasSize(2);

    // first call only has validator1
    SszList<SignedValidatorRegistration> firstRegistrations = registrationCalls.get(0);
    assertThat(firstRegistrations).hasSize(1);
    Stream<BLSPublicKey> firstValidatorsPublicKeys = getPublicKeys(firstRegistrations);
    assertThat(firstValidatorsPublicKeys).containsExactly(validator1.getPublicKey());

    // second call should have processed validator2 and validator3
    SszList<SignedValidatorRegistration> secondRegistrations = registrationCalls.get(1);
    assertThat(secondRegistrations).hasSize(2);
    Stream<BLSPublicKey> secondValidatorsPublicKeys = getPublicKeys(secondRegistrations);
    assertThat(secondValidatorsPublicKeys)
        .containsExactlyInAnyOrder(validator2.getPublicKey(), validator3.getPublicKey());
  }

  @TestTemplate
  void skipsValidatorRegistrationIfFeeRecipientNotSpecified() {
    // GIVEN
    when(ownedValidators.getActiveValidators()).thenReturn(List.of(validator1, validator2));

    when(feeRecipientProvider.getFeeRecipient(validator1.getPublicKey()))
        .thenReturn(Optional.of(eth1Address));
    // no fee recipient provided for validator2
    when(feeRecipientProvider.getFeeRecipient(validator2.getPublicKey()))
        .thenReturn(Optional.empty());

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    // THEN
    SszList<SignedValidatorRegistration> registrations = captureValidatorApiChannelCall();

    assertThat(registrations).hasSize(1);
    assertThat(getPublicKeys(registrations)).containsExactly(validator1.getPublicKey());
  }

  private void verifyRegistrations(SszList<SignedValidatorRegistration> validatorRegistrations) {

    assertThat(validatorRegistrations).hasSize(3);

    assertThat(validatorRegistrations)
        .allSatisfy(
            registration -> {
              ValidatorRegistration message = registration.getMessage();
              assertThat(message.getFeeRecipient()).isEqualTo(eth1Address);
              assertThat(message.getGasLimit()).isEqualTo(UInt64.ZERO);
              assertThat(registration.getSignature().isValid()).isTrue();
            });

    Stream<BLSPublicKey> validatorsPublicKeys = getPublicKeys(validatorRegistrations);

    assertThat(validatorsPublicKeys)
        .containsExactlyInAnyOrder(
            validator1.getPublicKey(), validator2.getPublicKey(), validator3.getPublicKey());
  }

  private SszList<SignedValidatorRegistration> captureValidatorApiChannelCall() {
    return captureValidatorApiChannelCalls(1).get(0);
  }

  private List<SszList<SignedValidatorRegistration>> captureValidatorApiChannelCalls(int times) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(validatorApiChannel, timeout(1000).times(times))
        .registerValidators(argumentCaptor.capture());

    return argumentCaptor.getAllValues();
  }

  private Stream<BLSPublicKey> getPublicKeys(
      SszList<SignedValidatorRegistration> validatorRegistrations) {
    return validatorRegistrations.stream()
        .map(SignedValidatorRegistration::getMessage)
        .map(ValidatorRegistration::getPublicKey);
  }
}
