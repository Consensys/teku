/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.config;

import java.util.function.Consumer;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.config.GlobalConfigurationBuilder;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientConfiguration;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class TekuConfiguration {
  private final GlobalConfiguration globalConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;
  private final BeaconChainConfiguration beaconChainConfig;
  private final ValidatorClientConfiguration validatorClientConfig;

  private TekuConfiguration(
      GlobalConfiguration globalConfiguration,
      WeakSubjectivityConfig weakSubjectivityConfig,
      final ValidatorConfig validatorConfig) {
    this.globalConfiguration = globalConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
    this.beaconChainConfig = new BeaconChainConfiguration(weakSubjectivityConfig, validatorConfig);
    this.validatorClientConfig =
        new ValidatorClientConfiguration(globalConfiguration, validatorConfig);
  }

  public static Builder builder() {
    return new Builder();
  }

  public GlobalConfiguration global() {
    return globalConfiguration;
  }

  public WeakSubjectivityConfig weakSubjectivity() {
    return weakSubjectivityConfig;
  }

  public BeaconChainConfiguration beaconChain() {
    return beaconChainConfig;
  }

  public ValidatorClientConfiguration validatorClient() {
    return validatorClientConfig;
  }

  public void validate() {
    globalConfiguration.validate();
  }

  public static class Builder {
    private GlobalConfigurationBuilder globalConfigurationBuilder =
        new GlobalConfigurationBuilder();
    private WeakSubjectivityConfig.Builder weakSubjectivityBuilder =
        WeakSubjectivityConfig.builder();
    private ValidatorConfig.Builder validatorConfigBuilder = ValidatorConfig.builder();

    private Builder() {}

    public TekuConfiguration build() {
      return new TekuConfiguration(
          globalConfigurationBuilder.build(),
          weakSubjectivityBuilder.build(),
          validatorConfigBuilder.build());
    }

    public Builder globalConfig(final Consumer<GlobalConfigurationBuilder> globalConfigConsumer) {
      globalConfigConsumer.accept(globalConfigurationBuilder);
      return this;
    }

    public Builder weakSubjectivity(
        final Consumer<WeakSubjectivityConfig.Builder> wsConfigConsumer) {
      wsConfigConsumer.accept(weakSubjectivityBuilder);
      return this;
    }

    public Builder validator(final Consumer<ValidatorConfig.Builder> validatorConfigConsumer) {
      validatorConfigConsumer.accept(validatorConfigBuilder);
      return this;
    }
  }
}
