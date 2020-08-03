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
              "onyx",
              builder()
                  .constants("mainnet")
                  .snappyCompressionEnabled(true)
                  .initialStateFromClasspath("onyx-genesis.ssz")
                  .discoveryBootnodes(
                      "enr:-Ku4QMKVC_MowDsmEa20d5uGjrChI0h8_KsKXDmgVQbIbngZV0idV6_RL7fEtZGo-kTNZ5o7_EJI_vCPJ6scrhwX0Z4Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQJxCnE6v_x2ekgY_uoE1rtwzvGy40mq9eD66XfHPBWgIIN1ZHCCD6A")
                  .eth1DepositContractAddress("0x0f0f0fc0530007361933eab5db97d09acdd6c1c8")
                  .eth1Endpoint("https://goerli.prylabs.net")
                  .startupTimeoutSeconds(120)
                  .build())
          .put(
              "altona",
              builder()
                  .constants("altona")
                  .snappyCompressionEnabled(true)
                  .initialStateFromClasspath("altona-genesis.ssz")
                  .eth1DepositContractAddress("0x16e82D77882A663454Ef92806b7DeCa1D394810f")
                  .startupTimeoutSeconds(120)
                  .discoveryBootnodes(
                      "enr:-KG4QLkU_qJgdh-DhT5HtONGvuqwbbW99piCe27Bpk8oO_hvC0qEYgOSpmVfZ-8iJgW71HO23ajEFCNOCvEaqpcIXiUDhGV0aDKQ_co5sAAAASH__________4JpZIJ2NIJpcIQDE_KiiXNlY3AyNTZrMaEDbBoq33b6SAUh96EJZWNYOqYwjm78pVviDreLfK-aeUKDdGNwgiMog3VkcIIjKA",
                      "enr:-LK4QFtV7Pz4reD5a7cpfi1z6yPrZ2I9eMMU5mGQpFXLnLoKZW8TXvVubShzLLpsEj6aayvVO1vFx-MApijD3HLPhlECh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD6etXjAAABIf__________gmlkgnY0gmlwhDMPYfCJc2VjcDI1NmsxoQIerw_qBc9apYfZqo2awiwS930_vvmGnW2psuHsTzrJ8YN0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QPVkFd_MKzdW0219doTZryq40tTe8rwWYO75KDmeZM78fBskGsfCuAww9t8y3u0Q0FlhXOhjE1CWpx3SGbUaU80Ch2F0dG5ldHOIAAAAAAAAAACEZXRoMpD6etXjAAABIf__________gmlkgnY0gmlwhDMPRgeJc2VjcDI1NmsxoQNHu-QfNgzl8VxbMiPgv6wgAljojnqAOrN18tzJMuN8oYN0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QHe52XPPrcv6-MvcmN5GqDe_sgCwo24n_2hedlfwD_oxNt7cXL3tXJ7h9aYv6CTS1C_H2G2_dkeqm_LBO9nrpiYBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD9yjmwAAABIf__________gmlkgnY0gmlwhANzD9uJc2VjcDI1NmsxoQJX7zMnRU3szfGfS8MAIfPaQKOBpu3sBVTXf4Qq0b_m-4N0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QLkbbq7xuRa_EnWd_kc0TkQk0pd0B0cZYR5LvBsncFQBDyPbGdy8d24TzRVeK7ZWwM5_2EcSJK223f8TYUOQYfwBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD9yjmwAAABIf__________gmlkgnY0gmlwhAPsjtOJc2VjcDI1NmsxoQJNw_aZgWXl2SstD--WAjooGudjWLjEbbCIddJuEPxzWYN0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QHy-glnxN1WTk5f6d7-xXwy_UKJLs5k7p_S4KRY9I925KTzW_kQLjfFriIpH0de7kygBwrSl726ukq9_OG_sgKMCh2F0dG5ldHOIUjEAIQEAFMiEZXRoMpD9yjmwAAABIf__________gmlkgnY0gmlwhBLmhrCJc2VjcDI1NmsxoQNlU7gT0HUvpLA41n-P5GrCgjwMwtG02YsRRO0lAmpmBYN0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QDz0n0vpyOpuStB8e22h9ayHVcvmN7o0trC7eC0DnZV9GYGzK5uKv7WlzpMQM2nDTG43DWvF_DZYwJOZCbF4iCQBh2F0dG5ldHOI__________-EZXRoMpD9yjmwAAABIf__________gmlkgnY0gmlwhBKN136Jc2VjcDI1NmsxoQP5gcOUcaruHuMuTv8ht7ZEawp3iih7CmeLqcoY1hxOnoN0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QOScOZ35sOXEH6CEW15lfv7I3DhqQAzCPQ_nRav95otuSh4yi9ol0AruKDiIk9qqGXyD-wQDaBAPLhwl4t-rUSQBh2F0dG5ldHOI__________-EZXRoMpD9yjmwAAABIf__________gmlkgnY0gmlwhCL68KuJc2VjcDI1NmsxoQK5fYR3Ipoc01dz0d2-EcL7m26zKQSkAbf4rwcMMM09CoN0Y3CCIyiDdWRwgiMo",
                      "enr:-Ku4QMqmWPFkgM58F16wxB50cqWDaWaIsyANHL8wUNSB4Cy1TP9__uJQNRODvx_dvO6rY-BT3psrYTMAaxnMGXb6DuoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQNoed9JnQh7ltcAacHEGOjwocL1BhMQbYTgaPX0kFuXtIN1ZHCCE4g")
                  .build())
          .put(
              "medalla",
              builder()
                  .constants("medalla")
                  .snappyCompressionEnabled(true)
                  .initialStateFromClasspath("medalla-genesis.ssz")
                  .startupTimeoutSeconds(120)
                  .eth1DepositContractAddress("0x07b39F4fDE4A38bACe212b546dAc87C58DfE3fDC")
                  .discoveryBootnodes(
                      // PegaSys Teku
                      "enr:-KG4QFuKQ9eeXDTf8J4tBxFvs3QeMrr72mvS7qJgL9ieO6k9Rq5QuGqtGK4VlXMNHfe34Khhw427r7peSoIbGcN91fUDhGV0aDKQD8XYjwAAAAH__________4JpZIJ2NIJpcIQDhMExiXNlY3AyNTZrMaEDESplmV9c2k73v0DjxVXJ6__2bWyP-tK28_80lf7dUhqDdGNwgiMog3VkcIIjKA",

                      // Sigp Lighthouse
                      "enr:-LK4QKWk9yZo258PQouLshTOEEGWVHH7GhKwpYmB5tmKE4eHeSfman0PZvM2Rpp54RWgoOagAsOfKoXgZSbiCYzERWABh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAAAAAAAAAAAAAAAAAAAAAAgmlkgnY0gmlwhDQlA5CJc2VjcDI1NmsxoQOYiWqrQtQksTEtS3qY6idxJE5wkm0t9wKqpzv2gCR21oN0Y3CCIyiDdWRwgiMo",
                      "enr:-LK4QEnIS-PIxxLCadJdnp83VXuJqgKvC9ZTIWaJpWqdKlUFCiup2sHxWihF9EYGlMrQLs0mq_2IyarhNq38eoaOHUoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAAAAAAAAAAAAAAAAAAAAAAgmlkgnY0gmlwhA37LMaJc2VjcDI1NmsxoQJ7k0mKtTd_kdEq251flOjD1HKpqgMmIETDoD-Msy_O-4N0Y3CCIyiDdWRwgiMo",

                      // Prysmatic
                      "enr:-Ku4QLglCMIYAgHd51uFUqejD9DWGovHOseHQy7Od1SeZnHnQ3fSpE4_nbfVs8lsy8uF07ae7IgrOOUFU0NFvZp5D4wBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQJxCnE6v_x2ekgY_uoE1rtwzvGy40mq9eD66XfHPBWgIIN1ZHCCD6A",
                      "enr:-Ku4QOzU2MY51tYFcoByfULugCu2mepfqAbB0DajbRzg8xlILLfi5Iv_Wx-ARn8SiFoZZb3yp2x05cnUDYSoDYZupjIBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQLEq16KLm1vPjUKYGkHq296D60i7y209NYPUpwZPXDVgYN1ZHCCD6A",
                      "enr:-Ku4QOYFmi2BW_YPDew_CKdfMvsrcRY1ARA-ImtcqFl-lgoxOFbxte4PU44-1M3uRNSRM-6rVa8USGohmWwtgwalEt8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQKH3lxnglLqrA7L6sl5r7XFnckr3XCnlZMaBTYSdE8SHIN1ZHCCD6A",

                      // Proto
                      "enr:-Ku4QFVactU18ogiqPPasKs3jhUm5ISszUrUMK2c6SUPbGtANXVJ2wFapsKwVEVnVKxZ7Gsr9yEc4PYF-a14ahPa1q0Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAYrkzLAAAAAf__________gmlkgnY0gmlwhGQbAHyJc2VjcDI1NmsxoQILF-Ya2i5yowVkQtlnZLjG0kqC4qtwmSk8ha7tKLuME4N1ZHCCIyg")
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
