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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.RegistrationOverrides;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

public class ProposerConfigManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private Validator validatorInConfig;
  private Validator validatorInRuntimeConfig;
  private Validator validatorNotInConfig;

  private Eth1Address cliDefaultFeeRecipient;
  private UInt64 defaultGasLimit;
  private Eth1Address defaultFeeRecipientConfig;
  private Eth1Address validatorFeeRecipientConfig;
  private UInt64 validatorGasLimitConfig;

  private Eth1Address validatorFeeRecipientRuntime;
  private UInt64 validatorGasLimitRuntime;

  private final ValidatorConfig validatorConfig = mock(ValidatorConfig.class);
  private final ProposerConfigProvider proposerConfigProvider = mock(ProposerConfigProvider.class);
  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);

  private ProposerConfigManager proposerConfigManager;

  @BeforeEach
  void setUp() throws IOException {

    validatorInConfig =
        new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
    validatorNotInConfig =
        new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
    validatorInRuntimeConfig =
        new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);

    cliDefaultFeeRecipient = dataStructureUtil.randomEth1Address();
    defaultGasLimit = dataStructureUtil.randomUInt64();
    defaultFeeRecipientConfig = dataStructureUtil.randomEth1Address();
    final UInt64 defaultGasLimitConfig = dataStructureUtil.randomUInt64();

    validatorFeeRecipientConfig = dataStructureUtil.randomEth1Address();
    validatorGasLimitConfig = dataStructureUtil.randomUInt64();

    validatorFeeRecipientRuntime = dataStructureUtil.randomEth1Address();
    validatorGasLimitRuntime = dataStructureUtil.randomUInt64();

    when(validatorConfig.getProposerDefaultFeeRecipient())
        .thenReturn(Optional.ofNullable(cliDefaultFeeRecipient));
    when(validatorConfig.getBuilderRegistrationDefaultGasLimit()).thenReturn(defaultGasLimit);
    when(validatorConfig.getBuilderRegistrationTimestampOverride()).thenReturn(Optional.empty());
    when(validatorConfig.getBuilderRegistrationPublicKeyOverride()).thenReturn(Optional.empty());
    when(validatorConfig.isBuilderRegistrationDefaultEnabled()).thenReturn(false);

    ProposerConfig proposerConfig =
        new ProposerConfig(
            Map.of(
                validatorInConfig.getPublicKey().toBytesCompressed(),
                new ProposerConfig.Config(
                    validatorFeeRecipientConfig,
                    new ProposerConfig.BuilderConfig(true, validatorGasLimitConfig, null))),
            new ProposerConfig.Config(
                defaultFeeRecipientConfig,
                new ProposerConfig.BuilderConfig(false, defaultGasLimitConfig, null)));

    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerConfig)));

    final Validator defaultOwnedValidator =
        new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);

    when(ownedValidators.getValidator(any())).thenReturn(Optional.of(defaultOwnedValidator));

    setUpProposerConfigManager(Optional.empty(), Optional.empty());
  }

  @ParameterizedTest
  @EnumSource(Properties.class)
  void shouldReturnValidatorSpecific(final Properties property) throws IOException {

    switch (property) {
      case FEE_RECIPIENT:
        prepareConfigWithValidatorSpecificProperty(property, validatorFeeRecipientConfig);
        assertThat(proposerConfigManager.getFeeRecipient(validatorInConfig.getPublicKey()))
            .contains(validatorFeeRecipientConfig);
        break;
      case BUILDER_ENABLED:
        prepareConfigWithValidatorSpecificProperty(property, true);
        assertThat(proposerConfigManager.isBuilderEnabled(validatorInConfig.getPublicKey()))
            .isTrue();

        prepareConfigWithValidatorSpecificProperty(property, false);
        assertThat(proposerConfigManager.isBuilderEnabled(validatorInConfig.getPublicKey()))
            .isFalse();
        break;
      case BUILDER_GAS_LIMIT:
        final UInt64 randomGasLimit = dataStructureUtil.randomUInt64();
        prepareConfigWithValidatorSpecificProperty(property, randomGasLimit);
        assertThat(proposerConfigManager.getGasLimit(validatorInConfig.getPublicKey()))
            .isEqualTo(randomGasLimit);
        break;
      case BUILDER_REGISTRATION_OVERRIDE_PUB_KEY:
        final BLSPublicKey randomKey = dataStructureUtil.randomPublicKey();
        prepareConfigWithValidatorSpecificProperty(property, randomKey);
        assertThat(
                proposerConfigManager.getBuilderRegistrationPublicKeyOverride(
                    validatorInConfig.getPublicKey()))
            .contains(randomKey);
        break;
      case BUILDER_REGISTRATION_OVERRIDE_TIMESTAMP:
        final UInt64 randomTimestamp = dataStructureUtil.randomUInt64();
        prepareConfigWithValidatorSpecificProperty(property, randomTimestamp);
        assertThat(
                proposerConfigManager.getBuilderRegistrationTimestampOverride(
                    validatorInConfig.getPublicKey()))
            .contains(randomTimestamp);
        break;
    }
  }

  @Test
  void initialize_throwsIfProposerConfigProviderThrows() {
    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.failedFuture(new IOException("error")));

    proposerConfigManager =
        new ProposerConfigManager(
            validatorConfig, new RuntimeProposerConfig(Optional.empty()), proposerConfigProvider);

    assertThat(proposerConfigManager.initialize(ownedValidators)).isCompletedExceptionally();
  }

  @ParameterizedTest
  @EnumSource(Properties.class)
  void shouldReturnDefaultConfig(final Properties property) throws IOException {

    switch (property) {
      case FEE_RECIPIENT:
        prepareConfigWithDefaultConfigProperty(property, defaultFeeRecipientConfig);
        assertThat(proposerConfigManager.getFeeRecipient(validatorNotInConfig.getPublicKey()))
            .contains(defaultFeeRecipientConfig);
        break;
      case BUILDER_ENABLED:
        prepareConfigWithDefaultConfigProperty(property, true);
        assertThat(proposerConfigManager.isBuilderEnabled(validatorNotInConfig.getPublicKey()))
            .isTrue();

        prepareConfigWithDefaultConfigProperty(property, false);
        assertThat(proposerConfigManager.isBuilderEnabled(validatorNotInConfig.getPublicKey()))
            .isFalse();
        break;
      case BUILDER_GAS_LIMIT:
        final UInt64 randomGasLimit = dataStructureUtil.randomUInt64();
        prepareConfigWithDefaultConfigProperty(property, randomGasLimit);
        assertThat(proposerConfigManager.getGasLimit(validatorNotInConfig.getPublicKey()))
            .isEqualTo(randomGasLimit);
        break;
      case BUILDER_REGISTRATION_OVERRIDE_PUB_KEY:
        // not allowed
        break;
      case BUILDER_REGISTRATION_OVERRIDE_TIMESTAMP:
        final UInt64 randomTimestamp = dataStructureUtil.randomUInt64();
        prepareConfigWithDefaultConfigProperty(property, randomTimestamp);
        assertThat(
                proposerConfigManager.getBuilderRegistrationTimestampOverride(
                    validatorNotInConfig.getPublicKey()))
            .contains(randomTimestamp);
        break;
    }
  }

  @Test
  void getFeeRecipient_shouldReturnCliDefaultIfNotPresentInConfigOrRuntime() throws IOException {
    setUpWithNoConfigs(false);

    assertThat(proposerConfigManager.getFeeRecipient(validatorNotInConfig.getPublicKey()))
        .contains(cliDefaultFeeRecipient);
  }

  @Test
  void getGasLimit_shouldReturnDefaultIfNotPresentInConfigOrRuntime() throws IOException {
    setUpWithNoConfigs(false);

    assertThat(proposerConfigManager.getGasLimit(validatorNotInConfig.getPublicKey()))
        .isEqualTo(defaultGasLimit);
  }

  @Test
  void getBuilderRegistrationTimestampOverride_shouldReturnDefaultIfNotPresentInConfigOrRuntime()
      throws IOException {
    setUpWithNoConfigs(false);
    final UInt64 randomTimestamp = dataStructureUtil.randomUInt64();
    when(validatorConfig.getBuilderRegistrationTimestampOverride())
        .thenReturn(Optional.of(randomTimestamp));

    assertThat(
            proposerConfigManager.getBuilderRegistrationTimestampOverride(
                validatorNotInConfig.getPublicKey()))
        .contains(randomTimestamp);
  }

  @Test
  void getBuilderRegistrationPublicKeyOverride_shouldReturnDefaultIfNotPresentInConfigOrRuntime()
      throws IOException {
    setUpWithNoConfigs(false);
    final BLSPublicKey randomPubKey = dataStructureUtil.randomPublicKey();
    when(validatorConfig.getBuilderRegistrationPublicKeyOverride())
        .thenReturn(Optional.of(randomPubKey));

    assertThat(
            proposerConfigManager.getBuilderRegistrationPublicKeyOverride(
                validatorNotInConfig.getPublicKey()))
        .contains(randomPubKey);
  }

  @Test
  void isBuilderEnabled_shouldReturnCliDefaultIfNotPresentInConfigOrRuntime() throws IOException {
    setUpWithNoConfigs(false);

    assertThat(proposerConfigManager.isBuilderEnabled(validatorNotInConfig.getPublicKey()))
        .isEqualTo(false);

    setUpWithNoConfigs(true);

    assertThat(proposerConfigManager.isBuilderEnabled(validatorNotInConfig.getPublicKey()))
        .isEqualTo(true);
  }

  @Test
  void getFeeRecipient_shouldReturnRuntimeConfiguration(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(tempDir);

    assertThat(proposerConfigManager.getFeeRecipient(validatorInRuntimeConfig.getPublicKey()))
        .contains(validatorFeeRecipientRuntime);
  }

  @Test
  void getFeeRecipient_shouldReturnRuntimeConfigurationIfProposerConfigIsNotConfigured(
      @TempDir final Path tempDir) throws IOException {
    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    proposerWithRuntimeConfiguration(tempDir);

    assertThat(proposerConfigManager.getFeeRecipient(validatorInRuntimeConfig.getPublicKey()))
        .contains(validatorFeeRecipientRuntime);
  }

  @Test
  void getFeeRecipient_shouldReturnConfigurationFileValueFirst(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(tempDir);

    assertThat(proposerConfigManager.getFeeRecipient(validatorInConfig.getPublicKey()))
        .contains(validatorFeeRecipientConfig);
  }

  @Test
  void getGasLimit_shouldReturnConfigurationFileValueFirst(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(tempDir);

    assertThat(proposerConfigManager.getGasLimit(validatorInConfig.getPublicKey()))
        .isEqualTo(validatorGasLimitConfig);
  }

  @Test
  void deleteFeeRecipient_shouldReturnFalseIfConfigurationSetsAddress() {
    assertThat(proposerConfigManager.deleteFeeRecipient(validatorInConfig.getPublicKey()))
        .isFalse();
  }

  @Test
  void deleteFeeRecipient_shouldReturnTrueIfConfigHasPublicKey(@TempDir final Path tempDir)
      throws IOException {
    proposerWithRuntimeConfiguration(tempDir);
    assertThat(proposerConfigManager.deleteFeeRecipient(validatorInRuntimeConfig.getPublicKey()))
        .isTrue();
  }

  @Test
  void deleteFeeRecipient_shouldReturnTrueIfConfigMissingPublicKey() {
    // successful in that it is not a configured key, and it is not listed in the runtime
    // configuration.
    assertThat(proposerConfigManager.deleteFeeRecipient(validatorNotInConfig.getPublicKey()))
        .isTrue();
  }

  @Test
  void getFeeRecipient_shouldReturnRuntimeConfigIfNotPresentInConfigurationFile(
      @TempDir final Path tempDir) throws IOException {
    proposerWithRuntimeConfiguration(tempDir);

    assertThat(proposerConfigManager.getFeeRecipient(validatorInRuntimeConfig.getPublicKey()))
        .contains(validatorFeeRecipientRuntime);
  }

  @Test
  void getGasLimit_shouldReturnRuntimeConfigIfNotPresentInConfigurationFile(
      @TempDir final Path tempDir) throws IOException {
    proposerWithRuntimeConfiguration(tempDir);

    assertThat(proposerConfigManager.getGasLimit(validatorInRuntimeConfig.getPublicKey()))
        .isEqualTo(validatorGasLimitRuntime);
  }

  @Test
  void setFeeRecipient_shouldNotAcceptForConfiguredPubkey() {
    assertThatThrownBy(
            () ->
                proposerConfigManager.setFeeRecipient(
                    validatorInConfig.getPublicKey(), cliDefaultFeeRecipient))
        .isInstanceOf(SetFeeRecipientException.class);
  }

  @Test
  void setGasLimit_shouldNotAcceptForConfiguredPubkey() {
    assertThatThrownBy(
            () ->
                proposerConfigManager.setGasLimit(
                    validatorInConfig.getPublicKey(), defaultGasLimit))
        .isInstanceOf(SetGasLimitException.class);
  }

  @Test
  void setFeeRecipient_shouldUpdateRuntimeFeeRecipient(@TempDir final Path tempDir)
      throws IOException, SetFeeRecipientException {
    final Eth1Address address = dataStructureUtil.randomEth1Address();
    proposerWithRuntimeConfiguration(tempDir);

    proposerConfigManager.setFeeRecipient(validatorNotInConfig.getPublicKey(), address);
    assertThat(proposerConfigManager.getFeeRecipient(validatorNotInConfig.getPublicKey()))
        .contains(address);
  }

  @Test
  void setGasLimit_shouldUpdateRuntimeFeeRecipient(@TempDir final Path tempDir)
      throws IOException, SetFeeRecipientException {
    final UInt64 gasLimit = dataStructureUtil.randomUInt64();
    proposerWithRuntimeConfiguration(tempDir);

    proposerConfigManager.setGasLimit(validatorNotInConfig.getPublicKey(), gasLimit);
    assertThat(proposerConfigManager.getGasLimit(validatorNotInConfig.getPublicKey()))
        .isEqualTo(gasLimit);
  }

  @Test
  void setFeeRecipient_shouldNotAllowSettingToZero() {
    assertThatThrownBy(
            () ->
                proposerConfigManager.setFeeRecipient(
                    validatorNotInConfig.getPublicKey(), Eth1Address.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void setUpWithNoConfigs(final boolean builderDefaultEnabled) throws IOException {
    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(validatorConfig.getProposerDefaultFeeRecipient())
        .thenReturn(Optional.of(cliDefaultFeeRecipient));

    when(validatorConfig.isBuilderRegistrationDefaultEnabled()).thenReturn(builderDefaultEnabled);

    setUpProposerConfigManager(Optional.empty(), Optional.empty());
  }

  private void prepareConfigWithValidatorSpecificProperty(
      final Properties property, final Object value) throws IOException {
    ProposerConfig proposerConfig =
        new ProposerConfig(
            Map.of(
                validatorInConfig.getPublicKey().toBytesCompressed(),
                buildSingleConfigWithProperty(property, value, false)),
            new ProposerConfig.Config(
                defaultFeeRecipientConfig, new ProposerConfig.BuilderConfig(false, null, null)));

    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerConfig)));

    setUpProposerConfigManager(Optional.empty(), Optional.empty());
  }

  private void prepareConfigWithDefaultConfigProperty(final Properties property, final Object value)
      throws IOException {
    ProposerConfig proposerConfig =
        new ProposerConfig(Map.of(), buildSingleConfigWithProperty(property, value, true));

    when(proposerConfigProvider.getProposerConfig())
        .thenReturn(SafeFuture.completedFuture(Optional.of(proposerConfig)));

    setUpProposerConfigManager(Optional.empty(), Optional.empty());
  }

  private ProposerConfig.Config buildSingleConfigWithProperty(
      final Properties property, final Object value, final boolean isDefault) {
    switch (property) {
      case FEE_RECIPIENT:
        return new ProposerConfig.Config((Eth1Address) value, null);
      case BUILDER_ENABLED:
        return new ProposerConfig.Config(
            isDefault ? defaultFeeRecipientConfig : null,
            new ProposerConfig.BuilderConfig((Boolean) value, null, null));
      case BUILDER_GAS_LIMIT:
        return new ProposerConfig.Config(
            isDefault ? defaultFeeRecipientConfig : null,
            new ProposerConfig.BuilderConfig(isDefault ? false : null, (UInt64) value, null));
      case BUILDER_REGISTRATION_OVERRIDE_PUB_KEY:
        return new ProposerConfig.Config(
            isDefault ? defaultFeeRecipientConfig : null,
            new ProposerConfig.BuilderConfig(
                isDefault ? false : null,
                null,
                new RegistrationOverrides(null, (BLSPublicKey) value)));
      case BUILDER_REGISTRATION_OVERRIDE_TIMESTAMP:
        return new ProposerConfig.Config(
            isDefault ? defaultFeeRecipientConfig : null,
            new ProposerConfig.BuilderConfig(
                isDefault ? false : null, null, new RegistrationOverrides((UInt64) value, null)));
    }

    throw new RuntimeException();
  }

  private void proposerWithRuntimeConfiguration(final Path tempDir) throws IOException {

    setUpProposerConfigManager(
        Optional.of(tempDir),
        Optional.of(
            String.format(
                "{\"%s\":{\"fee_recipient\":\"%s\",\"gas_limit\":\"%s\"}}",
                validatorInRuntimeConfig.getPublicKey(),
                validatorFeeRecipientRuntime,
                validatorGasLimitRuntime.toString())));
  }

  private void setUpProposerConfigManager(
      final Optional<Path> runtimeConfigPath, final Optional<String> existingRuntimeData)
      throws IOException {
    Optional<Path> storagePath = Optional.empty();

    if (runtimeConfigPath.isPresent() && existingRuntimeData.isPresent()) {
      Path recipients = runtimeConfigPath.get().resolve("recipients");
      Files.write(recipients, existingRuntimeData.get().getBytes(StandardCharsets.UTF_8));

      storagePath = Optional.of(recipients);
    }

    proposerConfigManager =
        new ProposerConfigManager(
            validatorConfig, new RuntimeProposerConfig(storagePath), proposerConfigProvider);

    assertThat(proposerConfigManager.isReadyToProvideProperties()).isFalse();
    assertThat(proposerConfigManager.initialize(ownedValidators)).isCompleted();
    assertThat(proposerConfigManager.isReadyToProvideProperties()).isTrue();
  }

  enum Properties {
    FEE_RECIPIENT,
    BUILDER_ENABLED,
    BUILDER_GAS_LIMIT,
    BUILDER_REGISTRATION_OVERRIDE_PUB_KEY,
    BUILDER_REGISTRATION_OVERRIDE_TIMESTAMP
  }
}
