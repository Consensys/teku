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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class NetworkDefinition {
  private static final ImmutableMap<String, NetworkDefinition> NETWORKS =
      ImmutableMap.<String, NetworkDefinition>builder()
          .put(
              "minimal",
              builder()
                  .constants("minimal")
                  .snappyCompressionEnabled(false)
                  .startupTargetPeerCount(0)
                  .build())
          .put("mainnet", builder().constants("mainnet").snappyCompressionEnabled(true).build())
          .put(
              "topaz",
              builder()
                  .constants("mainnet")
                  .snappyCompressionEnabled(true)
                  .initialState(
                      "https://github.com/eth2-clients/eth2-testnets/raw/master/prysm/Topaz(v0.11.1)/genesis.ssz")
                  .discoveryBootnodes(
                      "enr:-Ku4QAGwOT9StqmwI5LHaIymIO4ooFKfNkEjWa0f1P8OsElgBh2Ijb-GrD_-b9W4kcPFcwmHQEy5RncqXNqdpVo1heoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAAAAAAAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQJxCnE6v_x2ekgY_uoE1rtwzvGy40mq9eD66XfHPBWgIIN1ZHCCD6A")
                  .eth1DepositContractAddress("0x5cA1e00004366Ac85f492887AAab12d0e6418876")
                  .build())
          .put(
              "schlesi",
              builder()
                  .constants("schlesi")
                  .snappyCompressionEnabled(true)
                  .initialState(
                      "https://github.com/goerli/schlesi/raw/master/.trash/schlesi/teku/genesis.ssz")
                  .discoveryBootnodes(
                      "enr:-LK4QJ-6k6QytxOn7P9BdDZHXesHz3aaglpvo-VcTGc-rfr5H4DBzjQsjg6stZoy1H-p3yK21IISkJHe742QTVwRS_IEh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCZJe_WAAAAAP__________gmlkgnY0gmlwhDMPd52Jc2VjcDI1NmsxoQINdLr6UY7y2CzshX4n_BbdYM1G40rpdEs84Mdoyv_ZyYN0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QFO0gKFieMiNrUystSk5Xt7DmIgusloLudv-gH8Krjw9SsUDZRk---H-3hwvL9rMfsMcZwU6L5ezK2d1_dG0UgECh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCZJe_WAAAAAP__________gmlkgnY0gmlwhDMPd52Jc2VjcDI1NmsxoQPNb3TG-iN0aGTagN4peO0SEkWKklJOvloWL0He8pnB_4N0Y3CCJRyDdWRwgiUc",
                      "enr:-LK4QJS5Rn_kkA2MQpieVDUao5vkBj3kE15S_JJepGA9MNfndwHyfBWSjmAa5T_qvkGklrDiZXqlIAahXTm_eH_IXY8Ch2F0dG5ldHOIAAAAAAAAAACEZXRoMpCZJe_WAAAAAP__________gmlkgnY0gmlwhDMPd52Jc2VjcDI1NmsxoQOS1-hRSwsxLo2PH3RKtwWdjLdT1IMX2nqkQAlHs5E7LIN0Y3CCMsiDdWRwgi7g",
                      "enr:-LK4QC08ftWworc3AQkYAtFSzUZpbSkRrgw74WrvKPFL3BbPBozhZx-gLHw8FeBzbi_0HDmZDWqZF-oF0b0W8Q8kHFELh2F0dG5ldHOIAQAAAAAAAACEZXRoMpCZJe_WAAAAAP__________gmlkgnY0gmlwhDMPd52Jc2VjcDI1NmsxoQJyLMVEG-_6ho3DR0iYvyEVbMyOJ4o2G-pIIEsNw80nn4N0Y3CCNLyDdWRwgjDU",
                      "enr:-KG4QEKucvfLm_Hp8Erw1rVEGerBlDblJI54LNNHvzfCY-jCAHTaoHf0UF8HLB5HsbZtJhjJ83oWkQ0aMty7c26aZy8ChGV0aDKQmSXv1gAAAAD__________4JpZIJ2NIJpcIQzD0YHiXNlY3AyNTZrMaEDggHXPlO6yT4JkCgVMOJjilj4F0ogSlHuXjPJjsiWne2DdGNwgiMog3VkcIIjKA",
                      "enr:-KG4QBUEkcqHGnHHCZLnWfSPBocBqP5SNClDHOR1KmlzaS-YN53w0xBspt-HCzk5-FZw_ZcYIdxQKrLp8VUSO2LPSDwChGV0aDKQmSXv1gAAAAD__________4JpZIJ2NIJpcIQzD0YHiXNlY3AyNTZrMaEDMcdoZ1TJBKATCJixtLTYxGmKbe7r3ckjvhg5OP5cILeDdGNwgiUcg3VkcIIlHA")
                  .eth1DepositContractAddress("0xA15554BF93a052669B511ae29EA21f3581677ac5")
                  .build())
          .put(
              "witti",
              builder()
                  .constants("witti")
                  .snappyCompressionEnabled(true)
                  .initialStateFromClasspath("witti-genesis.ssz")
                  .discoveryBootnodes(
                      "enr:-Ku4QMucba73OPBz2mgyYduH1tM50mZjaiLNEXMrEmTSnrgyEWMEF0zFK0QT6URu_wFfqW04gYn1wze-VQe-jb0L8r8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhFzAVJaJc2VjcDI1NmsxoQIX4kyr2PR-bGTptaN9DbXa2A2L_rVfuIpMj7sCjPBov4N1ZHCCW8w",
                      "enr:-Ku4QJsxkOibTc9FXfBWYmcdMAGwH4bnOOFb4BlTHfMdx_f0WN-u4IUqZcQVP9iuEyoxipFs7-Qd_rH_0HfyOQitc7IBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhLAJM9iJc2VjcDI1NmsxoQL2RyM26TKZzqnUsyycHQB4jnyg6Wi79rwLXtaZXty06YN1ZHCCW8w",
                      "enr:-Ku4QF5CI2Ndig0cxR0fQjRolG1k6TggK0BV4Z5neog4U9LDYsTHz6Vv6qVyJ7b9L2r6S2Gu4Ek4Z7i7j-jAJBxHbmYBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhFe0zw-Jc2VjcDI1NmsxoQPD9aDKsFoUfpEMPuTgZ13qNsJZ31zMMrl3PsQXeX2cwYN1ZHCCW8w",
                      "enr:-Ku4QLE_fTEjP6K3OII1RYRLUMUbwV9dAh7-2vr7gkZnRXLxSy2B6C-b0nVVQcFYsUvp2Tgli7GKHBYpWiknTse7rrUBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhFzAVJaJc2VjcDI1NmsxoQKxhcZJCugFnVuRMMzE4JJe0E0FS71ctmAnx1Y2wCJwKoN1ZHCCpgQ",
                      "enr:-Ku4QKsKa3HbJjz8cZn4mEh-stIF6kACLh2rmCGscEsLUe4XUSt-xZEAx7SK6R3zqAc2WAVBpLkh5fu-r-PHr_8d4B8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhDMPd52Jc2VjcDI1NmsxoQMLvOjDnLAqnQsTKqUqNr1qcleEBgkin3KOW9BeIxAJ54N1ZHCCW8w")
                  .eth1DepositContractAddress("0x42cc0FcEB02015F145105Cf6f19F90e9BEa76558")
                  .startupTimeoutSeconds(120)
                  .build())
          .build();

  private final String constants;
  private final Optional<String> initialState;
  private final int startupTargetPeerCount;
  private final int startupTimeoutSeconds;
  private final List<String> discoveryBootnodes;
  private final Optional<Eth1Address> eth1DepositContractAddress;
  private final Optional<String> eth1Endpoint;
  private final Optional<Boolean> snappyCompressionEnabled;

  private NetworkDefinition(
      final String constants,
      final Optional<String> initialState,
      final int startupTargetPeerCount,
      final int startupTimeoutSeconds,
      final List<String> discoveryBootnodes,
      final Optional<Eth1Address> eth1DepositContractAddress,
      final Optional<String> eth1Endpoint,
      final Optional<Boolean> snappyCompressionEnabled) {
    this.constants = constants;
    this.initialState = initialState;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.discoveryBootnodes = discoveryBootnodes;
    this.eth1DepositContractAddress = eth1DepositContractAddress;
    this.eth1Endpoint = eth1Endpoint;
    this.snappyCompressionEnabled = snappyCompressionEnabled;
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

  public Optional<String> getInitialState() {
    return initialState;
  }

  public Integer getStartupTargetPeerCount() {
    return startupTargetPeerCount;
  }

  public Integer getStartupTimeoutSeconds() {
    return startupTimeoutSeconds;
  }

  public List<String> getDiscoveryBootnodes() {
    return discoveryBootnodes;
  }

  public Optional<Eth1Address> getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public Optional<String> getEth1Endpoint() {
    return eth1Endpoint;
  }

  public Optional<Boolean> getSnappyCompressionEnabled() {
    return snappyCompressionEnabled;
  }

  private static class Builder {
    private String constants;
    private Optional<String> initialState = Optional.empty();
    private int startupTargetPeerCount = Constants.DEFAULT_STARTUP_TARGET_PEER_COUNT;
    private int startupTimeoutSeconds = Constants.DEFAULT_STARTUP_TIMEOUT_SECONDS;
    private List<String> discoveryBootnodes = new ArrayList<>();
    private Optional<Eth1Address> eth1DepositContractAddress = Optional.empty();
    private Optional<String> eth1Endpoint = Optional.empty();
    private Optional<Boolean> snappyCompressionEnabled = Optional.empty();

    public Builder constants(final String constants) {
      this.constants = constants;
      return this;
    }

    public Builder initialState(final String initialState) {
      this.initialState = Optional.of(initialState);
      return this;
    }

    public Builder initialStateFromClasspath(final String filename) {
      this.initialState =
          Optional.of(NetworkDefinition.class.getResource(filename).toExternalForm());
      return this;
    }

    public Builder startupTargetPeerCount(final int startupTargetPeerCount) {
      this.startupTargetPeerCount = startupTargetPeerCount;
      return this;
    }

    public Builder startupTimeoutSeconds(final int startupTimeoutSeconds) {
      this.startupTimeoutSeconds = startupTimeoutSeconds;
      return this;
    }

    public Builder discoveryBootnodes(final String... discoveryBootnodes) {
      this.discoveryBootnodes = asList(discoveryBootnodes);
      return this;
    }

    public Builder eth1DepositContractAddress(final String eth1Address) {
      this.eth1DepositContractAddress = Optional.of(Eth1Address.fromHexString(eth1Address));
      return this;
    }

    public Builder eth1Endpoint(final String eth1Endpoint) {
      this.eth1Endpoint = Optional.of(eth1Endpoint);
      return this;
    }

    public Builder snappyCompressionEnabled(final boolean isEnabled) {
      snappyCompressionEnabled = Optional.of(isEnabled);
      return this;
    }

    public NetworkDefinition build() {
      checkNotNull(constants, "Missing constants");
      return new NetworkDefinition(
          constants,
          initialState,
          startupTargetPeerCount,
          startupTimeoutSeconds,
          discoveryBootnodes,
          eth1DepositContractAddress,
          eth1Endpoint,
          snappyCompressionEnabled);
    }
  }
}
