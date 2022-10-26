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

package tech.pegasys.teku.validator.client.proposerconfig.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.ProposerConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.BuilderConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;
import tech.pegasys.teku.validator.client.ProposerConfig.RegistrationOverrides;

public class ProposerConfigLoaderTest {
  private final ProposerConfigLoader loader = new ProposerConfigLoader();

  @Test
  void shouldLoadValidConfigFromUrl() {
    final URL resource = Resources.getResource("proposerConfigValid1.json");

    validateContent1(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithEmptyProposerConfig() {
    final URL resource = Resources.getResource("proposerConfigValid2.json");

    validateContent2(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadNullFeeRecipient() {
    final URL resource = Resources.getResource("proposerConfigValid3.json");

    validateContent3(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithOnlyDefaultValidatorRegistrationEnabled() {
    final URL resource = Resources.getResource("proposerConfigWithBuilderValid1.json");

    validateContentWithValidatorRegistration1(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithDefaultValidatorRegistrationDisabled() {
    final URL resource = Resources.getResource("proposerConfigWithBuilderValid2.json");

    validateContentWithValidatorRegistration2(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithRegistrationOverrides() {
    final URL resource = Resources.getResource("proposerConfigWithRegistrationOverrides1.json");

    validateContentWithRegistrationOverrides1(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithEmptyDefaultRegistrationOverridesAndMissingFields() {
    final URL resource = Resources.getResource("proposerConfigWithRegistrationOverrides2.json");

    validateContentWithRegistrationOverrides2(loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadInvalidPubKey() {
    final URL resource = Resources.getResource("proposerConfigInvalid1.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadNullFeeRecipientInDefaultConfig() {
    final URL resource = Resources.getResource("proposerConfigInvalid2.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadInvalidFeeRecipient() {
    final URL resource = Resources.getResource("proposerConfigInvalid3.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadMissingFeeRecipient() {
    final URL resource = Resources.getResource("proposerConfigInvalid4.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadMissingDefault() {
    final URL resource = Resources.getResource("proposerConfigInvalid5.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadInvalidJson() {
    final URL resource = Resources.getResource("proposerConfigInvalid6.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  private void validateContent1(final ProposerConfig config) {
    final Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isPresent();
    assertThat(theConfig.get().getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0x50155530FCE8a85ec7055A5F8b2bE214B3DaeFd3")));

    final Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A")));
  }

  private void validateContent2(final ProposerConfig config) {
    final Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isEmpty();

    final Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A")));
  }

  private void validateContent3(final ProposerConfig config) {
    final Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isPresent();
    assertThat(theConfig.get().getFeeRecipient()).isEmpty();

    final Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A")));
  }

  private void validateContentWithValidatorRegistration1(final ProposerConfig config) {
    final Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isEmpty();

    final Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A")));

    assertThat(defaultConfig.getBuilder()).isPresent();
    assertThat(defaultConfig.getBuilder().get().isEnabled()).isEqualTo(Optional.of(true));
  }

  private void validateContentWithValidatorRegistration2(final ProposerConfig config) {
    final Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isPresent();
    assertThat(theConfig.get().getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0x50155530FCE8a85ec7055A5F8b2bE214B3DaeFd3")));

    assertThat(theConfig.get().getBuilder()).isPresent();
    final BuilderConfig builder = theConfig.get().getBuilder().get();
    assertThat(builder.isEnabled()).isEmpty();
    assertThat(builder.getGasLimit().orElseThrow()).isEqualTo(UInt64.valueOf(12345654321L));

    final Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A")));

    assertThat(defaultConfig.getBuilder()).isPresent();
    assertThat(defaultConfig.getBuilder().get().isEnabled()).contains(false);
    assertThat(defaultConfig.getBuilder().get().getGasLimit()).isEmpty();
  }

  private void validateContentWithRegistrationOverrides1(final ProposerConfig config) {
    final Optional<RegistrationOverrides> pubKeyRegistrationOverrides =
        config
            .getConfigForPubKey(
                "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a")
            .flatMap(this::getRegistrationOverrides);
    assertThat(pubKeyRegistrationOverrides)
        .hasValueSatisfying(
            registrationOverrides -> {
              assertThat(registrationOverrides.getPublicKey())
                  .hasValue(
                      BLSPublicKey.fromHexString(
                          "0xb53d21a4cfd562c469cc81514d4ce5a6b577d8403d32a394dc265dd190b47fa9f829fdd7963afdf972e5e77854051f6f"));
              assertThat(registrationOverrides.getTimestamp()).hasValue(UInt64.valueOf(1234));
            });
    final Optional<RegistrationOverrides> defaultRegistrationOverrides =
        getRegistrationOverrides(config.getDefaultConfig());
    assertThat(defaultRegistrationOverrides)
        .hasValueSatisfying(
            registrationOverrides -> {
              assertThat(registrationOverrides.getPublicKey())
                  .hasValue(
                      BLSPublicKey.fromHexString(
                          "0xa491d1b0ecd9bb917989f0e74f0dea0422eac4a873e5e2644f368dffb9a6e20fd6e10c1b77654d067c0618f6e5a7f79a"));
              assertThat(registrationOverrides.getTimestamp()).hasValue(UInt64.valueOf(1235));
            });
  }

  private void validateContentWithRegistrationOverrides2(final ProposerConfig config) {
    final Optional<RegistrationOverrides> pubKeyRegistrationOverrides =
        config
            .getConfigForPubKey(
                "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a")
            .flatMap(this::getRegistrationOverrides);
    assertThat(pubKeyRegistrationOverrides)
        .hasValueSatisfying(
            registrationOverrides -> {
              assertThat(registrationOverrides.getPublicKey())
                  .hasValue(
                      BLSPublicKey.fromHexString(
                          "0xb53d21a4cfd562c469cc81514d4ce5a6b577d8403d32a394dc265dd190b47fa9f829fdd7963afdf972e5e77854051f6f"));
              assertThat(registrationOverrides.getTimestamp()).isEmpty();
            });
    final Optional<RegistrationOverrides> defaultRegistrationOverrides =
        getRegistrationOverrides(config.getDefaultConfig());
    assertThat(defaultRegistrationOverrides).isEmpty();
  }

  private Optional<RegistrationOverrides> getRegistrationOverrides(final Config config) {
    return config.getBuilder().flatMap(BuilderConfig::getRegistrationOverrides);
  }
}
