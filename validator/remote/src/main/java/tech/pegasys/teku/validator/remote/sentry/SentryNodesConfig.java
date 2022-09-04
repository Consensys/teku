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

package tech.pegasys.teku.validator.remote.sentry;

import static tech.pegasys.teku.validator.remote.sentry.BeaconNodesSentryConfig.BEACON_NODES_SENTRY_CONFIG;

import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class SentryNodesConfig {

  static final DeserializableTypeDefinition<SentryNodesConfig> SENTRY_NODES_CONFIG =
      DeserializableTypeDefinition.object(SentryNodesConfig.class)
          .initializer(SentryNodesConfig::new)
          .withField(
              "beacon_nodes",
              BEACON_NODES_SENTRY_CONFIG,
              SentryNodesConfig::getBeaconNodesSentryConfig,
              SentryNodesConfig::setBeaconNodesSentryConfig)
          .build();

  private BeaconNodesSentryConfig beaconNodesSentryConfig;

  public SentryNodesConfig() {}

  public BeaconNodesSentryConfig getBeaconNodesSentryConfig() {
    return beaconNodesSentryConfig;
  }

  public void setBeaconNodesSentryConfig(final BeaconNodesSentryConfig beaconNodesSentryConfig) {
    this.beaconNodesSentryConfig = beaconNodesSentryConfig;
  }
}
