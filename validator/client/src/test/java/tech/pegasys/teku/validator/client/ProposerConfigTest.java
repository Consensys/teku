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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.ProposerConfig.BuilderConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;
import tech.pegasys.teku.validator.client.ProposerConfig.RegistrationOverrides;

class ProposerConfigTest {

  private static final Eth1Address ETH1_ADDRESS =
      Eth1Address.fromHexString("0x50155530FCE8a85ec7055A5F8b2bE214B3DaeFd3");
  private static final Bytes48 PUB_KEY =
      Bytes48.fromHexString(
          "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
  private static final Bytes48 ANOTHER_PUB_KEY =
      Bytes48.fromHexString(
          "0x89ece308f9d1f0131765212deca99697b112d61f9be9a5f1f3780a51335b3ff981747a0b2ca2179b96d2c0c9024e5224");

  private static final Config NO_BUILDER_CONFIG = new Config(ETH1_ADDRESS, null);

  private static final UInt64 DEFAULT_GAS_LIMIT = UInt64.valueOf(30_000_000);
  private static final UInt64 DEFAULT_TIMESTAMP_OVERRIDE = UInt64.valueOf(293353);
  private static final BLSPublicKey DEFAULT_PUBLIC_KEY_OVERRIDE =
      BLSPublicKey.fromHexString(
          "0xb53d21a4cfd562c469cc81514d4ce5a6b577d8403d32a394dc265dd190b47fa9f829fdd7963afdf972e5e77854051f6f");

  private static final UInt64 CUSTOM_GAS_LIMIT = UInt64.valueOf(28_000_000);
  private static final UInt64 TIMESTAMP_OVERRIDE = UInt64.valueOf(293356);
  private static final BLSPublicKey PUBLIC_KEY_OVERRIDE =
      BLSPublicKey.fromHexString(
          "0xa491d1b0ecd9bb917989f0e74f0dea0422eac4a873e5e2644f368dffb9a6e20fd6e10c1b77654d067c0618f6e5a7f79a");

  private static final Config DEFAULT_CONFIG =
      new Config(
          ETH1_ADDRESS,
          new BuilderConfig(
              false,
              DEFAULT_GAS_LIMIT,
              new RegistrationOverrides(DEFAULT_TIMESTAMP_OVERRIDE, DEFAULT_PUBLIC_KEY_OVERRIDE)));

  @Test
  void gets_isValidatorRegistrationEnabled_forPubKey() {
    final Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(
        PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, CUSTOM_GAS_LIMIT, null)));
    final ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    assertThat(proposerConfig.isBuilderEnabledForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .hasValue(true);

    // defaults to default config
    assertThat(
            proposerConfig.isBuilderEnabledForPubKey(
                BLSPublicKey.fromBytesCompressed(ANOTHER_PUB_KEY)))
        .hasValue(false);
  }

  @Test
  void registrationIsNotEnabledDoesNotExist_whenRegistrationIsNull() {
    final ProposerConfig proposerConfig = new ProposerConfig(new HashMap<>(), NO_BUILDER_CONFIG);

    assertThat(proposerConfig.isBuilderEnabledForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void gets_validatorRegistrationGasLimit_forPubKey() {
    final Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(
        PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, CUSTOM_GAS_LIMIT, null)));
    final ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    assertThat(
            proposerConfig.getBuilderGasLimitForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .hasValue(CUSTOM_GAS_LIMIT);

    // defaults to default config
    assertThat(
            proposerConfig.getBuilderGasLimitForPubKey(
                BLSPublicKey.fromBytesCompressed(ANOTHER_PUB_KEY)))
        .hasValue(DEFAULT_GAS_LIMIT);
  }

  @Test
  void registrationGasLimitDoesNotExist_whenRegistrationIsNull() {
    final ProposerConfig proposerConfig = new ProposerConfig(new HashMap<>(), NO_BUILDER_CONFIG);

    assertThat(
            proposerConfig.getBuilderGasLimitForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void registrationGasLimitDoesNotExist_whenGasLimitIsNull() {
    final Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, null, null)));
    final ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    assertThat(
            proposerConfig.getBuilderGasLimitForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void gets_validatorRegistrationOverrides_forPubKey() {
    final Map<Bytes48, Config> configByPubKey = new HashMap<>();

    configByPubKey.put(
        PUB_KEY,
        new Config(
            ETH1_ADDRESS,
            new BuilderConfig(
                true,
                CUSTOM_GAS_LIMIT,
                new RegistrationOverrides(TIMESTAMP_OVERRIDE, PUBLIC_KEY_OVERRIDE))));

    final ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    final Optional<RegistrationOverrides> registrationOverrides =
        proposerConfig.getBuilderRegistrationOverrides(BLSPublicKey.fromBytesCompressed(PUB_KEY));

    assertThat(registrationOverrides)
        .hasValueSatisfying(
            overrides -> {
              assertThat(overrides.getTimestamp()).hasValue(TIMESTAMP_OVERRIDE);
              assertThat(overrides.getPublicKey()).hasValue(PUBLIC_KEY_OVERRIDE);
            });

    // defaults to default config
    final Optional<RegistrationOverrides> defaultRegistrationOverrides =
        proposerConfig.getBuilderRegistrationOverrides(
            BLSPublicKey.fromBytesCompressed(ANOTHER_PUB_KEY));

    assertThat(defaultRegistrationOverrides)
        .hasValueSatisfying(
            overrides -> {
              assertThat(overrides.getTimestamp()).hasValue(DEFAULT_TIMESTAMP_OVERRIDE);
              assertThat(overrides.getPublicKey()).hasValue(DEFAULT_PUBLIC_KEY_OVERRIDE);
            });
  }

  @Test
  void registrationOverridesDoesNotExist_whenRegistrationOverridesIsNull() {
    final ProposerConfig proposerConfig = new ProposerConfig(new HashMap<>(), NO_BUILDER_CONFIG);

    assertThat(
            proposerConfig.getBuilderRegistrationOverrides(
                BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void registrationOverridesValuesDoNotExist_whenRegistrationOverridesHasNullValues() {
    final Map<Bytes48, Config> configByPubKey = new HashMap<>();

    configByPubKey.put(
        PUB_KEY,
        new Config(
            ETH1_ADDRESS,
            new BuilderConfig(true, CUSTOM_GAS_LIMIT, new RegistrationOverrides(null, null))));

    final ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    final Optional<RegistrationOverrides> registrationOverrides =
        proposerConfig.getBuilderRegistrationOverrides(BLSPublicKey.fromBytesCompressed(PUB_KEY));

    assertThat(registrationOverrides)
        .hasValueSatisfying(
            overrides -> {
              assertThat(overrides.getTimestamp()).isEmpty();
              assertThat(overrides.getPublicKey()).isEmpty();
            });
  }
}
