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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.operations.versions.bellatrix.BeaconPreparableProposer;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
public class BeaconProposerPreparerTest {
  private final int validator1Index = 19;
  private final int validator2Index = 23;
  private final int validator3Index = 24;
  private final ValidatorIndexProvider validatorIndexProvider = mock(ValidatorIndexProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ProposerConfigProvider proposerConfigProvider = mock(ProposerConfigProvider.class);

  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private BeaconProposerPreparer beaconProposerPreparer;
  private Eth1Address defaultFeeRecipient;
  private UInt64 defaultGasLimit;
  private Eth1Address defaultFeeRecipientConfig;
  private UInt64 defaultGasLimitConfig;
  private Eth1Address validator1FeeRecipientConfig;

  private UInt64 validator1GasLimitConfig;

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
    defaultGasLimit = specContext.getDataStructureUtil().randomUInt64();
    defaultFeeRecipientConfig = specContext.getDataStructureUtil().randomEth1Address();
    defaultGasLimitConfig = specContext.getDataStructureUtil().randomUInt64();
    validator1FeeRecipientConfig = specContext.getDataStructureUtil().randomEth1Address();
    validator1GasLimitConfig = specContext.getDataStructureUtil().randomUInt64();

    ProposerConfig proposerConfig =
        new ProposerConfig(
            Map.of(
                validator1.getPublicKey().toBytesCompressed(),
                new ProposerConfig.Config(
                    validator1FeeRecipientConfig,
                    new ProposerConfig.BuilderConfig(true, validator1GasLimitConfig, null))),
            new ProposerConfig.Config(
                defaultFeeRecipientConfig,
                new ProposerConfig.BuilderConfig(true, defaultGasLimitConfig, null)));

    beaconProposerPreparer =
        new BeaconProposerPreparer(
            validatorApiChannel,
            Optional.of(validatorIndexProvider),
            proposerConfigProvider,
            Optional.of(defaultFeeRecipient),
            defaultGasLimit,
            spec,
            Optional.empty());
    beaconProposerPreparer.initialize(
        Optional.of(validatorIndexProvider), Optional.of(ownedValidators));

    slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);

    when(validatorIndexProvider.getValidatorIndicesByPublicKey())
        .thenReturn(SafeFuture.completedFuture(validatorIndicesByPublicKey));

