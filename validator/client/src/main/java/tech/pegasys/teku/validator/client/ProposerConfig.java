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

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class ProposerConfig {
  @JsonProperty(value = "proposer_config")
  private Map<Bytes48, Config> proposerConfig;

  @JsonProperty(value = "default_config")
  private Config defaultConfig;

  @JsonCreator
  public ProposerConfig(
      @JsonProperty(value = "proposer_config") final Map<Bytes48, Config> proposerConfig,
      @JsonProperty(value = "default_config") final Config defaultConfig) {
    checkNotNull(defaultConfig, "default_config is required");
    checkNotNull(defaultConfig.feeRecipient, "fee_recipient is required in default_config");
    this.proposerConfig = proposerConfig == null ? ImmutableMap.of() : proposerConfig;
    this.defaultConfig = defaultConfig;
  }

  public Optional<Config> getConfigForPubKey(final String pubKey) {
    return getConfigForPubKey(Bytes48.fromHexString(pubKey));
  }

  public Optional<Config> getConfigForPubKey(final BLSPublicKey pubKey) {
    return getConfigForPubKey(pubKey.toBytesCompressed());
  }

  public Optional<Config> getConfigForPubKey(final Bytes48 pubKey) {
    return Optional.ofNullable(proposerConfig.get(pubKey));
  }

  public Config getConfigForPubKeyOrDefault(final BLSPublicKey pubKey) {
    return getConfigForPubKey(pubKey).orElse(defaultConfig);
  }

  public Optional<Boolean> isBuilderEnabledForPubKey(final BLSPublicKey pubKey) {
    return getConfigForPubKeyOrDefault(pubKey).getBuilder().map(BuilderConfig::isEnabled);
  }

  public Optional<UInt64> getBuilderGasLimitForPubKey(final BLSPublicKey pubKey) {
    return getConfigForPubKeyOrDefault(pubKey).getBuilder().flatMap(BuilderConfig::getGasLimit);
  }

  public Optional<BLSPublicKey> getBuilderPublicKeyForPubKey(final BLSPublicKey pubKey) {
    return getConfigForPubKeyOrDefault(pubKey).getBuilder().getRegistrationOverrides().flatMap(RegistrationOverridesConfig::getPublicKey);
  }

  public Config getDefaultConfig() {
    return defaultConfig;
  }

  public int getNumberOfProposerConfigs() {
    return proposerConfig.size();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProposerConfig that = (ProposerConfig) o;
    return Objects.equals(proposerConfig, that.proposerConfig)
        && Objects.equals(defaultConfig, that.defaultConfig);
  }

  @Override
  public int hashCode() {
    return Objects.hash(proposerConfig, defaultConfig);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Config {
    @JsonProperty(value = "fee_recipient")
    private Eth1Address feeRecipient;

    @JsonProperty(value = "builder")
    private BuilderConfig builder;

    @JsonCreator
    public Config(
        @JsonProperty(value = "fee_recipient") final Eth1Address feeRecipient,
        @JsonProperty(value = "builder") final BuilderConfig builder) {
      this.feeRecipient = feeRecipient;
      this.builder = builder;
    }

    public Optional<Eth1Address> getFeeRecipient() {
      return Optional.ofNullable(feeRecipient);
    }

    public Optional<BuilderConfig> getBuilder() {
      return Optional.ofNullable(builder);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Config that = (Config) o;
      return Objects.equals(feeRecipient, that.feeRecipient)
          && Objects.equals(builder, that.builder);
    }

    @Override
    public int hashCode() {
      return Objects.hash(feeRecipient, builder);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BuilderConfig {
    @JsonProperty(value = "enabled")
    private Boolean enabled;

    @JsonProperty(value = "gas_limit")
    private UInt64 gasLimit;

    @JsonProperty(value = "registration_overrides")
    private RegistrationOverridesConfig registrationOverrides;

    @JsonCreator
    public BuilderConfig(
        @JsonProperty(value = "enabled") final Boolean enabled,
        @JsonProperty(value = "gas_limit") final UInt64 gasLimit 
        @JsonProperty(value = "registration_overrides") final RegistrationOverridesConfig registrationOverrides) {
      checkNotNull(enabled, "enabled is required");
      this.enabled = enabled;
      this.gasLimit = gasLimit;
      this.registrationOverrides = registrationOverrides;
    }

    public Boolean isEnabled() {
      return enabled;
    }

    public Optional<UInt64> getGasLimit() {
      return Optional.ofNullable(gasLimit);
    }

    public Optional<RegistrationOverridesConfig> getRegistrationOverrides() {
      return Optional.ofNullable(registrationOverrides);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BuilderConfig that = (BuilderConfig) o;
      return Objects.equals(enabled, that.enabled) && Objects.equals(gasLimit, that.gasLimit) && Objects.equals(registrationOverrides, that.registrationOverrides);
    }

    @Override
    public int hashCode() {
      return Objects.hash(enabled, gasLimit, registrationOverrides);
    }
  }

  @JsonCreator
  public RegistrationOverridesConfig(
      @JsonProperty(value = "public_key") final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
  }

  public Optional<BLSPublicKey> getPublicKey() {
    return Optional.ofNullable(publicKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RegistrationOverridesConfig that = (RegistrationOverridesConfig) o;
    return Objects.equals(publicKey, that.publicKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey);
  }
}
