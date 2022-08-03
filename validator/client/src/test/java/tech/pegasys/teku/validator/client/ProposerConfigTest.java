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
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.validator.client.ProposerConfig.BuilderConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;

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
  private static final UInt64 CUSTOM_GAS_LIMIT = UInt64.valueOf(28_000_000);

  private static final Config DEFAULT_CONFIG =
      new Config(ETH1_ADDRESS, new BuilderConfig(false, DEFAULT_GAS_LIMIT));

  @Test
  void gets_isValidatorRegistrationEnabled_forPubKey() {
    Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(
        PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, CUSTOM_GAS_LIMIT)));
    ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

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
    ProposerConfig proposerConfig = new ProposerConfig(new HashMap<>(), NO_BUILDER_CONFIG);

    assertThat(proposerConfig.isBuilderEnabledForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void gets_validatorRegistrationGasLimit_forPubKey() {
    Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(
        PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, CUSTOM_GAS_LIMIT)));
    ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

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
    ProposerConfig proposerConfig = new ProposerConfig(new HashMap<>(), NO_BUILDER_CONFIG);

    assertThat(
            proposerConfig.getBuilderGasLimitForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void registrationGasLimitDoesNotExist_whenGasLimitIsNull() {
    Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, null)));
    ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    assertThat(
            proposerConfig.getBuilderGasLimitForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void gets_validatorRegistrationPublicKey_forPubKey() {
    Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(
        PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, null, new RegistrationOverridesConfig(CUSTOM_PUBLIC_KEY)));
    ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    assertThat(
            proposerConfig.getBuilderPublicKeyForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .hasValue(CUSTOM_PUBLIC_KEY);

    // defaults to default config
    assertThat(
            proposerConfig.getBuilderPublicKeyForPubKey(
                BLSPublicKey.fromBytesCompressed(ANOTHER_PUB_KEY)))
        .hasValue(DEFAULT_PUBLIC_KEY);
  }

  @Test
  void registrationPublicKeyDoesNotExist_whenRegistrationIsNull() {
    ProposerConfig proposerConfig = new ProposerConfig(new HashMap<>(), NO_BUILDER_CONFIG);

    assertThat(
            proposerConfig.getBuilderPublicKeyForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }

  @Test
  void registrationPublicKeyDoesNotExist_whenPublicKeyIsNull() {
    Map<Bytes48, Config> configByPubKey = new HashMap<>();
    configByPubKey.put(PUB_KEY, new Config(ETH1_ADDRESS, new BuilderConfig(true, null, new RegistrationOverridesConfig(null))));
    ProposerConfig proposerConfig = new ProposerConfig(configByPubKey, DEFAULT_CONFIG);

    assertThat(
            proposerConfig.getBuilderPublicKeyForPubKey(BLSPublicKey.fromBytesCompressed(PUB_KEY)))
        .isEmpty();
  }
}
