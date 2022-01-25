/*
 * Copyright 2022 ConsenSys AG.
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
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;

public class ProposerConfig {
  @JsonProperty(value = "proposer_config")
  private Map<Bytes48, Config> proposerConfig;

  @JsonProperty(value = "default_config")
  private Config defaultConfig;

  @JsonCreator
  ProposerConfig(
      @JsonProperty(value = "proposer_config") final Map<Bytes48, Config> proposerConfig,
      @JsonProperty(value = "default_config") final Config defaultConfig) {
    checkNotNull(proposerConfig, "proposer_config is required");
    checkNotNull(defaultConfig, "default_config is required");
    this.proposerConfig = proposerConfig;
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

  public Optional<Config> getDefaultConfig() {
    return Optional.ofNullable(defaultConfig);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Config {
    @JsonProperty(value = "fee_recipient")
    private Bytes20 feeRecipient;

    @JsonCreator
    Config(@JsonProperty(value = "fee_recipient") final Bytes20 feeRecipient) {
      checkNotNull(feeRecipient, "fee_recipient is required");
      this.feeRecipient = feeRecipient;
    }

    public Bytes20 getFeeRecipient() {
      return feeRecipient;
    }
  }
}
