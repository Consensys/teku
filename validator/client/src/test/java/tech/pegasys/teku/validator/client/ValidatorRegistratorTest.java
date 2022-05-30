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
import org.mockito.InOrder;
import org.mockito.Mockito;
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
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistration;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class ValidatorRegistratorTest {

  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final TimeProvider stubTimeProvider = StubTimeProvider.withTimeInMillis(500);
  private final Signer signer = mock(Signer.class);

  private DataStructureUtil dataStructureUtil;
  private int slotsPerEpoch;

  private Validator validator1;
  private Validator validator2;
  private Validator validator3;

  private ValidatorRegistrator validatorRegistrator;

  @BeforeEach
  void setUp(SpecContext specContext) {
    slotsPerEpoch = specContext.getSpec().getGenesisSpecConfig().getSlotsPerEpoch();
    dataStructureUtil = specContext.getDataStructureUtil();
    validator1 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator2 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator3 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validatorRegistrator =
        new ValidatorRegistrator(
            ownedValidators, validatorApiChannel, specContext.getSpec(), stubTimeProvider);
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(SafeFuture.completedFuture(null));
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

    doAnswer(invocation -> SafeFuture.completedFuture(dataStructureUtil.randomSignature()))
        .when(signer)
        .signValidatorRegistration(any(ValidatorRegistration.class), any(UInt64.class));

    // WHEN
    validatorRegistrator.onSlot(UInt64.ZERO);

    // THEN
    @SuppressWarnings("unchecked")
    ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    InOrder inOrder = Mockito.inOrder(validatorApiChannel);

    inOrder.verify(validatorApiChannel, timeout(1000)).registerValidators(argumentCaptor.capture());
    verifyRegistrations(argumentCaptor.getValue());

    // WHEN onSlot called again next epoch, registrations will be cached
    validatorRegistrator.onSlot(UInt64.valueOf(slotsPerEpoch));

    // THEN

    // signer will be called in total 3 times, since from the 2nd run the signed registrations will
    // be cached
    verify(signer, times(3)).signValidatorRegistration(any(), any());

    inOrder.verify(validatorApiChannel, timeout(1000)).registerValidators(argumentCaptor.capture());
    verifyRegistrations(argumentCaptor.getValue());
  }

  private void verifyRegistrations(SszList<SignedValidatorRegistration> validatorRegistrations) {

    assertThat(validatorRegistrations).hasSize(3);
    assertThat(validatorRegistrations)
        .allSatisfy(registration -> assertThat(registration.getSignature().isValid()).isTrue());

    Stream<BLSPublicKey> validatorsPublicKeys =
        validatorRegistrations.stream()
            .map(SignedValidatorRegistration::getMessage)
            .map(ValidatorRegistration::getPublicKey);

    assertThat(validatorsPublicKeys)
        .containsExactlyInAnyOrder(
            validator1.getPublicKey(), validator2.getPublicKey(), validator3.getPublicKey());
  }
}
