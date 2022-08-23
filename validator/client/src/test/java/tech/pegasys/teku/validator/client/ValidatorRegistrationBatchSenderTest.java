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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class ValidatorRegistrationBatchSenderTest {

  private static final int BATCH_SIZE = 2;

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private DataStructureUtil dataStructureUtil;
  private ValidatorRegistrationBatchSender validatorRegistrationBatchSender;

  @BeforeEach
  void setUp(SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();

    when(validatorApiChannel.registerValidators(any())).thenReturn(SafeFuture.COMPLETE);

    validatorRegistrationBatchSender =
        new ValidatorRegistrationBatchSender(BATCH_SIZE, validatorApiChannel);
  }

  @TestTemplate
  void noRegistrationsAreSentIfEmpty() {
    final SafeFuture<Void> result = validatorRegistrationBatchSender.sendInBatches(List.of());

    assertThat(result).isCompleted();

    verifyNoInteractions(validatorApiChannel);
  }

  @TestTemplate
  void sendsRegistrationsInBatches() {
    SignedValidatorRegistration registration1 =
        dataStructureUtil.randomSignedValidatorRegistration();
    SignedValidatorRegistration registration2 =
        dataStructureUtil.randomSignedValidatorRegistration();
    SignedValidatorRegistration registration3 =
        dataStructureUtil.randomSignedValidatorRegistration();
    SignedValidatorRegistration registration4 =
        dataStructureUtil.randomSignedValidatorRegistration();
    SignedValidatorRegistration registration5 =
        dataStructureUtil.randomSignedValidatorRegistration();

    final SafeFuture<Void> result =
        validatorRegistrationBatchSender.sendInBatches(
            List.of(registration1, registration2, registration3, registration4, registration5));

    assertThat(result).isCompleted();

    final List<SszList<SignedValidatorRegistration>> sentRegistrations =
        captureSentRegistrations(3);

    assertThat(sentRegistrations.get(0)).containsExactly(registration1, registration2);
    assertThat(sentRegistrations.get(1)).containsExactly(registration3, registration4);
    assertThat(sentRegistrations.get(2)).containsExactly(registration5);
  }

  @TestTemplate
  void stopsToSendBatchesOnFirstFailure() {
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    final SafeFuture<Void> result =
        validatorRegistrationBatchSender.sendInBatches(
            dataStructureUtil.randomSignedValidatorRegistrations(5).asList());

    assertThat(result).isCompletedExceptionally();

    final List<SszList<SignedValidatorRegistration>> sentRegistrations =
        captureSentRegistrations(1);

    assertThat(sentRegistrations.get(0)).hasSize(BATCH_SIZE);
  }

  private List<SszList<SignedValidatorRegistration>> captureSentRegistrations(final int times) {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(validatorApiChannel, times(times)).registerValidators(argumentCaptor.capture());

    return argumentCaptor.getAllValues();
  }
}
