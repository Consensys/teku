/*
 * Copyright 2021 ConsenSys AG.
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class BeaconProposerPreparerTest {
  private final int validator1Index = 19;
  private final int validator2Index = 23;
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ProposerConfigProvider proposerConfigProvider = mock(ProposerConfigProvider.class);
  private BeaconProposerPreparer beaconProposerPreparer;
  private Eth1Address defaultFeeRecipient;
  private Eth1Address defaultFeeRecipientConfig;
  private Eth1Address validator1FeeRecipientConfig;

  private long slotsPerEpoch;

  @BeforeEach
  void setUp(SpecContext specContext) {
    Validator validator1 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);
    Validator validator2 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);
    Validator validatorWithoutIndex =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);

    Map<BLSPublicKey, Optional<Integer>> validatorIndexesByPublicKey =
        Map.of(
            validator1.getPublicKey(),
            Optional.of(validator1Index),
            validator2.getPublicKey(),
            Optional.of(validator2Index),
            validatorWithoutIndex.getPublicKey(),
            Optional.empty());

    defaultFeeRecipient = specContext.getDataStructureUtil().randomEth1Address();
    defaultFeeRecipientConfig = specContext.getDataStructureUtil().randomEth1Address();
    validator1FeeRecipientConfig = specContext.getDataStructureUtil().randomEth1Address();

    ProposerConfig proposerConfig =
        new ProposerConfig(
            Map.of(
                validator1.getPublicKey().toBytesCompressed(),
                new ProposerConfig.Config(validator1FeeRecipientConfig)),
            new ProposerConfig.Config(defaultFeeRecipientConfig));

    beaconProposerPreparer =
        new BeaconProposerPreparer(
            validatorApiChannel,
            validatorIndexProvider,
            proposerConfigProvider,
            Optional.of(defaultFeeRecipient),
            specContext.getSpec());

    slotsPerEpoch = specContext.getSpec().getSlotsPerEpoch(UInt64.ZERO);

    when(validatorIndexProvider.getValidatorIndexesByPublicKey())
        .thenReturn(SafeFuture.completedFuture(validatorIndexesByPublicKey));
    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerConfig)));
  }

  @TestTemplate
  void should_callPrepareBeaconProposerAtBeginningOfEpoch() {
    ArgumentCaptor<Collection<BeaconPreparableProposer>> captor = doCall();

    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            new BeaconPreparableProposer(
                UInt64.valueOf(validator1Index), validator1FeeRecipientConfig),
            new BeaconPreparableProposer(
                UInt64.valueOf(validator2Index), defaultFeeRecipientConfig));
  }

  @TestTemplate
  void should_useDefaultFeeRecipientWhenNoConfig() {
    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    ArgumentCaptor<Collection<BeaconPreparableProposer>> captor = doCall();

    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            new BeaconPreparableProposer(UInt64.valueOf(validator1Index), defaultFeeRecipient),
            new BeaconPreparableProposer(UInt64.valueOf(validator2Index), defaultFeeRecipient));
  }

  @TestTemplate
  void should_useDefaultFeeRecipientWhenExceptionInConfigProvider() {
    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("error")));

    ArgumentCaptor<Collection<BeaconPreparableProposer>> captor = doCall();

    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            new BeaconPreparableProposer(UInt64.valueOf(validator1Index), defaultFeeRecipient),
            new BeaconPreparableProposer(UInt64.valueOf(validator2Index), defaultFeeRecipient));
  }

  @TestTemplate
  void should_notCallPrepareBeaconProposerAfterFirstSlotOfEpoch() {
    beaconProposerPreparer.onSlot(UInt64.ZERO);
    verify(validatorApiChannel).prepareBeaconProposer(any());

    beaconProposerPreparer.onSlot(UInt64.ONE);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @TestTemplate
  void should_callPrepareBeaconProposerAfterOnPossibleMissedEvents() {
    beaconProposerPreparer.onPossibleMissedEvents();
    verify(validatorApiChannel).prepareBeaconProposer(any());
  }

  void should_callPrepareBeaconProposerAfterOnValidatorsAdded() {
    beaconProposerPreparer.onValidatorsAdded();
    verify(validatorApiChannel).prepareBeaconProposer(any());
  }

  @TestTemplate
  void should_catchApiExceptions() {
    doThrow(new RuntimeException("error")).when(validatorApiChannel).prepareBeaconProposer(any());

    beaconProposerPreparer.onSlot(UInt64.ZERO);
    verify(validatorApiChannel, times(1)).prepareBeaconProposer(any());
  }

  private ArgumentCaptor<Collection<BeaconPreparableProposer>> doCall() {
    beaconProposerPreparer.onSlot(UInt64.valueOf(slotsPerEpoch * 2));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Collection<BeaconPreparableProposer>> captor =
        ArgumentCaptor.forClass(Collection.class);
    verify(validatorApiChannel).prepareBeaconProposer(captor.capture());

    return captor;
  }
}
