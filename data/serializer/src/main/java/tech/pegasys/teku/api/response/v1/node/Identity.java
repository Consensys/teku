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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.api.schema.Metadata;

public class Identity {
  @JsonProperty("peer_id")
  @Schema(
      description =
          "Cryptographic hash of a peer’s public key. "
              + "[Read more](https://docs.libp2p.io/concepts/peer-id/)",
      example = "QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N")
  public final String peerId;

  @JsonProperty("enr")
  @Schema(
      description =
          "Ethereum node record. " + "[Read more](https://eips.ethereum.org/EIPS/eip-778)",
      example =
          "enr:-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499S"
              + "ZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCdl8")
  public final String enr;

  @JsonProperty("p2p_addresses")
  @ArraySchema(
      arraySchema =
          @Schema(
              type = "string",
              description =
                  "Node's addresses on which eth2 rpc requests are served. "
                      + "[Read more](https://docs.libp2p.io/reference/glossary/#multiaddr)",
              example = "/ip4/7.7.7.7/tcp/4242/p2p/QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N"))
  public final List<String> p2pAddresses;

  @JsonProperty("discovery_addresses")
  @ArraySchema(
      arraySchema =
          @Schema(
              type = "string",
              description =
                  "Node's addresses on which is listening for discv5 requests. "
                      + "[Read more](https://docs.libp2p.io/reference/glossary/#multiaddr)",
              example = "/ip4/7.7.7.7/tcp/4242/p2p/QmYyQSo1c1Ym7orWxLYvCrM2EmxFTANf8wXmmE7DWjhx5N"))
  public final List<String> discoveryAddresses;

  @JsonProperty("metadata")
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Identity identity = (Identity) o;
    return Objects.equals(peerId, identity.peerId)
        && Objects.equals(enr, identity.enr)
        && Objects.equals(p2pAddresses, identity.p2pAddresses)
        && Objects.equals(discoveryAddresses, identity.discoveryAddresses)
        && Objects.equals(metadata, identity.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(peerId, enr, p2pAddresses, discoveryAddresses, metadata);
  }
}
