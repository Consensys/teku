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
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.P2PConfig.P2PConfigBuilder;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.config.GlobalConfigurationBuilder;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientConfiguration;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class TekuConfiguration {
  private final GlobalConfiguration globalConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;
  private final DataConfig dataConfig;
  private final BeaconChainConfiguration beaconChainConfig;
  private final ValidatorClientConfiguration validatorClientConfig;

  private TekuConfiguration(
      GlobalConfiguration globalConfiguration,
      WeakSubjectivityConfig weakSubjectivityConfig,
      final ValidatorConfig validatorConfig,
      final DataConfig dataConfig,
      final P2PConfig p2pConfig,
      final BeaconRestApiConfig beaconRestApiConfig) {
    this.globalConfiguration = globalConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
    this.dataConfig = dataConfig;
    this.beaconChainConfig =
        new BeaconChainConfiguration(
            weakSubjectivityConfig, validatorConfig, p2pConfig, beaconRestApiConfig);
    this.validatorClientConfig =
        new ValidatorClientConfiguration(globalConfiguration, validatorConfig, dataConfig);
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

  public DataConfig dataConfig() {
    return dataConfig;
  }

  public void validate() {
    globalConfiguration.validate();
  }

  public static class Builder {
    private final GlobalConfigurationBuilder globalConfigurationBuilder =
        new GlobalConfigurationBuilder();
    private final WeakSubjectivityConfig.Builder weakSubjectivityBuilder =
        WeakSubjectivityConfig.builder();
    private final ValidatorConfig.Builder validatorConfigBuilder = ValidatorConfig.builder();
    private final DataConfig.Builder dataConfigBuilder = DataConfig.builder();
    private final P2PConfigBuilder p2pConfigBuilder = P2PConfig.builder();
    private final BeaconRestApiConfig.BeaconRestApiConfigBuilder restApiBuilder =
        BeaconRestApiConfig.builder();

    private Builder() {}

    public TekuConfiguration build() {
      return new TekuConfiguration(
          globalConfigurationBuilder.build(),
          weakSubjectivityBuilder.build(),
          validatorConfigBuilder.build(),
          dataConfigBuilder.build(),
          p2pConfigBuilder.build(),
          restApiBuilder.build());
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

    public Builder data(final Consumer<DataConfig.Builder> dataConfigConsumer) {
      dataConfigConsumer.accept(dataConfigBuilder);
      return this;
    }

    public Builder p2p(final Consumer<P2PConfigBuilder> p2pConfigConsumer) {
      p2pConfigConsumer.accept(p2pConfigBuilder);
      return this;
    }

    public Builder restApi(
        final Consumer<BeaconRestApiConfig.BeaconRestApiConfigBuilder>
            beaconRestApiConfigConsumer) {
      beaconRestApiConfigConsumer.accept(restApiBuilder);
      return this;
    }
  }
}
