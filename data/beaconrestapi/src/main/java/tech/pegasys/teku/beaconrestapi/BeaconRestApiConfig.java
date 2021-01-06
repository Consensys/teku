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

package tech.pegasys.teku.beaconrestapi;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;

public class BeaconRestApiConfig {
  // Beacon REST API
  private final int restApiPort;
  private final boolean restApiDocsEnabled;
  private final boolean restApiEnabled;
  private final String restApiInterface;
  private final List<String> restApiHostAllowlist;
  private final List<String> restApiCorsAllowedOrigins;
  private final Optional<Eth1Address> eth1DepositContractAddress;

  private BeaconRestApiConfig(
      final int restApiPort,
      final boolean restApiDocsEnabled,
      final boolean restApiEnabled,
      final String restApiInterface,
      final List<String> restApiHostAllowlist,
      final List<String> restApiCorsAllowedOrigins,
      final Optional<Eth1Address> eth1DepositContractAddress) {
    this.restApiPort = restApiPort;
    this.restApiDocsEnabled = restApiDocsEnabled;
    this.restApiEnabled = restApiEnabled;
    this.restApiInterface = restApiInterface;
    this.restApiHostAllowlist = restApiHostAllowlist;
    this.restApiCorsAllowedOrigins = restApiCorsAllowedOrigins;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
  }

  public int getRestApiPort() {
    return restApiPort;
  }

  public boolean isRestApiDocsEnabled() {
    return restApiDocsEnabled;
  }

  public boolean isRestApiEnabled() {
    return restApiEnabled;
  }

  public String getRestApiInterface() {
    return restApiInterface;
  }

  public List<String> getRestApiHostAllowlist() {
    return restApiHostAllowlist;
  }

  public List<String> getRestApiCorsAllowedOrigins() {
    return restApiCorsAllowedOrigins;
  }

  public Optional<Eth1Address> getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public static BeaconRestApiConfigBuilder builder() {
    return new BeaconRestApiConfigBuilder();
  }

  public static final class BeaconRestApiConfigBuilder {
    // Beacon REST API
    private int restApiPort;
    private boolean restApiDocsEnabled;
    private boolean restApiEnabled;
    private String restApiInterface;
    private List<String> restApiHostAllowlist;
    private List<String> restApiCorsAllowedOrigins;
    private Optional<Eth1Address> eth1DepositContractAddress = Optional.empty();

    private BeaconRestApiConfigBuilder() {}

    public BeaconRestApiConfigBuilder restApiPort(final int restApiPort) {
      this.restApiPort = restApiPort;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiDocsEnabled(final boolean restApiDocsEnabled) {
      this.restApiDocsEnabled = restApiDocsEnabled;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiEnabled(final boolean restApiEnabled) {
      this.restApiEnabled = restApiEnabled;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiInterface(final String restApiInterface) {
      this.restApiInterface = restApiInterface;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiHostAllowlist(
        final List<String> restApiHostAllowlist) {
      this.restApiHostAllowlist = restApiHostAllowlist;
      return this;
    }

    public BeaconRestApiConfigBuilder restApiCorsAllowedOrigins(
        final List<String> restApiCorsAllowedOrigins) {
      this.restApiCorsAllowedOrigins = restApiCorsAllowedOrigins;
      return this;
    }

    public BeaconRestApiConfigBuilder eth1DepositContractAddress(
        final Eth1Address eth1DepositContractAddress) {
      checkNotNull(eth1DepositContractAddress);
      this.eth1DepositContractAddress = Optional.of(eth1DepositContractAddress);
      return this;
    }

    public BeaconRestApiConfigBuilder eth1DepositContractAddress(
        final Optional<Eth1Address> eth1DepositContractAddress) {
      checkNotNull(eth1DepositContractAddress);
      this.eth1DepositContractAddress = eth1DepositContractAddress;
      return this;
    }

    public BeaconRestApiConfig build() {
      return new BeaconRestApiConfig(
          restApiPort,
          restApiDocsEnabled,
          restApiEnabled,
          restApiInterface,
          restApiHostAllowlist,
          restApiCorsAllowedOrigins,
          eth1DepositContractAddress);
    }
  }
}
