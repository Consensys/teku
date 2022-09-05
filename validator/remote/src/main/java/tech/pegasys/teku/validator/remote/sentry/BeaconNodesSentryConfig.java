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

import static tech.pegasys.teku.validator.remote.sentry.BeaconNodeRoleConfig.BEACON_NODE_ROLE_CONFIG;

import com.fasterxml.jackson.annotation.JsonRootName;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

@JsonRootName(value = "beacon_nodes")
public class BeaconNodesSentryConfig {

  static final DeserializableTypeDefinition<BeaconNodesSentryConfig> BEACON_NODES_SENTRY_CONFIG =
      DeserializableTypeDefinition.object(BeaconNodesSentryConfig.class)
          .initializer(BeaconNodesSentryConfig::new)
          .withField(
              "duties_provider",
              BEACON_NODE_ROLE_CONFIG,
              BeaconNodesSentryConfig::getDutiesProviderNodeConfig,
              BeaconNodesSentryConfig::setDutiesProviderNodeConfig)
          .withOptionalField(
              "block_handler",
              BEACON_NODE_ROLE_CONFIG,
              BeaconNodesSentryConfig::getBlockHandlerNodeConfig,
              BeaconNodesSentryConfig::setBlockHandlerNodeConfig)
          .withOptionalField(
              "attestation_publisher",
              BEACON_NODE_ROLE_CONFIG,
              BeaconNodesSentryConfig::getAttestationPublisherConfig,
              BeaconNodesSentryConfig::setAttestationPublisherConfig)
          .build();

  private BeaconNodeRoleConfig dutiesProviderNodeConfig;
  private Optional<BeaconNodeRoleConfig> blockHandlerNodeConfig = Optional.empty();
  private Optional<BeaconNodeRoleConfig> attestationPublisherConfig = Optional.empty();

  public BeaconNodesSentryConfig() {}

  public BeaconNodeRoleConfig getDutiesProviderNodeConfig() {
    return dutiesProviderNodeConfig;
  }

  public Optional<BeaconNodeRoleConfig> getBlockHandlerNodeConfig() {
    return blockHandlerNodeConfig;
  }

  public Optional<BeaconNodeRoleConfig> getAttestationPublisherConfig() {
    return attestationPublisherConfig;
  }

  public void setDutiesProviderNodeConfig(final BeaconNodeRoleConfig dutiesProviderNodeConfig) {
    this.dutiesProviderNodeConfig = dutiesProviderNodeConfig;
  }

  public void setBlockHandlerNodeConfig(
      final Optional<BeaconNodeRoleConfig> blockHandlerNodeConfig) {
    this.blockHandlerNodeConfig = blockHandlerNodeConfig;
  }

  public void setAttestationPublisherConfig(
      final Optional<BeaconNodeRoleConfig> attestationPublisherConfig) {
    this.attestationPublisherConfig = attestationPublisherConfig;
  }
}
