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

package tech.pegasys.teku.networks;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;
import static tech.pegasys.teku.spec.networks.Eth2Network.KINTSUGI;
import static tech.pegasys.teku.spec.networks.Eth2Network.LESS_SWIFT;
import static tech.pegasys.teku.spec.networks.Eth2Network.MAINNET;
import static tech.pegasys.teku.spec.networks.Eth2Network.MINIMAL;
import static tech.pegasys.teku.spec.networks.Eth2Network.PRATER;
import static tech.pegasys.teku.spec.networks.Eth2Network.PYRMONT;
import static tech.pegasys.teku.spec.networks.Eth2Network.SWIFT;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class Eth2NetworkConfiguration {
  private static final int DEFAULT_STARTUP_TARGET_PEER_COUNT = 5;
  private static final int DEFAULT_STARTUP_TIMEOUT_SECONDS = 30;

  private final Spec spec;
  private final String constants;
  private final Optional<String> initialState;
  private final boolean usingCustomInitialState;
  private final int startupTargetPeerCount;
  private final int startupTimeoutSeconds;
  private final List<String> discoveryBootnodes;
  private final Optional<UInt64> altairForkEpoch;
  private final Optional<UInt64> mergeForkEpoch;
  private final Eth1Address eth1DepositContractAddress;
  private final Optional<UInt64> eth1DepositContractDeployBlock;
  private final boolean proposerBoostEnabled;
  private final Optional<Bytes32> mergeTerminalBlockHashOverride;
  private final Optional<UInt256> mergeTotalTerminalDifficultyOverride;
  private final Optional<UInt64> mergeTerminalBlockHashEpochOverride;

  private Eth2NetworkConfiguration(
      final Spec spec,
      final String constants,
      final Optional<String> initialState,
      final boolean usingCustomInitialState,
      final int startupTargetPeerCount,
      final int startupTimeoutSeconds,
      final List<String> discoveryBootnodes,
      final Eth1Address eth1DepositContractAddress,
      final Optional<UInt64> eth1DepositContractDeployBlock,
      final boolean proposerBoostEnabled,
      final Optional<UInt64> altairForkEpoch,
      final Optional<UInt64> mergeForkEpoch,
      final Optional<Bytes32> mergeTerminalBlockHashOverride,
      final Optional<UInt256> mergeTotalTerminalDifficultyOverride,
      final Optional<UInt64> mergeTerminalBlockHashEpochOverride) {
    this.spec = spec;
    this.constants = constants;
    this.initialState = initialState;
    this.usingCustomInitialState = usingCustomInitialState;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.discoveryBootnodes = discoveryBootnodes;
    this.altairForkEpoch = altairForkEpoch;
    this.mergeForkEpoch = mergeForkEpoch;
    this.eth1DepositContractAddress =
        eth1DepositContractAddress == null
            ? new Eth1Address(spec.getGenesisSpecConfig().getDepositContractAddress())
            : eth1DepositContractAddress;
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
    this.proposerBoostEnabled = proposerBoostEnabled;
    this.mergeTerminalBlockHashOverride = mergeTerminalBlockHashOverride;
    this.mergeTotalTerminalDifficultyOverride = mergeTotalTerminalDifficultyOverride;
    this.mergeTerminalBlockHashEpochOverride = mergeTerminalBlockHashEpochOverride;
  }

  public static Eth2NetworkConfiguration.Builder builder(final String network) {
    return builder().applyNetworkDefaults(network);
  }

  public static Eth2NetworkConfiguration.Builder builder(final Eth2Network network) {
    return builder().applyNetworkDefaults(network);
  }

  public static Eth2NetworkConfiguration.Builder builder() {
    return new Builder();
  }

  public Spec getSpec() {
    return spec;
  }

  /**
   * @deprecated Constants should be accessed via {@link SpecVersion}
   * @return The constants resource name or url
   */
  @Deprecated
  public String getConstants() {
    return constants;
  }

  public Optional<String> getInitialState() {
    return initialState;
  }

  public boolean isUsingCustomInitialState() {
    return usingCustomInitialState;
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

  public Eth1Address getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public Optional<UInt64> getEth1DepositContractDeployBlock() {
    return eth1DepositContractDeployBlock;
  }

  public boolean isProposerBoostEnabled() {
    return proposerBoostEnabled;
  }

  public Optional<UInt64> getAltairForkEpoch() {
    return altairForkEpoch;
  }

  public Optional<UInt64> getMergeForkEpoch() {
    return mergeForkEpoch;
  }

  public Optional<Bytes32> getMergeTerminalBlockHashOverride() {
    return mergeTerminalBlockHashOverride;
  }

  public Optional<UInt256> getMergeTotalTerminalDifficultyOverride() {
    return mergeTotalTerminalDifficultyOverride;
  }

  public Optional<UInt64> getMergeTerminalBlockHashEpochOverride() {
    return mergeTerminalBlockHashEpochOverride;
  }

  @Override
  public String toString() {
    return constants;
  }

  public static class Builder {
    private String constants;
    private Optional<String> initialState = Optional.empty();
    private boolean usingCustomInitialState = false;
    private int startupTargetPeerCount = DEFAULT_STARTUP_TARGET_PEER_COUNT;
    private int startupTimeoutSeconds = DEFAULT_STARTUP_TIMEOUT_SECONDS;
    private List<String> discoveryBootnodes = new ArrayList<>();
    private Eth1Address eth1DepositContractAddress;
    private Optional<UInt64> eth1DepositContractDeployBlock = Optional.empty();
    private boolean proposerBoostEnabled = false;
    private Optional<UInt64> altairForkEpoch = Optional.empty();
    private Optional<UInt64> mergeForkEpoch = Optional.empty();
    private Optional<Bytes32> mergeTerminalBlockHashOverride = Optional.empty();
    private Optional<UInt256> mergeTotalTerminalDifficultyOverride = Optional.empty();
    private Optional<UInt64> mergeTerminalBlockHashEpochOverride = Optional.empty();
    private Spec spec;

    public void spec(Spec spec) {
      this.spec = spec;
    }

    public Eth2NetworkConfiguration build() {
      checkNotNull(constants, "Missing constants");
      if (spec == null) {
        spec = SpecFactory.create(constants, altairForkEpoch, mergeForkEpoch);
      }
      // if the deposit contract was not set, default from constants
      if (eth1DepositContractAddress == null) {
        final String contractAddress =
            spec.getGenesisSpec().getConfig().getDepositContractAddress().toUnprefixedHexString();
        if (contractAddress.length() < 40) {
          eth1DepositContractAddress("0x" + Strings.padStart(contractAddress, 40, '0'));
        } else {
          eth1DepositContractAddress("0x" + contractAddress);
        }
      }
      return new Eth2NetworkConfiguration(
          spec,
          constants,
          initialState,
          usingCustomInitialState,
          startupTargetPeerCount,
          startupTimeoutSeconds,
          discoveryBootnodes,
          eth1DepositContractAddress,
          eth1DepositContractDeployBlock,
          proposerBoostEnabled,
          altairForkEpoch,
          mergeForkEpoch,
          mergeTerminalBlockHashOverride,
          mergeTotalTerminalDifficultyOverride,
          mergeTerminalBlockHashEpochOverride);
    }

    public Builder constants(final String constants) {
      this.constants = constants;
      return this;
    }

    public Builder customInitialState(final String initialState) {
      this.initialState = Optional.of(initialState);
      this.usingCustomInitialState = true;
      return this;
    }

    public Builder defaultInitialState(final String initialState) {
      this.initialState = Optional.of(initialState);
      this.usingCustomInitialState = false;
      return this;
    }

    public Builder initialStateFromClasspath(final String filename) {
      this.initialState =
          Optional.of(Eth2NetworkConfiguration.class.getResource(filename).toExternalForm());
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
      this.eth1DepositContractAddress = Eth1Address.fromHexString(eth1Address);
      return this;
    }

    public Builder eth1DepositContractAddress(final Eth1Address eth1Address) {
      checkNotNull(eth1Address);
      this.eth1DepositContractAddress = eth1Address;
      return this;
    }

    public Builder eth1DepositContractDeployBlock(final long eth1DepositContractDeployBlock) {
      this.eth1DepositContractDeployBlock =
          Optional.of(UInt64.valueOf(eth1DepositContractDeployBlock));
      return this;
    }

    public Builder proposerBoostEnabled(final boolean proposerBoostEnabled) {
      this.proposerBoostEnabled = proposerBoostEnabled;
      return this;
    }

    public Builder altairForkEpoch(final UInt64 altairForkEpoch) {
      this.altairForkEpoch = Optional.of(altairForkEpoch);
      return this;
    }

    public Builder mergeForkEpoch(final UInt64 mergeForkEpoch) {
      this.mergeForkEpoch = Optional.of(mergeForkEpoch);
      return this;
    }

    public Builder mergeTotalTerminalDifficultyOverride(
        final UInt256 mergeTotalTerminalDifficultyOverride) {
      this.mergeTotalTerminalDifficultyOverride = Optional.of(mergeTotalTerminalDifficultyOverride);
      return this;
    }

    public Builder mergeTerminalBlockHashOverride(final Bytes32 mergeTerminalBlockHashOverride) {
      this.mergeTerminalBlockHashOverride = Optional.of(mergeTerminalBlockHashOverride);
      return this;
    }

    public Builder mergeTerminalBlockHashEpochOverride(
        final UInt64 mergeTerminalBlockHashEpochOverride) {
      this.mergeTerminalBlockHashEpochOverride = Optional.of(mergeTerminalBlockHashEpochOverride);
      return this;
    }

    public Builder applyNetworkDefaults(final String networkName) {
      Eth2Network.fromStringLenient(networkName)
          .ifPresentOrElse(this::applyNetworkDefaults, () -> reset().constants(networkName));
      return this;
    }

    public Builder applyNetworkDefaults(final Eth2Network network) {
      switch (network) {
        case MAINNET:
          return applyMainnetNetworkDefaults();
        case MINIMAL:
          return applyMinimalNetworkDefaults();
        case PYRMONT:
          return applyPyrmontNetworkDefaults();
        case PRATER:
          return applyPraterNetworkDefaults();
        case KINTSUGI:
          return applyKintsugiNetworkDefaults();
        case SWIFT:
          return applySwiftNetworkDefaults();
        case LESS_SWIFT:
          return applyLessSwiftNetworkDefaults();
        default:
          return reset().constants(network.configName());
      }
    }

    private Builder reset() {
      constants = null;
      initialState = Optional.empty();
      startupTargetPeerCount = DEFAULT_STARTUP_TARGET_PEER_COUNT;
      startupTimeoutSeconds = DEFAULT_STARTUP_TIMEOUT_SECONDS;
      discoveryBootnodes = new ArrayList<>();
      eth1DepositContractAddress = null;
      eth1DepositContractDeployBlock = Optional.empty();

      return this;
    }

    public Builder applyMinimalNetworkDefaults() {
      return reset().constants(MINIMAL.configName()).startupTargetPeerCount(0);
    }

    public Builder applySwiftNetworkDefaults() {
      return reset().constants(SWIFT.configName()).startupTargetPeerCount(0);
    }

    public Builder applyLessSwiftNetworkDefaults() {
      return reset().constants(LESS_SWIFT.configName()).startupTargetPeerCount(0);
    }

    public Builder applyMainnetNetworkDefaults() {
      return reset()
          .constants(MAINNET.configName())
          .initialStateFromClasspath("mainnet-genesis.ssz")
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(11052984)
          .discoveryBootnodes(
              // PegaSys Teku
              "enr:-KG4QJRlj4pHagfNIm-Fsx9EVjW4rviuZYzle3tyddm2KAWMJBDGAhxfM2g-pDaaiwE8q19uvLSH4jyvWjypLMr3TIcEhGV0aDKQ9aX9QgAAAAD__________4JpZIJ2NIJpcIQDE8KdiXNlY3AyNTZrMaEDhpehBDbZjM_L9ek699Y7vhUJ-eAdMyQW_Fil522Y0fODdGNwgiMog3VkcIIjKA",
              "enr:-KG4QL-eqFoHy0cI31THvtZjpYUu_Jdw_MO7skQRJxY1g5HTN1A0epPCU6vi0gLGUgrzpU-ygeMSS8ewVxDpKfYmxMMGhGV0aDKQtTA_KgAAAAD__________4JpZIJ2NIJpcIQ2_DUbiXNlY3AyNTZrMaED8GJ2vzUqgL6-KD1xalo1CsmY4X1HaDnyl6Y_WayCo9GDdGNwgiMog3VkcIIjKA",

              // Prysmatic Labs
              "enr:-Ku4QImhMc1z8yCiNJ1TyUxdcfNucje3BGwEHzodEZUan8PherEo4sF7pPHPSIB1NNuSg5fZy7qFsjmUKs2ea1Whi0EBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQOVphkDqal4QzPMksc5wnpuC3gvSC8AfbFOnZY_On34wIN1ZHCCIyg",
              "enr:-Ku4QP2xDnEtUXIjzJ_DhlCRN9SN99RYQPJL92TMlSv7U5C1YnYLjwOQHgZIUXw6c-BvRg2Yc2QsZxxoS_pPRVe0yK8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQMeFF5GrS7UZpAH2Ly84aLK-TyvH-dRo0JM1i8yygH50YN1ZHCCJxA",
              "enr:-Ku4QPp9z1W4tAO8Ber_NQierYaOStqhDqQdOPY3bB3jDgkjcbk6YrEnVYIiCBbTxuar3CzS528d2iE7TdJsrL-dEKoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQMw5fqqkw2hHC4F5HZZDPsNmPdB1Gi8JPQK7pRc9XHh-oN1ZHCCKvg",
              // Sigp Lighthouse
              "enr:-Jq4QItoFUuug_n_qbYbU0OY04-np2wT8rUCauOOXNi0H3BWbDj-zbfZb7otA7jZ6flbBpx1LNZK2TDebZ9dEKx84LYBhGV0aDKQtTA_KgEAAAD__________4JpZIJ2NIJpcISsaa0ZiXNlY3AyNTZrMaEDHAD2JKYevx89W0CcFJFiskdcEzkH_Wdv9iW42qLK79ODdWRwgiMo",
              "enr:-Jq4QN_YBsUOqQsty1OGvYv48PMaiEt1AzGD1NkYQHaxZoTyVGqMYXg0K9c0LPNWC9pkXmggApp8nygYLsQwScwAgfgBhGV0aDKQtTA_KgEAAAD__________4JpZIJ2NIJpcISLosQxiXNlY3AyNTZrMaEDBJj7_dLFACaxBfaI8KZTh_SSJUjhyAyfshimvSqo22WDdWRwgiMo",
              // EF
              "enr:-Ku4QHqVeJ8PPICcWk1vSn_XcSkjOkNiTg6Fmii5j6vUQgvzMc9L1goFnLKgXqBJspJjIsB91LTOleFmyWWrFVATGngBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhAMRHkWJc2VjcDI1NmsxoQKLVXFOhp2uX6jeT0DvvDpPcU8FWMjQdR4wMuORMhpX24N1ZHCCIyg",
              "enr:-Ku4QG-2_Md3sZIAUebGYT6g0SMskIml77l6yR-M_JXc-UdNHCmHQeOiMLbylPejyJsdAPsTHJyjJB2sYGDLe0dn8uYBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhBLY-NyJc2VjcDI1NmsxoQORcM6e19T1T9gi7jxEZjk_sjVLGFscUNqAY9obgZaxbIN1ZHCCIyg",
              "enr:-Ku4QPn5eVhcoF1opaFEvg1b6JNFD2rqVkHQ8HApOKK61OIcIXD127bKWgAtbwI7pnxx6cDyk_nI88TrZKQaGMZj0q0Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhDayLMaJc2VjcDI1NmsxoQK2sBOLGcUb4AwuYzFuAVCaNHA-dy24UuEKkeFNgCVCsIN1ZHCCIyg",
              "enr:-Ku4QEWzdnVtXc2Q0ZVigfCGggOVB2Vc1ZCPEc6j21NIFLODSJbvNaef1g4PxhPwl_3kax86YPheFUSLXPRs98vvYsoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhDZBrP2Jc2VjcDI1NmsxoQM6jr8Rb1ktLEsVcKAPa08wCsKUmvoQ8khiOl_SLozf9IN1ZHCCIyg",

              // Nimbus
              "enr:-LK4QA8FfhaAjlb_BXsXxSfiysR7R52Nhi9JBt4F8SPssu8hdE1BXQQEtVDC3qStCW60LSO7hEsVHv5zm8_6Vnjhcn0Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhAN4aBKJc2VjcDI1NmsxoQJerDhsJ-KxZ8sHySMOCmTO6sHM3iCFQ6VMvLTe948MyYN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QKWrXTpV9T78hNG6s8AM6IO4XH9kFT91uZtFg1GcsJ6dKovDOr1jtAAFPnS2lvNltkOGA9k29BUN7lFh_sjuc9QBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhANAdd-Jc2VjcDI1NmsxoQLQa6ai7y9PMN5hpLe5HmiJSlYzMuzP7ZhwRiwHvqNXdoN0Y3CCI4yDdWRwgiOM");
    }

    public Builder applyPraterNetworkDefaults() {
      return reset()
          .constants(PRATER.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(4367322)
          .defaultInitialState(
              "https://github.com/eth2-clients/eth2-testnets/raw/192c1b48ea5ff4adb4e6ef7d2a9e5f82fb5ffd72/shared/prater/genesis.ssz")
          .discoveryBootnodes(
              // Teku bootnode
              "enr:-KK4QH0RsNJmIG0EX9LSnVxMvg-CAOr3ZFF92hunU63uE7wcYBjG1cFbUTvEa5G_4nDJkRhUq9q2ck9xY-VX1RtBsruBtIRldGgykIL0pysBABAg__________-CaWSCdjSCaXCEEnXQ0YlzZWNwMjU2azGhA1grTzOdMgBvjNrk-vqWtTZsYQIi0QawrhoZrsn5Hd56g3RjcIIjKIN1ZHCCIyg",
              // q9f bootnode errai (lighthouse)
              "enr:-LK4QH1xnjotgXwg25IDPjrqRGFnH1ScgNHA3dv1Z8xHCp4uP3N3Jjl_aYv_WIxQRdwZvSukzbwspXZ7JjpldyeVDzMCh2F0dG5ldHOIAAAAAAAAAACEZXRoMpB53wQoAAAQIP__________gmlkgnY0gmlwhIe1te-Jc2VjcDI1NmsxoQOkcGXqbCJYbcClZ3z5f6NWhX_1YPFRYRRWQpJjwSHpVIN0Y3CCIyiDdWRwgiMo",
              // q9f bootnode gudja (teku)
              "enr:-KG4QCIzJZTY_fs_2vqWEatJL9RrtnPwDCv-jRBuO5FQ2qBrfJubWOWazri6s9HsyZdu-fRUfEzkebhf1nvO42_FVzwDhGV0aDKQed8EKAAAECD__________4JpZIJ2NIJpcISHtbYziXNlY3AyNTZrMaED4m9AqVs6F32rSCGsjtYcsyfQE2K8nDiGmocUY_iq-TSDdGNwgiMog3VkcIIjKA",
              // Prysm bootnode #1
              "enr:-Ku4QFmUkNp0g9bsLX2PfVeIyT-9WO-PZlrqZBNtEyofOOfLMScDjaTzGxIb1Ns9Wo5Pm_8nlq-SZwcQfTH2cgO-s88Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDkvpOTAAAQIP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQLV_jMOIxKbjHFKgrkFvwDvpexo6Nd58TK5k7ss4Vt0IoN1ZHCCG1g",
              // Lighthouse bootnode #1
              "enr:-LK4QLINdtobGquK7jukLDAKmsrH2ZuHM4k0TklY5jDTD4ZgfxR9weZmo5Jwu81hlKu3qPAvk24xHGBDjYs4o8f1gZ0Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpB53wQoAAAQIP__________gmlkgnY0gmlwhDRN_P6Jc2VjcDI1NmsxoQJuNujTgsJUHUgVZML3pzrtgNtYg7rQ4K1tkWERgl0DdoN0Y3CCIyiDdWRwgiMo",
              // Nimbus bootstrap nodes
              "enr:-LK4QMzPq4Q7w5R-rnGQDcI8BYky6oPVBGQTbS1JJLVtNi_8PzBLV7Bdzsoame9nJK5bcJYpGHn4SkaDN2CM6tR5G_4Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpB53wQoAAAQIP__________gmlkgnY0gmlwhAN4yvyJc2VjcDI1NmsxoQKa8Qnp_P2clLIP6VqLKOp_INvEjLszalEnW0LoBZo4YYN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QLM_pPHa78R8xlcU_s40Y3XhFjlb3kPddW9lRlY67N5qeFE2Wo7RgzDgRs2KLCXODnacVHMFw1SfpsW3R474RZEBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpB53wQoAAAQIP__________gmlkgnY0gmlwhANBY-yJc2VjcDI1NmsxoQNsZkFXgKbTzuxF7uwxlGauTGJelE6HD269CcFlZ_R7A4N0Y3CCI4yDdWRwgiOM");
    }

    public Builder applyPyrmontNetworkDefaults() {
      return reset()
          .constants(PYRMONT.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(3743587)
          .initialStateFromClasspath("pyrmont-genesis.ssz")
          .discoveryBootnodes(
              // @protolambda bootnode 1
              "enr:-Ku4QOA5OGWObY8ep_x35NlGBEj7IuQULTjkgxC_0G1AszqGEA0Wn2RNlyLFx9zGTNB1gdFBA6ZDYxCgIza1uJUUOj4Dh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDVTPWXAAAgCf__________gmlkgnY0gmlwhDQPSjiJc2VjcDI1NmsxoQM6yTQB6XGWYJbI7NZFBjp4Yb9AYKQPBhVrfUclQUobb4N1ZHCCIyg",
              // @protolambda bootnode 2
              "enr:-Ku4QOksdA2tabOGrfOOr6NynThMoio6Ggka2oDPqUuFeWCqcRM2alNb8778O_5bK95p3EFt0cngTUXm2H7o1jkSJ_8Dh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDVTPWXAAAgCf__________gmlkgnY0gmlwhDaa13aJc2VjcDI1NmsxoQKdNQJvnohpf0VO0ZYCAJxGjT0uwJoAHbAiBMujGjK0SoN1ZHCCIyg",
              // lighthouse bootnode 1
              "enr:-LK4QDiPGwNomqUqNDaM3iHYvtdX7M5qngson6Qb2xGIg1LwC8-Nic0aQwO0rVbJt5xp32sRE3S1YqvVrWO7OgVNv0kBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhBKNA4qJc2VjcDI1NmsxoQKbBS4ROQ_sldJm5tMgi36qm5I5exKJFb4C8dDVS_otAoN0Y3CCIyiDdWRwgiMo",
              // lighthouse bootnode 2
              "enr:-LK4QKAezYUw_R4P1vkzfw9qMQQFJvRQy3QsUblWxIZ4FSduJ2Kueik-qY5KddcVTUsZiEO-oZq0LwbaSxdYf27EjckBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhCOmkIaJc2VjcDI1NmsxoQOQgTD4a8-rESfTdbCG0V6Yz1pUvze02jB2Py3vzGWhG4N0Y3CCIyiDdWRwgiMo",
              // nimbus bootnodes
              "enr:-LK4QK6e16UnTLbi8mJuXHdUSNN8BUcUqhnhyy2bL2_JeX7iMfK9lRbtq8M4kMDGhFwyUQLkHxaDNxS0IPuGS53c1osBh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhAN_0AGJc2VjcDI1NmsxoQOPQv1VILGXeB10y088SeuU6-w8Yh689Fv_uWjhtFqbLIN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QHy7BBDm_mxT0i-EBatHvHGfzNH4BcaAdNguNS8fuaxFDfHP0qVJ9f9A38Q_lMmRUK5PSVHEEoC1mwrExO51T2cBh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhBLGXiqJc2VjcDI1NmsxoQJV51WZn_NLj-0vHAmmZ6tWtzIdu-P_xVr7k9zMEkvaA4N0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QE8QIkEl2k67fj53vn6SgLwj07ElmWZJrIeEpZUfh91oe-PNAlIzeRwI47_wZTK1S2KretXF56XkZqP0v5VlBVUBh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhBLB_8yJc2VjcDI1NmsxoQOEowpACJVUFtcWKhpEk9HlEyY4AEcTB4fONkPEvpeYmIN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QJMF9O8D7hNcGP1Xxh5E09lxUwrzFwokYDxIxUjj_yOnDOWX5HjTDJ4TLZle3HVozC3vJuiZF7jImJMt79t8FuYBh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhBKeOTGJc2VjcDI1NmsxoQJLajuu1S9v-NREUDo5kzUY-ook9CqYLDiHf8z1nMSY1oN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QOTyWBISU1AysyKFt35m_epniDd54LEAsTS2x0OSo1FFTY2ZxETVm43VcZYkmYMQo2ECUAV-0RwAFZcC9_xjRQ4Bh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhAN9a7CJc2VjcDI1NmsxoQJCIUgdHgGuE_k9CVThmgiiXXYW1lfdCZbWHj4p_SAkY4N0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QHOOeQg3HjXSGoXGZPJYeBQ3o9beIGLU1Fxv2PIZX5NEeBLJPB9kpP5xNX_dJ23lsZ0RhBwAxXXTtziC9EMuZuMBh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhCOc7_OJc2VjcDI1NmsxoQPMp2C3hjMNBt6Dr4npyfTG0__GpHtxYXrnho4lT2g2c4N0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QOMpgA7LUM-YUJqWWGX1t01wJkqDMjDJrhxyJHp7ZOCyWkJEYqkHOHYms_K6PI0Ky9Bw57R3ayk9LzE5E9v54WEBh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhBLApGOJc2VjcDI1NmsxoQNGyxAQW2ZUvt_n-MZByer467sfBWclC3pJtvnZDaLhZYN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QL3Y2elAiia5WV18p_pu9t_7syTsZs-rWGD6_IHhiEvBUIzZtT88VMsI-rN8fNSukaHuq7qtDhZwRISdG9O4uQsBh2F0dG5ldHOI__________-EZXRoMpA7CIeVAAAgCf__________gmlkgnY0gmlwhBLGowKJc2VjcDI1NmsxoQK13jMsuO1LbguOsFZ0hxvRe7PT8V1W9qeUMs6fgiwuM4N0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QAtPY91umFgpKmvSEcsDdzXxB6Ss5pa55oqk-t58Uv9qF-B68jEjsN7B_SBGe4qCH1thKwokbS8-zC8Xy-NsED8Bh2F0dG5ldHOI__________-EZXRoMpDzGkhaAAAAAP__________gmlkgnY0gmlwhBKeqH2Jc2VjcDI1NmsxoQIRA0fHAr6eECjjIZZK-GB6dE0awWYtTrOMACfjq12M5oN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QLvxqICUmpMitpwHDwJNEUGj1ecsW_ZlGImx6SwfyFJICV2SO6lYcdxDKHAK0RzdWYo8dGm3tL__NpP_4Afy5psBh2F0dG5ldHOI__________-EZXRoMpDzGkhaAAAAAP__________gmlkgnY0gmlwhBLBEDqJc2VjcDI1NmsxoQJw2JPyabX2G_f9eAkbjhBDshIeUP-eZ-KoMGqFTdxUToN0Y3CCI4yDdWRwgiOM");
    }

    public Builder applyKintsugiNetworkDefaults() {
      return reset()
          .constants(KINTSUGI.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(0)
          .defaultInitialState(
              "https://github.com/eth-clients/merge-testnets/raw/3d07b2914747c4a8bd847a27c272e048cb2d29d8/kintsugi/genesis.ssz")
          .discoveryBootnodes(
              "enr:-Iq4QKuNB_wHmWon7hv5HntHiSsyE1a6cUTK1aT7xDSU_hNTLW3R4mowUboCsqYoh1kN9v3ZoSu_WuvW9Aw0tQ0Dxv6GAXxQ7Nv5gmlkgnY0gmlwhLKAlv6Jc2VjcDI1NmsxoQK6S-Cii_KmfFdUJL2TANL3ksaKUnNXvTCv1tLwXs0QgIN1ZHCCIyk",
              "enr:-KG4QIkKUzDxrv7Xz8u9K9QqoTqEwKKCkLoChxVnfeILU6IdBoWoNOxPGvdl474l1iPFoR8CJUhgGEeO-k1SJ7SJCOEDhGV0aDKQR9ByjGEAAHAKAAAAAAAAAIJpZIJ2NIJpcISl6LnPiXNlY3AyNTZrMaEDprwHy6RKAKJguvGCldiGAI5JDJmQ8TZVnnWQur8zEh2DdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QGJodG8Q0vX5ePXsLsXody1Fbeauyottk3-iAvJ_6XfTVlWGsnfBQPlIOBgexXJqD78bUD5OCnXF5igBBJ4WuboBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpBH0HKMYQAAcAoAAAAAAAAAgmlkgnY0gmlwhEDhBN-Jc2VjcDI1NmsxoQPf98kXQf3Nh3ooc8vBdbUY2WAHR1VDrDhXYTKvRt4n-IhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-KG4QLIhAeEVABV4Id22qEbjemJ0b9JBjRhdYpKN0kvpVi_GbFkQTvAf7-Da-5sW2oNenTW3is_GxLImUCtYzxPMOR4DhGV0aDKQR9ByjGEAAHAKAAAAAAAAAIJpZIJ2NIJpcISl6LF5iXNlY3AyNTZrMaED6XFvht9SUPD0FlYWnjunXhF9FdQMQO56816C9iFNt-WDdGNwgiMog3VkcIIjKA",
              "enr:-LK4QPluMnS3OaiMpy7E0dSF-n7ES9Ort7mpOj85lS_43jGvfuV-SOZyjYNG-WEIT5aOzpWH2vgBbF2MoB94IZdDLxIBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpBH0HKMYQAAcAoAAAAAAAAAgmlkgnY0gmlwhKEjS06Jc2VjcDI1NmsxoQM0uDjlVaZoToQ6ReyUkgFTQizlE6avXGljrWIz9Rf4LoN0Y3CCIyiDdWRwgiMo",
              "enr:-Ku4QM4JL9b3RGfRnnfAY7jqDRiTTGaU2OWk0j4YWbR7N2YHc7RjPGiVERqiWHasIjmMz-No86wsvf4KHuyM4FeRtuMFh2F0dG5ldHOIAAAAAAAAAACEZXRoMpBH0HKMYQAAcAoAAAAAAAAAgmlkgnY0gmlwhKEjQ92Jc2VjcDI1NmsxoQN_UgL8zuTFqyGm5_lKZqUdoHMH2XeU0OvNmZwgycMmSIN0Y3CCIyg");
    }
  }
}
