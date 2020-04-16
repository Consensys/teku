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

package tech.pegasys.artemis.util.config;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class NetworkDefinition {

  private static final ImmutableMap<String, NetworkDefinition> NETWORKS =
      ImmutableMap.<String, NetworkDefinition>builder()
          .put("minimal", builder().constants("minimal").build())
          .put("mainnet", builder().constants("mainnet").build())
          .put(
              "topaz",
              builder()
                  .constants("mainnet")
                  .eth1DepositContractAddress("0x5cA1e00004366Ac85f492887AAab12d0e6418876")
                  .build())
          .build();

  private final String constants;
  private final Optional<List<String>> discoveryBootnodes;
  private final Optional<String> eth1DepositContractAddress;
  private final Optional<String> eth1Endpoint;

  private NetworkDefinition(
      final String constants,
      final Optional<List<String>> discoveryBootnodes,
      final Optional<String> eth1DepositContractAddress,
      final Optional<String> eth1Endpoint) {
    this.constants = constants;
    this.discoveryBootnodes = discoveryBootnodes;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1Endpoint = eth1Endpoint;
  }

  public static NetworkDefinition fromCliArg(final String arg) {
    return NETWORKS.getOrDefault(arg.toLowerCase(Locale.US), builder().constants(arg).build());
  }

  private static Builder builder() {
    return new Builder();
  }

  public String getConstants() {
    return constants;
  }

  public Optional<List<String>> getDiscoveryBootnodes() {
    return discoveryBootnodes;
  }

  public Optional<String> getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public Optional<String> getEth1Endpoint() {
    return eth1Endpoint;
  }

  private static class Builder {
    private String constants;
    private Optional<List<String>> discoveryBootnodes = Optional.empty();
    private Optional<String> eth1DepositContractAddress = Optional.empty();
    private Optional<String> eth1Endpoint = Optional.empty();

    public Builder constants(final String constants) {
      this.constants = constants;
      return this;
    }

    public Builder discoveryBootnodes(final String... discoveryBootnodes) {
      this.discoveryBootnodes = Optional.of(asList(discoveryBootnodes));
      return this;
    }

    public Builder eth1DepositContractAddress(final String eth1DepositContractAddress) {
      this.eth1DepositContractAddress = Optional.of(eth1DepositContractAddress);
      return this;
    }

    public Builder eth1Endpoint(final String eth1Endpoint) {
      this.eth1Endpoint = Optional.of(eth1Endpoint);
      return this;
    }

    public NetworkDefinition build() {
      checkNotNull(constants, "Missing constants");
      return new NetworkDefinition(
          constants, discoveryBootnodes, eth1DepositContractAddress, eth1Endpoint);
    }
  }
}
