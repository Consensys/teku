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

import java.util.List;
import java.util.Optional;

public enum PredefinedNetwork {
  MINIMAL("minimal"),
  MAINNET("mainnet"),
  TOPAZ(
      "mainnet",
      Optional.empty(),
      Optional.of("0x5cA1e00004366Ac85f492887AAab12d0e6418876"),
      Optional.empty());

  private final String constants;
  private final Optional<List<String>> discoveryBootnodes;
  private final Optional<String> eth1DepositContractAddress;
  private final Optional<String> eth1Endpoint;

  PredefinedNetwork(final String constants) {
    this(constants, Optional.empty(), Optional.empty(), Optional.empty());
  }

  PredefinedNetwork(
      final String constants,
      final Optional<List<String>> discoveryBootnodes,
      final Optional<String> eth1DepositContractAddress,
      final Optional<String> eth1Endpoint) {
    this.constants = constants;
    this.discoveryBootnodes = discoveryBootnodes;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1Endpoint = eth1Endpoint;
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
}
