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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class BeaconProposerPreparerTest {
  private final int validator1Index = 19;
  private final int validator2Index = 23;
  private final int validator3Index = 24;
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ProposerConfigProvider proposerConfigProvider = mock(ProposerConfigProvider.class);
  private BeaconProposerPreparer beaconProposerPreparer;
  private Eth1Address defaultFeeRecipient;
  private Eth1Address defaultFeeRecipientConfig;
  private Eth1Address validator1FeeRecipientConfig;

  private Spec spec;

  private Validator validator1;
  private Validator validator2;
  private Validator validator3;
  private DataStructureUtil dataStructureUtil;

  private long slotsPerEpoch;

  @BeforeEach
  void setUp(SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    validator1 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);
    validator2 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);
    validator3 =
        new Validator(
            specContext.getDataStructureUtil().randomPublicKey(),
            mock(Signer.class),
            Optional::empty);

    Map<BLSPublicKey, Integer> validatorIndicesByPublicKey =
        Map.of(
            validator1.getPublicKey(), validator1Index,
            validator2.getPublicKey(), validator2Index,
            validator3.getPublicKey(), validator3Index);

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
            spec);

    slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);

    when(validatorIndexProvider.getValidatorIndicesByPublicKey())
        .thenReturn(SafeFuture.completedFuture(validatorIndicesByPublicKey));
    when(validatorIndexProvider.containsPublicKey(eq(validator1.getPublicKey()))).thenReturn(true);
    when(validatorIndexProvider.containsPublicKey(eq(validator2.getPublicKey()))).thenReturn(true);
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
  void getFeeRecipient_shouldReturnEmptyIfValidatorIndexProviderMissing() {
    beaconProposerPreparer =
        new BeaconProposerPreparer(
            validatorApiChannel,
            Optional.empty(),
            proposerConfigProvider,
            Optional.of(defaultFeeRecipient),
            spec,
            Optional.empty());

    assertThat(beaconProposerPreparer.getFeeRecipient(validator1.getPublicKey())).isEmpty();
  }

  @TestTemplate
  void getFeeRecipent_shouldReturnDefaultFeeRecipientWhenProposerConfigMissing() {
    assertThat(beaconProposerPreparer.getFeeRecipient(validator1.getPublicKey()))
        .contains(defaultFeeRecipient);
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnEmptyIfValidatorIndexProviderMissingPubkey() {
    assertThat(beaconProposerPreparer.getFeeRecipient(dataStructureUtil.randomPublicKey()))
        .isEmpty();
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnConfigurationFileValueFirst(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(
        tempDir, validator1.getPublicKey(), dataStructureUtil.randomEth1Address());
    beaconProposerPreparer.onSlot(UInt64.ONE);

    assertThat(beaconProposerPreparer.getFeeRecipient(validator1.getPublicKey()))
        .contains(validator1FeeRecipientConfig);
  }

  @TestTemplate
  void deleteFeeRecipient_shouldReturnFalseIfConfigurationSetsAddress() {
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThat(beaconProposerPreparer.deleteFeeRecipient(validator1.getPublicKey())).isFalse();
  }

  @TestTemplate
  void deleteFeeRecipient_shouldReturnTrueIfConfigHasPublicKey(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(
        tempDir, validator2.getPublicKey(), dataStructureUtil.randomEth1Address());
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThat(beaconProposerPreparer.deleteFeeRecipient(validator2.getPublicKey())).isTrue();
  }

  @TestTemplate
  void deleteFeeRecipient_shouldReturnTrueIfConfigMissingPublicKey() {
    // successful in that it is not a configured key, and it is not listed in the runtime
    // configuration.
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThat(beaconProposerPreparer.deleteFeeRecipient(validator2.getPublicKey())).isTrue();
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnRuntimeConfigIfNotPresentInConfigurationFile(
      @TempDir final Path tempDir) throws IOException {
    final Eth1Address address = dataStructureUtil.randomEth1Address();
    proposerWithRuntimeConfiguration(tempDir, validator2.getPublicKey(), address);
    beaconProposerPreparer.onSlot(UInt64.ONE);

    assertThat(beaconProposerPreparer.getFeeRecipient(validator2.getPublicKey())).contains(address);
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnConfigFileDefaultIfNotPresentInConfigOrRuntime() {
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThat(beaconProposerPreparer.getFeeRecipient(validator2.getPublicKey()))
        .contains(defaultFeeRecipientConfig);
  }

  @TestTemplate
  void setFeeRecipient_shouldNotAcceptForConfiguredPubkey() {
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThatThrownBy(
            () ->
                beaconProposerPreparer.setFeeRecipient(
                    validator1.getPublicKey(), defaultFeeRecipient))
        .isInstanceOf(SetFeeRecipientException.class);
  }

  @TestTemplate
  void setFeeRecipient_shouldUpdateRuntimeFeeRecipient(@TempDir final Path tempDir)
      throws IOException, SetFeeRecipientException {
    final Eth1Address address = dataStructureUtil.randomEth1Address();
    proposerWithRuntimeConfiguration(
        tempDir, validator2.getPublicKey(), dataStructureUtil.randomEth1Address());
    beaconProposerPreparer.onSlot(UInt64.ONE);

    beaconProposerPreparer.setFeeRecipient(validator2.getPublicKey(), address);
    assertThat(beaconProposerPreparer.getFeeRecipient(validator2.getPublicKey())).contains(address);
  }

  @TestTemplate
  void setFeeRecipient_shouldNotAllowSettingToZero() {
    assertThatThrownBy(
            () ->
                beaconProposerPreparer.setFeeRecipient(validator2.getPublicKey(), Eth1Address.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void send_shouldIncludeRuntimeFeeRecipients(@TempDir final Path tempDir)
      throws IOException, SetFeeRecipientException {
    final Eth1Address address2 = dataStructureUtil.randomEth1Address();
    final Eth1Address address3 = dataStructureUtil.randomEth1Address();
    when(validatorIndexProvider.containsPublicKey(eq(validator3.getPublicKey()))).thenReturn(true);
    // validator 2 is configured via runtime configuration file
    proposerWithRuntimeConfiguration(tempDir, validator2.getPublicKey(), address2);
    // validator 3 is configured from API
    beaconProposerPreparer.setFeeRecipient(validator3.getPublicKey(), address3);

    ArgumentCaptor<Collection<BeaconPreparableProposer>> captor = doCall();

    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            new BeaconPreparableProposer(
                UInt64.valueOf(validator1Index), validator1FeeRecipientConfig),
            new BeaconPreparableProposer(UInt64.valueOf(validator2Index), address2),
            new BeaconPreparableProposer(UInt64.valueOf(validator3Index), address3));
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

  @TestTemplate
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

  private void proposerWithRuntimeConfiguration(
      final Path tempDir, final BLSPublicKey pubkey, final Eth1Address eth1Address)
      throws IOException {
    Path recipients = tempDir.resolve("recipients");
    final String data = String.format("{\"%s\":{\"fee_recipient\":\"%s\"}}", pubkey, eth1Address);
    Files.write(recipients, data.getBytes(StandardCharsets.UTF_8));
    beaconProposerPreparer =
        new BeaconProposerPreparer(
            validatorApiChannel,
            Optional.of(validatorIndexProvider),
            proposerConfigProvider,
            Optional.of(defaultFeeRecipient),
            spec,
            Optional.of(tempDir.resolve("recipients")));
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
