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
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig;
import tech.pegasys.teku.infrastructure.metrics.MetricsConfig.MetricsConfigBuilder;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.P2PConfig.P2PConfigBuilder;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.chainstorage.StorageConfiguration;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.InteropConfig.InteropConfigBuilder;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.ValidatorClientConfiguration;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class TekuConfiguration {
  private final Eth2NetworkConfiguration eth2NetworkConfiguration;
  private final StorageConfiguration storageConfiguration;
  private final WeakSubjectivityConfig weakSubjectivityConfig;
  private final DataConfig dataConfig;
  private final LoggingConfig loggingConfig;
  private final MetricsConfig metricsConfig;
  private final BeaconChainConfiguration beaconChainConfig;
  private final ValidatorClientConfiguration validatorClientConfig;
  private final PowchainConfiguration powchainConfiguration;

  private TekuConfiguration(
      final Eth2NetworkConfiguration eth2NetworkConfiguration,
      final StorageConfiguration storageConfiguration,
      final WeakSubjectivityConfig weakSubjectivityConfig,
      final ValidatorConfig validatorConfig,
      final PowchainConfiguration powchainConfiguration,
      final InteropConfig interopConfig,
      final DataConfig dataConfig,
      final P2PConfig p2pConfig,
      final BeaconRestApiConfig beaconRestApiConfig,
      final LoggingConfig loggingConfig,
      final MetricsConfig metricsConfig,
      final StoreConfig storeConfig) {
    this.eth2NetworkConfiguration = eth2NetworkConfiguration;
    this.storageConfiguration = storageConfiguration;
    this.weakSubjectivityConfig = weakSubjectivityConfig;
    this.powchainConfiguration = powchainConfiguration;
    this.dataConfig = dataConfig;
    this.loggingConfig = loggingConfig;
    this.metricsConfig = metricsConfig;
    this.beaconChainConfig =
        new BeaconChainConfiguration(
            eth2NetworkConfiguration,
            weakSubjectivityConfig,
            validatorConfig,
            interopConfig,
            p2pConfig,
            beaconRestApiConfig,
            powchainConfiguration,
            loggingConfig,
            storeConfig);
    this.validatorClientConfig = new ValidatorClientConfiguration(validatorConfig, interopConfig);
  }

  public static Builder builder() {
    return new Builder();
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

  public PowchainConfiguration powchain() {
    return powchainConfiguration;
  }

  public DataConfig dataConfig() {
    return dataConfig;
  }

  public LoggingConfig loggingConfig() {
    return loggingConfig;
  }

  public MetricsConfig metricsConfig() {
    return metricsConfig;
  }

  public static class Builder {
    private final Eth2NetworkConfiguration.Builder eth2NetworkConfigurationBuilder =
        Eth2NetworkConfiguration.builder().applyMainnetNetworkDefaults();
    private final StorageConfiguration.Builder storageConfigurationBuilder =
        StorageConfiguration.builder();
    private final WeakSubjectivityConfig.Builder weakSubjectivityBuilder =
        WeakSubjectivityConfig.builder();
    private final ValidatorConfig.Builder validatorConfigBuilder = ValidatorConfig.builder();
    private final PowchainConfiguration.Builder powchainConfigBuilder =
        PowchainConfiguration.builder();
    private final InteropConfig.InteropConfigBuilder interopConfigBuilder = InteropConfig.builder();
    private final DataConfig.Builder dataConfigBuilder = DataConfig.builder();
    private final P2PConfigBuilder p2pConfigBuilder = P2PConfig.builder();
    private final BeaconRestApiConfig.BeaconRestApiConfigBuilder restApiBuilder =
        BeaconRestApiConfig.builder();
    private final LoggingConfig.LoggingConfigBuilder loggingConfigBuilder = LoggingConfig.builder();
    private final MetricsConfig.MetricsConfigBuilder metricsConfigBuilder = MetricsConfig.builder();
    private final StoreConfig.Builder storeConfigBuilder = StoreConfig.builder();

    private Builder() {}

    public TekuConfiguration build() {
      return new TekuConfiguration(
          eth2NetworkConfigurationBuilder.build(),
          storageConfigurationBuilder.build(),
          weakSubjectivityBuilder.build(),
          validatorConfigBuilder.build(),
          powchainConfigBuilder.build(),
          interopConfigBuilder.build(),
          dataConfigBuilder.build(),
          p2pConfigBuilder.build(),
          restApiBuilder.build(),
          loggingConfigBuilder.build(),
          metricsConfigBuilder.build(),
          storeConfigBuilder.build());
    }

    public Builder eth2NetworkConfig(final Consumer<Eth2NetworkConfiguration.Builder> consumer) {
      consumer.accept(eth2NetworkConfigurationBuilder);
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

    public Builder powchain(final Consumer<PowchainConfiguration.Builder> consumer) {
      consumer.accept(powchainConfigBuilder);
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

    public Builder metrics(final Consumer<MetricsConfigBuilder> metricsConfigBuilderConsumer) {
      metricsConfigBuilderConsumer.accept(metricsConfigBuilder);
      return this;
    }

    public Builder store(final Consumer<StoreConfig.Builder> storeConfigBuilderConsumer) {
      storeConfigBuilderConsumer.accept(storeConfigBuilder);
      return this;
    }
  }
}
