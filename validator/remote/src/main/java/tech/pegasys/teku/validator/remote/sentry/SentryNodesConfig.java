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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import java.util.Optional;

@JsonRootName(value = "beacon_nodes")
public class SentryNodesConfig {

  private final BeaconNodeRoleConfig dutiesProviderNodeConfig;
  private final BeaconNodeRoleConfig blockHandlerNodeConfig;
  private final BeaconNodeRoleConfig attestationPublisherConfig;

  @JsonCreator
  public SentryNodesConfig(
      @JsonProperty(value = "duties_provider", required = true)
          final BeaconNodeRoleConfig dutiesProviderNodeConfig,
      @JsonProperty("block_handler") final BeaconNodeRoleConfig blockHandlerNodeConfig,
      @JsonProperty("attestation_publisher")
          final BeaconNodeRoleConfig attestationPublisherConfig) {
    this.dutiesProviderNodeConfig = dutiesProviderNodeConfig;
    this.blockHandlerNodeConfig = blockHandlerNodeConfig;
    this.attestationPublisherConfig = attestationPublisherConfig;
  }

  public BeaconNodeRoleConfig getDutiesProviderNodeConfig() {
    return dutiesProviderNodeConfig;
  }

  public Optional<BeaconNodeRoleConfig> getBlockHandlerNodeConfig() {
    return Optional.ofNullable(blockHandlerNodeConfig);
  }

  public Optional<BeaconNodeRoleConfig> getAttestationPublisherConfig() {
    return Optional.ofNullable(attestationPublisherConfig);
  }
}
