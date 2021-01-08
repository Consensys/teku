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

package tech.pegasys.teku.util.config;

import java.util.List;

/**
 * @deprecated - Use TekuConfigurationBuilder where possible. Global application configuration
 *     builder.
 */
@Deprecated
public class GlobalConfigurationBuilder {

  private Integer peerRateLimit;
  private Integer peerRequestLimit;
  private int eth1LogsMaxBlockRange;
  private boolean eth1DepositsFromStorageEnabled;
  private String transitionRecordDirectory;
  private boolean metricsEnabled;
  private int metricsPort;
  private String metricsInterface;
  private List<String> metricsCategories;
  private List<String> metricsHostAllowlist;
  private StateStorageMode dataStorageMode;
  private String dataStorageCreateDbVersion;
  private int hotStatePersistenceFrequencyInEpochs;
  private boolean isBlockProcessingAtStartupDisabled;
  private long dataStorageFrequency;

  public GlobalConfigurationBuilder setPeerRateLimit(final Integer peerRateLimit) {
    this.peerRateLimit = peerRateLimit;
    return this;
  }

  public GlobalConfigurationBuilder setPeerRequestLimit(final Integer peerRequestLimit) {
    this.peerRequestLimit = peerRequestLimit;
    return this;
  }

  public GlobalConfigurationBuilder setEth1LogsMaxBlockRange(final int eth1LogsMaxBlockRange) {
    this.eth1LogsMaxBlockRange = eth1LogsMaxBlockRange;
    return this;
  }

  public GlobalConfigurationBuilder setEth1DepositsFromStorageEnabled(
      final boolean eth1DepositsFromStorageEnabled) {
    this.eth1DepositsFromStorageEnabled = eth1DepositsFromStorageEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setTransitionRecordDirectory(
      final String transitionRecordDirectory) {
    this.transitionRecordDirectory = transitionRecordDirectory;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsEnabled(final boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsPort(final int metricsPort) {
    this.metricsPort = metricsPort;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsInterface(final String metricsInterface) {
    this.metricsInterface = metricsInterface;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsCategories(final List<String> metricsCategories) {
    this.metricsCategories = metricsCategories;
    return this;
  }

  public GlobalConfigurationBuilder setMetricsHostAllowlist(
      final List<String> metricsHostAllowlist) {
    this.metricsHostAllowlist = metricsHostAllowlist;
    return this;
  }

  public GlobalConfigurationBuilder setDataStorageMode(final StateStorageMode dataStorageMode) {
    this.dataStorageMode = dataStorageMode;
    return this;
  }

  public GlobalConfigurationBuilder setDataStorageFrequency(final long dataStorageFrequency) {
    this.dataStorageFrequency = dataStorageFrequency;
    return this;
  }

  public GlobalConfigurationBuilder setDataStorageCreateDbVersion(
      final String dataStorageCreateDbVersion) {
    this.dataStorageCreateDbVersion = dataStorageCreateDbVersion;
    return this;
  }

  public GlobalConfigurationBuilder setHotStatePersistenceFrequencyInEpochs(
      final int hotStatePersistenceFrequencyInEpochs) {
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    return this;
  }

  public GlobalConfigurationBuilder setIsBlockProcessingAtStartupDisabled(
      final boolean isDisabled) {
    this.isBlockProcessingAtStartupDisabled = isDisabled;
    return this;
  }

  public GlobalConfiguration build() {

    return new GlobalConfiguration(
        peerRateLimit,
        peerRequestLimit,
        eth1LogsMaxBlockRange,
        eth1DepositsFromStorageEnabled,
        transitionRecordDirectory,
        metricsEnabled,
        metricsPort,
        metricsInterface,
        metricsCategories,
        metricsHostAllowlist,
        dataStorageMode,
        dataStorageFrequency,
        dataStorageCreateDbVersion,
        hotStatePersistenceFrequencyInEpochs,
        isBlockProcessingAtStartupDisabled);
  }
}
