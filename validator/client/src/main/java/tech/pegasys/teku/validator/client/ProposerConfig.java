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

  public Optional<Boolean> isValidatorRegistrationEnabledForPubKey(final BLSPublicKey pubKey) {
    return getConfigForPubKeyOrDefault(pubKey)
        .getValidatorRegistration()
        .map(ValidatorRegistration::isEnabled);
  }

  public Optional<UInt64> getValidatorRegistrationGasLimitForPubKey(final BLSPublicKey pubKey) {
    return getConfigForPubKeyOrDefault(pubKey)
        .getValidatorRegistration()
        .flatMap(ValidatorRegistration::getGasLimit);
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

    @JsonProperty(value = "builder_registration")
    private ValidatorRegistration validatorRegistration;

    @JsonCreator
    public Config(
        @JsonProperty(value = "fee_recipient") final Eth1Address feeRecipient,
        @JsonProperty(value = "builder_registration")
            final ValidatorRegistration validatorRegistration) {
      checkNotNull(feeRecipient, "fee_recipient is required");
      this.feeRecipient = feeRecipient;
      this.validatorRegistration = validatorRegistration;
    }

    public Eth1Address getFeeRecipient() {
      return feeRecipient;
    }

    public Optional<ValidatorRegistration> getValidatorRegistration() {
      return Optional.ofNullable(validatorRegistration);
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
          && Objects.equals(validatorRegistration, that.validatorRegistration);
    }

    @Override
    public int hashCode() {
      return Objects.hash(feeRecipient, validatorRegistration);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ValidatorRegistration {
    @JsonProperty(value = "enabled")
    private Boolean enabled;

    @JsonProperty(value = "gas_limit")
    private UInt64 gasLimit;

    @JsonCreator
    public ValidatorRegistration(
        @JsonProperty(value = "enabled") final Boolean enabled,
        @JsonProperty(value = "gas_limit") final UInt64 gasLimit) {
      checkNotNull(enabled, "enabled is required");
      this.enabled = enabled;
      this.gasLimit = gasLimit;
    }

    public Boolean isEnabled() {
      return enabled;
    }

    public Optional<UInt64> getGasLimit() {
      return Optional.ofNullable(gasLimit);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ValidatorRegistration that = (ValidatorRegistration) o;
      return Objects.equals(enabled, that.enabled) && Objects.equals(gasLimit, that.gasLimit);
    }

    @Override
    public int hashCode() {
      return Objects.hash(enabled, gasLimit);
    }
  }
}