    when(ownedValidators.getValidator(validator1.getPublicKey()))
        .thenReturn(Optional.of(validator1));
    when(ownedValidators.getValidator(validator2.getPublicKey()))
        .thenReturn(Optional.of(validator2));

    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerConfig)));
    when(validatorApiChannel.prepareBeaconProposer(anyList())).thenReturn(SafeFuture.COMPLETE);
  }

  @TestTemplate
  void should_callPrepareBeaconProposerAtBeginningOfEpoch() {
    // not yet ready to provide a fee recipient to other consumers since the first sending hasn't
    // been done
    assertThat(beaconProposerPreparer.isReadyToProvideProperties()).isFalse();

    ArgumentCaptor<Collection<BeaconPreparableProposer>> captor = doCall();

    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            new BeaconPreparableProposer(
                UInt64.valueOf(validator1Index), validator1FeeRecipientConfig),
            new BeaconPreparableProposer(
                UInt64.valueOf(validator2Index), defaultFeeRecipientConfig));

    assertThat(beaconProposerPreparer.isReadyToProvideProperties()).isTrue();
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
            defaultGasLimit,
            spec,
            Optional.empty());

    assertThat(beaconProposerPreparer.getFeeRecipient(validator1.getPublicKey())).isEmpty();
  }

  @TestTemplate
  void getGasLimit_shouldReturnEmptyIfValidatorIndexProviderMissing() {
    beaconProposerPreparer =
        new BeaconProposerPreparer(
            validatorApiChannel,
            Optional.empty(),
            proposerConfigProvider,
            Optional.of(defaultFeeRecipient),
            defaultGasLimit,
            spec,
            Optional.empty());

    assertThat(beaconProposerPreparer.getGasLimit(validator1.getPublicKey())).isEmpty();
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnDefaultFeeRecipientWhenProposerConfigMissing() {
    assertThat(beaconProposerPreparer.getFeeRecipient(validator1.getPublicKey()))
        .contains(defaultFeeRecipient);
  }

  @TestTemplate
  void getGasLimit_shouldReturnDefaultFeeRecipientWhenProposerConfigMissing() {
    assertThat(beaconProposerPreparer.getGasLimit(validator1.getPublicKey()))
        .contains(defaultGasLimit);
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnEmptyIfValidatorIndexProviderMissingPubkey() {
    assertThat(beaconProposerPreparer.getFeeRecipient(dataStructureUtil.randomPublicKey()))
        .isEmpty();
  }

  @TestTemplate
  void getGasLimit_shouldReturnEmptyIfValidatorIndexProviderMissingPubkey() {
    assertThat(beaconProposerPreparer.getGasLimit(dataStructureUtil.randomPublicKey())).isEmpty();
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnConfigurationFileValueFirst(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(
        tempDir,
        validator1,
        dataStructureUtil.randomEth1Address(),
        dataStructureUtil.randomUInt64());
    beaconProposerPreparer.onSlot(UInt64.ONE);

    assertThat(beaconProposerPreparer.getFeeRecipient(validator1.getPublicKey()))
        .contains(validator1FeeRecipientConfig);
  }

  @TestTemplate
  void getGasLimit_shouldReturnConfigurationFileValueFirst(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(
        tempDir,
        validator1,
        dataStructureUtil.randomEth1Address(),
        dataStructureUtil.randomUInt64());
    beaconProposerPreparer.onSlot(UInt64.ONE);

    assertThat(beaconProposerPreparer.getGasLimit(validator1.getPublicKey()))
        .contains(validator1GasLimitConfig);
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
        tempDir,
        validator2,
        dataStructureUtil.randomEth1Address(),
        dataStructureUtil.randomUInt64());
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
    proposerWithRuntimeConfiguration(
        tempDir, validator2, address, dataStructureUtil.randomUInt64());
    beaconProposerPreparer.onSlot(UInt64.ONE);

    assertThat(beaconProposerPreparer.getFeeRecipient(validator2.getPublicKey())).contains(address);
  }

  @TestTemplate
  void getGasLimit_shouldReturnRuntimeConfigIfNotPresentInConfigurationFile(
      @TempDir final Path tempDir) throws IOException {
    final UInt64 gasLimit = dataStructureUtil.randomUInt64();
    proposerWithRuntimeConfiguration(
        tempDir, validator2, dataStructureUtil.randomEth1Address(), gasLimit);
    beaconProposerPreparer.onSlot(UInt64.ONE);

    assertThat(beaconProposerPreparer.getGasLimit(validator2.getPublicKey())).contains(gasLimit);
  }

  @TestTemplate
  void getFeeRecipient_shouldReturnConfigFileDefaultIfNotPresentInConfigOrRuntime() {
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThat(beaconProposerPreparer.getFeeRecipient(validator2.getPublicKey()))
        .contains(defaultFeeRecipientConfig);
  }

  @TestTemplate
  void getGasLimit_shouldReturnConfigFileDefaultIfNotPresentInConfigOrRuntime() {
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThat(beaconProposerPreparer.getGasLimit(validator2.getPublicKey()))
        .contains(defaultGasLimitConfig);
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
  void setGasLimit_shouldNotAcceptForConfiguredPubkey() {
    beaconProposerPreparer.onSlot(UInt64.ONE);
    assertThatThrownBy(
            () -> beaconProposerPreparer.setGasLimit(validator1.getPublicKey(), defaultGasLimit))
        .isInstanceOf(SetGasLimitException.class);
  }

  @TestTemplate
  void setFeeRecipient_shouldUpdateRuntimeFeeRecipient(@TempDir final Path tempDir)
      throws IOException, SetFeeRecipientException {
    final Eth1Address address = dataStructureUtil.randomEth1Address();
    proposerWithRuntimeConfiguration(
        tempDir,
        validator2,
        dataStructureUtil.randomEth1Address(),
        dataStructureUtil.randomUInt64());
    beaconProposerPreparer.onSlot(UInt64.ONE);

    beaconProposerPreparer.setFeeRecipient(validator2.getPublicKey(), address);
    assertThat(beaconProposerPreparer.getFeeRecipient(validator2.getPublicKey())).contains(address);
  }

  @TestTemplate
  void setGasLimit_shouldUpdateRuntimeFeeRecipient(@TempDir final Path tempDir)
      throws IOException, SetFeeRecipientException {
    final UInt64 gasLimit = dataStructureUtil.randomUInt64();
    proposerWithRuntimeConfiguration(
        tempDir,
        validator2,
        dataStructureUtil.randomEth1Address(),
        dataStructureUtil.randomUInt64());
    beaconProposerPreparer.onSlot(UInt64.ONE);

    beaconProposerPreparer.setGasLimit(validator2.getPublicKey(), gasLimit);
    assertThat(beaconProposerPreparer.getGasLimit(validator2.getPublicKey())).contains(gasLimit);
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
    when(ownedValidators.getValidator(validator3.getPublicKey()))
        .thenReturn(Optional.of(validator3));
    // validator 2 is configured via runtime configuration file
    proposerWithRuntimeConfiguration(
        tempDir, validator2, address2, dataStructureUtil.randomUInt64());
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
    when(validatorApiChannel.prepareBeaconProposer(anyList()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("error")));

    beaconProposerPreparer.onSlot(UInt64.ZERO);
    verify(validatorApiChannel, times(1)).prepareBeaconProposer(any());
  }

  private void proposerWithRuntimeConfiguration(
      final Path tempDir,
      final Validator validator,
      final Eth1Address eth1Address,
      final UInt64 gasLimit)
      throws IOException {
    Path recipients = tempDir.resolve("recipients");
    final String data =
        String.format(
            "{\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"}}",
            validator.getPublicKey(), eth1Address, gasLimit.toString());
    Files.write(recipients, data.getBytes(StandardCharsets.UTF_8));
    beaconProposerPreparer =
        new BeaconProposerPreparer(
            validatorApiChannel,
            Optional.of(validatorIndexProvider),
            proposerConfigProvider,
            Optional.of(defaultFeeRecipient),
            defaultGasLimit,
            spec,
            Optional.of(tempDir.resolve("recipients")));
    beaconProposerPreparer.initialize(
        Optional.of(validatorIndexProvider), Optional.of(ownedValidators));
    when(ownedValidators.getValidator(validator.getPublicKey())).thenReturn(Optional.of(validator));
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
