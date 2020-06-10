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

package tech.pegasys.teku.api.response.v1.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tech.pegasys.teku.api.schema.Metadata;

public class Identity {
  @JsonProperty("peer_id")
  public final String peerId;

  public final String enr;

  @JsonProperty("p2p_addresses")
  public final List<String> p2pAddresses;

  @JsonProperty("discovery_addresses")
  public final List<String> discoveryAddresses;

  public final Metadata metadata;

  @JsonCreator
  public Identity(
      @JsonProperty("peer_id") final String peerId,
      @JsonProperty("enr") final String enr,
      @JsonProperty("p2p_addresses") final List<String> p2pAddresses,
      @JsonProperty("discovery_addresses") final List<String> discoveryAddresses,
      @JsonProperty("metadata") final Metadata metadata) {
    this.peerId = peerId;
    this.enr = enr;
    this.p2pAddresses = p2pAddresses;
    this.discoveryAddresses = discoveryAddresses;
    this.metadata = metadata;
  }
}
