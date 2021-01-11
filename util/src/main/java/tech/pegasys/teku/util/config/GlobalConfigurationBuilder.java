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

/**
 * @deprecated - Use TekuConfigurationBuilder where possible. Global application configuration
 *     builder.
 */
@Deprecated
public class GlobalConfigurationBuilder {

  private Integer peerRateLimit;
  private Integer peerRequestLimit;
  private int eth1LogsMaxBlockRange;
  private int hotStatePersistenceFrequencyInEpochs;

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

  public GlobalConfigurationBuilder setHotStatePersistenceFrequencyInEpochs(
      final int hotStatePersistenceFrequencyInEpochs) {
    this.hotStatePersistenceFrequencyInEpochs = hotStatePersistenceFrequencyInEpochs;
    return this;
  }

  public GlobalConfiguration build() {

    return new GlobalConfiguration(
        peerRateLimit,
        peerRequestLimit,
        eth1LogsMaxBlockRange,
        hotStatePersistenceFrequencyInEpochs);
  }
}
