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
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig.LoggingConfigBuilder;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.P2PConfig.P2PConfigBuilder;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.chainstorage.StorageConfiguration;
import tech.pegasys.teku.util.config.GlobalConfiguration;
import tech.pegasys.teku.util.config.GlobalConfigurationBuilder;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.InteropConfig.InteropConfigBuilder;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientConfiguration;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class TekuConfiguration {
  private final GlobalConfiguration globalConfiguration;
  private final Eth2NetworkConfiguration eth2NetworkConfiguration;
  private final StorageConfiguration storageConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;
  private final DataConfig dataConfig;
  private final LoggingConfig loggingConfig;
  private final BeaconChainConfiguration beaconChainConfig;
  private final ValidatorClientConfiguration validatorClientConfig;

  private TekuConfiguration(
      GlobalConfiguration globalConfiguration,
      Eth2NetworkConfiguration eth2NetworkConfiguration,
      StorageConfiguration storageConfiguration,
      WeakSubjectivityConfig weakSubjectivityConfig,
      final ValidatorConfig validatorConfig,
      final InteropConfig interopConfig,
      final DataConfig dataConfig,
      final P2PConfig p2pConfig,
      final BeaconRestApiConfig beaconRestApiConfig,
      final LoggingConfig loggingConfig) {
    this.globalConfiguration = globalConfiguration;
    this.eth2NetworkConfiguration = eth2NetworkConfiguration;
    this.storageConfiguration = storageConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
    this.dataConfig = dataConfig;
    this.loggingConfig = loggingConfig;
    this.beaconChainConfig =
        new BeaconChainConfiguration(
            weakSubjectivityConfig,
            validatorConfig,
            interopConfig,
            p2pConfig,
            beaconRestApiConfig,
            loggingConfig);
    this.validatorClientConfig =
        new ValidatorClientConfiguration(globalConfiguration, validatorConfig, interopConfig);
  }

  public static Builder builder() {
    return new Builder();
  }

  public GlobalConfiguration global() {
    return globalConfiguration;
  }

  public Eth2NetworkConfiguration eth2NetworkConfiguration() {
    return eth2NetworkConfiguration;
  }

  public StorageConfiguration storageConfiguration() {
    return storageConfiguration;
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

  public LoggingConfig loggingConfig() {
    return loggingConfig;
  }

  public static class Builder {
    private final GlobalConfigurationBuilder globalConfigurationBuilder =
        new GlobalConfigurationBuilder();
    private final SettableBuilder<Eth2NetworkConfiguration.Builder, Eth2NetworkConfiguration>
        eth2NetworkConfigurationBuilder =
            new SettableBuilder<>(
                Eth2NetworkConfiguration.builder(Eth2NetworkConfiguration.MAINNET),
                Eth2NetworkConfiguration.Builder::build);
    private final StorageConfiguration.Builder storageConfigurationBuilder =
        StorageConfiguration.builder();
    private final WeakSubjectivityConfig.Builder weakSubjectivityBuilder =
        WeakSubjectivityConfig.builder();
    private final ValidatorConfig.Builder validatorConfigBuilder = ValidatorConfig.builder();
    private final InteropConfig.InteropConfigBuilder interopConfigBuilder = InteropConfig.builder();
    private final DataConfig.Builder dataConfigBuilder = DataConfig.builder();
    private final P2PConfigBuilder p2pConfigBuilder = P2PConfig.builder();
    private final BeaconRestApiConfig.BeaconRestApiConfigBuilder restApiBuilder =
        BeaconRestApiConfig.builder();
    private final LoggingConfig.LoggingConfigBuilder loggingConfigBuilder = LoggingConfig.builder();

    private Builder() {}

    public TekuConfiguration build() {
      final GlobalConfiguration globalConfig = globalConfigurationBuilder.build();
      final Eth2NetworkConfiguration eth2NetworkConfig = eth2NetworkConfigurationBuilder.build();
      final StorageConfiguration storageConfig = storageConfigurationBuilder.build();
      final WeakSubjectivityConfig wsConfig = weakSubjectivityBuilder.build();
      final ValidatorConfig validatorConfig = validatorConfigBuilder.build();
      final InteropConfig interopConfig = interopConfigBuilder.build();
      final DataConfig dataConfig = dataConfigBuilder.build();
      final P2PConfig p2pConfig = p2pConfigBuilder.build();
      final BeaconRestApiConfig restApiConfig = restApiBuilder.build();
      final LoggingConfig loggingConfig = loggingConfigBuilder.build();

      // Validate consistency between config objects
      if (globalConfig.getEth1Endpoint() != null
          && eth2NetworkConfig.getEth1DepositContractAddress().isEmpty()) {
        throw new IllegalArgumentException(
            "Eth1 deposit contract address is required if an eth1 endpoint is specified.");
      }

      return new TekuConfiguration(
          globalConfig,
          eth2NetworkConfig,
          storageConfig,
          wsConfig,
          validatorConfig,
          interopConfig,
          dataConfig,
          p2pConfig,
          restApiConfig,
          loggingConfig);
    }

    public Builder globalConfig(final Consumer<GlobalConfigurationBuilder> globalConfigConsumer) {
      globalConfigConsumer.accept(globalConfigurationBuilder);
      return this;
    }

    public Builder eth2NetworkConfig(final Consumer<Eth2NetworkConfiguration.Builder> consumer) {
      eth2NetworkConfigurationBuilder.configure(consumer);
      return this;
    }

    public Builder eth2NetworkConfig(final Eth2NetworkConfiguration.Builder eth2Builder) {
      eth2NetworkConfigurationBuilder.reset(eth2Builder);
      return this;
    }

    public Builder storageConfiguration(final Consumer<StorageConfiguration.Builder> consumer) {
      consumer.accept(storageConfigurationBuilder);
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

    public Builder interop(final Consumer<InteropConfigBuilder> interopConfigBuilderConsumer) {
      interopConfigBuilderConsumer.accept(interopConfigBuilder);
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

    public Builder logging(final Consumer<LoggingConfigBuilder> loggingConfigBuilderConsumer) {
      loggingConfigBuilderConsumer.accept(loggingConfigBuilder);
      return this;
    }
  }
}
