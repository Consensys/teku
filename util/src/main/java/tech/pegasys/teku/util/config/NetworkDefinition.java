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
          .put("minimal", builder().constants("minimal").startupTargetPeerCount(0).build())
          .put("mainnet", builder().constants("mainnet").build())
          .put(
              "medalla",
              builder()
                  .constants("medalla")
                  .genesisStateFromClasspath("medalla-genesis.ssz")
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
          .put(
              "spadina",
              builder()
                  .constants("spadina")
                  .genesisStateFromClasspath("spadina-genesis.ssz")
                  .startupTimeoutSeconds(120)
                  .eth1DepositContractAddress("0x48B597F4b53C21B48AD95c7256B49D1779Bd5890")
                  .discoveryBootnodes(
                      // PegaSys Teku
                      "enr:-KG4QA-EcFfXQsL2dcneG8vp8HTWLrpwHQ5HhfyIytfpeKOISzROy2kYSsf_v-BZKnIx5XHDjqJ-ttz0hoz6qJA7tasEhGV0aDKQxKgkDQAAAAL__________4JpZIJ2NIJpcIQDFt-UiXNlY3AyNTZrMaECkR4C5DVO_9rB48eHTY4kdyOHsguTEDlvb7Ce0_mvghSDdGNwgiMog3VkcIIjKA",

                      // Prysmatic Prysm
                      "enr:-Ku4QGQJf2bcDAwVGvbvtq3AB4KKwAvStTenY-i_QnW2ABNRRBncIU_5qR_e_um-9t3s9g-Y5ZfFATj1nhtzq6lvgc4Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDEqCQNAAAAAv__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQNoed9JnQh7ltcAacHEGOjwocL1BhMQbYTgaPX0kFuXtIN1ZHCCE4g",

                      // Proto
                      "enr:-Ku4QFW1SLbtzJ_ghQQC8-8xezvZ1Mx95J-zer9IPmDE2BKeD_SM7j4vH6xmroUFVuyK-54n2Ey2ueB-Lf-fkbcLwAQBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDEqCQNAAAAAv__________gmlkgnY0gmlwhGQZkSyJc2VjcDI1NmsxoQJMcbZhTCEKYSH5-qPQPgYfSHHUMLGBAKU-f-96yYKFMIN1ZHCCIyg")
                  .build())
          .put(
              "zinken",
              builder()
                  .constants("zinken")
                  .genesisStateFromClasspath("zinken-genesis.ssz")
                  .startupTimeoutSeconds(120)
                  .eth1DepositContractAddress("0x99F0Ec06548b086E46Cb0019C78D0b9b9F36cD53")
                  .discoveryBootnodes(
                      // PegaSys Teku
                      "enr:-KG4QHPtVnKHEOkEJT1f5C6Hs-C_c4SlipTfkPrDIikLTzhqA_3m6bTq-CirsljlVP4IJybXelHE7J3l9DojR14_ZHUGhGV0aDKQ2jUIggAAAAP__________4JpZIJ2NIJpcIQSv2qciXNlY3AyNTZrMaECi_CNPDkKPilhimY7aEY-mBtSzI8AKMDvvv_I2Un74_qDdGNwgiMog3VkcIIjKA",

                      // Prysmatic Prysm
                      "enr:-Ku4QH63huZ12miIY0kLI9dunG5fwKpnn-zR3XyA_kH6rQpRD1VoyLyzIcFysCJ09JDprdX-EzXp-Nc8swYqBznkXggBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDaNQiCAAAAA___________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQILqxBY-_SF8o_5FjFD3yM92s50zT_ciFi8hStde5AEjIN1ZHCCH0A",

                      // Proto
                      "enr:-Ku4QMGGAuQO8NPhYCz29wsahrFR-betfxKx6ltyzLUM70yJWoaRjJZ-n1Oiof2PiKnzjVG1n6RoyO4ZNJkQtqEkqNkBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDaNQiCAAAAA___________gmlkgnY0gmlwhDZUyU6Jc2VjcDI1NmsxoQNMOowBnXeUYjK71_Zz78j3y7EYKSXH9ZGhYB4wB6V8lIN1ZHCCIyg")
                  .build())
          .build();

  private final String constants;
  private final Optional<String> genesisState;
  private final int startupTargetPeerCount;
  private final int startupTimeoutSeconds;
  private final List<String> discoveryBootnodes;
  private final Optional<Eth1Address> eth1DepositContractAddress;
  private final Optional<String> eth1Endpoint;

  private NetworkDefinition(
      final String constants,
      final Optional<String> genesisState,
      final int startupTargetPeerCount,
      final int startupTimeoutSeconds,
      final List<String> discoveryBootnodes,
      final Optional<Eth1Address> eth1DepositContractAddress,
      final Optional<String> eth1Endpoint) {
    this.constants = constants;
    this.genesisState = genesisState;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
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

  public Optional<String> getGenesisState() {
    return genesisState;
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

  private static class Builder {
    private String constants;
    private Optional<String> genesisState = Optional.empty();
    private int startupTargetPeerCount = Constants.DEFAULT_STARTUP_TARGET_PEER_COUNT;
    private int startupTimeoutSeconds = Constants.DEFAULT_STARTUP_TIMEOUT_SECONDS;
    private List<String> discoveryBootnodes = new ArrayList<>();
    private Optional<Eth1Address> eth1DepositContractAddress = Optional.empty();
    private Optional<String> eth1Endpoint = Optional.empty();

    public Builder constants(final String constants) {
      this.constants = constants;
      return this;
    }

    public Builder genesisState(final String genesisState) {
      this.genesisState = Optional.of(genesisState);
      return this;
    }

    public Builder genesisStateFromClasspath(final String filename) {
      this.genesisState =
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

    public NetworkDefinition build() {
      checkNotNull(constants, "Missing constants");
      return new NetworkDefinition(
          constants,
          genesisState,
          startupTargetPeerCount,
          startupTimeoutSeconds,
          discoveryBootnodes,
          eth1DepositContractAddress,
          eth1Endpoint);
    }
  }
}
