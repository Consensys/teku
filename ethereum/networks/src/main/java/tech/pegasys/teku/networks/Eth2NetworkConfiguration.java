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

package tech.pegasys.teku.networks;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;
import static tech.pegasys.teku.spec.constants.NetworkConstants.DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;
import static tech.pegasys.teku.spec.networks.Eth2Network.GNOSIS;
import static tech.pegasys.teku.spec.networks.Eth2Network.KILN;
import static tech.pegasys.teku.spec.networks.Eth2Network.LESS_SWIFT;
import static tech.pegasys.teku.spec.networks.Eth2Network.MAINNET;
import static tech.pegasys.teku.spec.networks.Eth2Network.MINIMAL;
import static tech.pegasys.teku.spec.networks.Eth2Network.PRATER;
import static tech.pegasys.teku.spec.networks.Eth2Network.ROPSTEN;
import static tech.pegasys.teku.spec.networks.Eth2Network.SEPOLIA;
import static tech.pegasys.teku.spec.networks.Eth2Network.SWIFT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.ProgressiveBalancesMode;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class Eth2NetworkConfiguration {
  private static final int DEFAULT_STARTUP_TARGET_PEER_COUNT = 5;
  private static final int DEFAULT_STARTUP_TIMEOUT_SECONDS = 30;
  public static final boolean DEFAULT_PROPOSER_BOOST_ENABLED = true;
  public static final boolean DEFAULT_EQUIVOCATING_INDICES_ENABLED = true;
  public static final boolean DEFAULT_FORK_CHOICE_BEFORE_PROPOSING_ENABLED = true;
  public static final ProgressiveBalancesMode DEFAULT_PROGRESSIVE_BALANCES_MODE =
      ProgressiveBalancesMode.DISABLED;

  private final Spec spec;
  private final String constants;
  private final Optional<String> initialState;
  private final boolean usingCustomInitialState;
  private final Optional<String> genesisState;
  private final int startupTargetPeerCount;
  private final int startupTimeoutSeconds;
  private final List<String> discoveryBootnodes;
  private final Optional<UInt64> altairForkEpoch;
  private final Optional<UInt64> bellatrixForkEpoch;
  private final Eth1Address eth1DepositContractAddress;
  private final Optional<UInt64> eth1DepositContractDeployBlock;
  private final boolean proposerBoostEnabled;
  private final boolean equivocatingIndicesEnabled;
  private final boolean forkChoiceBeforeProposingEnabled;
  private final Optional<Bytes32> terminalBlockHashOverride;
  private final Optional<UInt256> totalTerminalDifficultyOverride;
  private final Optional<UInt64> terminalBlockHashEpochOverride;

  private Eth2NetworkConfiguration(
      final Spec spec,
      final String constants,
      final Optional<String> initialState,
      final boolean usingCustomInitialState,
      final Optional<String> genesisState,
      final int startupTargetPeerCount,
      final int startupTimeoutSeconds,
      final List<String> discoveryBootnodes,
      final Eth1Address eth1DepositContractAddress,
      final Optional<UInt64> eth1DepositContractDeployBlock,
      final boolean proposerBoostEnabled,
      final boolean equivocatingIndicesEnabled,
      final boolean forkChoiceBeforeProposingEnabled,
      final Optional<UInt64> altairForkEpoch,
      final Optional<UInt64> bellatrixForkEpoch,
      final Optional<Bytes32> terminalBlockHashOverride,
      final Optional<UInt256> totalTerminalDifficultyOverride,
      final Optional<UInt64> terminalBlockHashEpochOverride) {
    this.spec = spec;
    this.constants = constants;
    this.initialState = initialState;
    this.usingCustomInitialState = usingCustomInitialState;
    this.genesisState = genesisState;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.discoveryBootnodes = discoveryBootnodes;
    this.forkChoiceBeforeProposingEnabled = forkChoiceBeforeProposingEnabled;
    this.altairForkEpoch = altairForkEpoch;
    this.bellatrixForkEpoch = bellatrixForkEpoch;
    this.eth1DepositContractAddress =
        eth1DepositContractAddress == null
            ? spec.getGenesisSpecConfig().getDepositContractAddress()
            : eth1DepositContractAddress;
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
    this.proposerBoostEnabled = proposerBoostEnabled;
    this.equivocatingIndicesEnabled = equivocatingIndicesEnabled;
    this.terminalBlockHashOverride = terminalBlockHashOverride;
    this.totalTerminalDifficultyOverride = totalTerminalDifficultyOverride;
    this.terminalBlockHashEpochOverride = terminalBlockHashEpochOverride;
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

  public Eth1Address getEth1DepositContractAddress() {
    return eth1DepositContractAddress;
  }

  public Optional<UInt64> getEth1DepositContractDeployBlock() {
    return eth1DepositContractDeployBlock;
  }

  public boolean isProposerBoostEnabled() {
    return proposerBoostEnabled;
  }

  public boolean isEquivocatingIndicesEnabled() {
    return equivocatingIndicesEnabled;
  }

  public boolean isForkChoiceBeforeProposingEnabled() {
    return forkChoiceBeforeProposingEnabled;
  }

  public Optional<UInt64> getAltairForkEpoch() {
    return altairForkEpoch;
  }

  public Optional<UInt64> getBellatrixForkEpoch() {
    return bellatrixForkEpoch;
  }

  public Optional<Bytes32> getTerminalBlockHashOverride() {
    return terminalBlockHashOverride;
  }

  public Optional<UInt256> getTotalTerminalDifficultyOverride() {
    return totalTerminalDifficultyOverride;
  }

  public Optional<UInt64> getTerminalBlockHashEpochOverride() {
    return terminalBlockHashEpochOverride;
  }

  @Override
  public String toString() {
    return constants;
  }

  public static class Builder {
    private String constants;
    private Optional<String> initialState = Optional.empty();
    private boolean usingCustomInitialState = false;
    private Optional<String> genesisState = Optional.empty();
    private int startupTargetPeerCount = DEFAULT_STARTUP_TARGET_PEER_COUNT;
    private int startupTimeoutSeconds = DEFAULT_STARTUP_TIMEOUT_SECONDS;
    private List<String> discoveryBootnodes = new ArrayList<>();
    private Eth1Address eth1DepositContractAddress;
    private Optional<UInt64> eth1DepositContractDeployBlock = Optional.empty();
    private boolean proposerBoostEnabled = DEFAULT_PROPOSER_BOOST_ENABLED;
    private boolean equivocatingIndicesEnabled = DEFAULT_EQUIVOCATING_INDICES_ENABLED;
    private boolean forkChoiceBeforeProposingEnabled = DEFAULT_FORK_CHOICE_BEFORE_PROPOSING_ENABLED;
    private ProgressiveBalancesMode progressiveBalancesMode = DEFAULT_PROGRESSIVE_BALANCES_MODE;
    private Optional<UInt64> altairForkEpoch = Optional.empty();
    private Optional<UInt64> bellatrixForkEpoch = Optional.empty();
    private Optional<Bytes32> terminalBlockHashOverride = Optional.empty();
    private Optional<UInt256> totalTerminalDifficultyOverride = Optional.empty();
    private Optional<UInt64> terminalBlockHashEpochOverride = Optional.empty();
    private int safeSlotsToImportOptimistically = DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;
    private Spec spec;

    public void spec(Spec spec) {
      this.spec = spec;
    }

    public Eth2NetworkConfiguration build() {
      checkNotNull(constants, "Missing constants");
      checkArgument(
          safeSlotsToImportOptimistically >= 0, "Safe slots to import optimistically must be >= 0");
      if (spec == null) {
        spec =
            SpecFactory.create(
                constants,
                builder -> {
                  builder.progressiveBalancesMode(progressiveBalancesMode);
                  altairForkEpoch.ifPresent(
                      forkEpoch ->
                          builder.altairBuilder(
                              altairBuilder -> altairBuilder.altairForkEpoch(forkEpoch)));
                  builder.bellatrixBuilder(
                      bellatrixBuilder -> {
                        bellatrixBuilder.safeSlotsToImportOptimistically(
                            safeSlotsToImportOptimistically);
                        bellatrixForkEpoch.ifPresent(bellatrixBuilder::bellatrixForkEpoch);
                        totalTerminalDifficultyOverride.ifPresent(
                            bellatrixBuilder::terminalTotalDifficulty);
                        terminalBlockHashEpochOverride.ifPresent(
                            bellatrixBuilder::terminalBlockHashActivationEpoch);
                        terminalBlockHashOverride.ifPresent(bellatrixBuilder::terminalBlockHash);
                      });
                });
      }
      // if the deposit contract was not set, default from constants
      if (eth1DepositContractAddress == null) {
        eth1DepositContractAddress(spec.getGenesisSpec().getConfig().getDepositContractAddress());
      }
      return new Eth2NetworkConfiguration(
          spec,
          constants,
          initialState,
          usingCustomInitialState,
          genesisState,
          startupTargetPeerCount,
          startupTimeoutSeconds,
          discoveryBootnodes,
          eth1DepositContractAddress,
          eth1DepositContractDeployBlock,
          proposerBoostEnabled,
          equivocatingIndicesEnabled,
          forkChoiceBeforeProposingEnabled,
          altairForkEpoch,
          bellatrixForkEpoch,
          terminalBlockHashOverride,
          totalTerminalDifficultyOverride,
          terminalBlockHashEpochOverride);
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

    public Builder customGenesisState(final String genesisState) {
      this.genesisState = Optional.of(genesisState);
      return this;
    }

    public Builder genesisStateFromClasspath(final String filename) {
      this.genesisState =
          Optional.of(Eth2NetworkConfiguration.class.getResource(filename).toExternalForm());
      return this;
    }

    public Builder startupTargetPeerCount(final int startupTargetPeerCount) {
      if (startupTargetPeerCount < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid startupTargetPeerCount: %d", startupTargetPeerCount));
      }
      this.startupTargetPeerCount = startupTargetPeerCount;
      return this;
    }

    public Builder startupTimeoutSeconds(final int startupTimeoutSeconds) {
      if (startupTimeoutSeconds < 0) {
        throw new InvalidConfigurationException(
            String.format("Invalid startupTimeoutSeconds: %d", startupTimeoutSeconds));
      }
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

    public Builder progressiveBalancesEnabled(
        final ProgressiveBalancesMode progressiveBalancesMode) {
      this.progressiveBalancesMode = progressiveBalancesMode;
      return this;
    }

    public Builder equivocatingIndicesEnabled(final boolean equivocatingIndicesEnabled) {
      this.equivocatingIndicesEnabled = equivocatingIndicesEnabled;
      return this;
    }

    public Builder forkChoiceBeforeProposingEnabled(
        final boolean forkChoiceBeforeProposingEnabled) {
      this.forkChoiceBeforeProposingEnabled = forkChoiceBeforeProposingEnabled;
      return this;
    }

    public Builder altairForkEpoch(final UInt64 altairForkEpoch) {
      this.altairForkEpoch = Optional.of(altairForkEpoch);
      return this;
    }

    public Builder bellatrixForkEpoch(final UInt64 bellatrixForkEpoch) {
      this.bellatrixForkEpoch = Optional.of(bellatrixForkEpoch);
      return this;
    }

    public Builder safeSlotsToImportOptimistically(final int safeSlotsToImportOptimistically) {
      if (safeSlotsToImportOptimistically < 0) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid safeSlotsToImportOptimistically: %d", safeSlotsToImportOptimistically));
      }
      this.safeSlotsToImportOptimistically = safeSlotsToImportOptimistically;
      return this;
    }

    public Builder totalTerminalDifficultyOverride(final UInt256 totalTerminalDifficultyOverride) {
      this.totalTerminalDifficultyOverride = Optional.of(totalTerminalDifficultyOverride);
      return this;
    }

    public Builder terminalBlockHashOverride(final Bytes32 terminalBlockHashOverride) {
      this.terminalBlockHashOverride = Optional.of(terminalBlockHashOverride);
      return this;
    }

    public Builder terminalBlockHashEpochOverride(final UInt64 terminalBlockHashEpochOverride) {
      this.terminalBlockHashEpochOverride = Optional.of(terminalBlockHashEpochOverride);
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
        case PRATER:
          return applyPraterNetworkDefaults();
        case ROPSTEN:
          return applyRopstenNetworkDefaults();
        case SEPOLIA:
          return applySepoliaNetworkDefaults();
        case KILN:
          return applyKilnNetworkDefaults();
        case GNOSIS:
          return applyGnosisNetworkDefaults();
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
      genesisState = Optional.empty();
      startupTargetPeerCount = DEFAULT_STARTUP_TARGET_PEER_COUNT;
      startupTimeoutSeconds = DEFAULT_STARTUP_TIMEOUT_SECONDS;
      discoveryBootnodes = new ArrayList<>();
      eth1DepositContractAddress = null;
      eth1DepositContractDeployBlock = Optional.empty();
      forkChoiceBeforeProposingEnabled = DEFAULT_FORK_CHOICE_BEFORE_PROPOSING_ENABLED;
      progressiveBalancesMode = DEFAULT_PROGRESSIVE_BALANCES_MODE;
      return this;
    }

    public Builder applyTestnetDefaults() {
      return reset().progressiveBalancesEnabled(ProgressiveBalancesMode.USED);
    }

    public Builder applyMinimalNetworkDefaults() {
      return applyTestnetDefaults().constants(MINIMAL.configName()).startupTargetPeerCount(0);
    }

    public Builder applySwiftNetworkDefaults() {
      return applyTestnetDefaults().constants(SWIFT.configName()).startupTargetPeerCount(0);
    }

    public Builder applyLessSwiftNetworkDefaults() {
      return applyTestnetDefaults().constants(LESS_SWIFT.configName()).startupTargetPeerCount(0);
    }

    public Builder applyMainnetNetworkDefaults() {
      return reset()
          .constants(MAINNET.configName())
          .initialStateFromClasspath("mainnet-genesis.ssz")
          .genesisStateFromClasspath("mainnet-genesis.ssz")
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
      return applyTestnetDefaults()
          .constants(PRATER.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(4367322)
          .defaultInitialState(
              "https://github.com/eth2-clients/eth2-testnets/raw/192c1b48ea5ff4adb4e6ef7d2a9e5f82fb5ffd72/shared/prater/genesis.ssz")
          .customGenesisState(
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

    public Builder applyRopstenNetworkDefaults() {
      return applyTestnetDefaults()
          .constants(ROPSTEN.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(12269949)
          .defaultInitialState(
              "https://github.com/eth-clients/merge-testnets/raw/5b1b44aa912dd3433ba30d381345659c53918955/ropsten-beacon-chain/genesis.ssz")
          .customGenesisState(
              "https://github.com/eth-clients/merge-testnets/raw/5b1b44aa912dd3433ba30d381345659c53918955/ropsten-beacon-chain/genesis.ssz")
          .discoveryBootnodes(
              // Teku bootnode
              "enr:-KG4QMJSJ7DHk6v2p-W8zQ3Xv7FfssZ_1E3p2eY6kN13staMObUonAurqyWhODoeY6edXtV8e9eL9RnhgZ9va2SMDRQMhGV0aDKQS-iVMYAAAHD0AQAAAAAAAIJpZIJ2NIJpcIQDhAAhiXNlY3AyNTZrMaEDXBVUZhhmdy1MYor1eGdRJ4vHYghFKDgjyHgt6sJ-IlCDdGNwgiMog3VkcIIjKA",
              // EF bootnodes
              "enr:-Iq4QMCTfIMXnow27baRUb35Q8iiFHSIDBJh6hQM5Axohhf4b6Kr_cOCu0htQ5WvVqKvFgY28893DHAg8gnBAXsAVqmGAX53x8JggmlkgnY0gmlwhLKAlv6Jc2VjcDI1NmsxoQK6S-Cii_KmfFdUJL2TANL3ksaKUnNXvTCv1tLwXs0QgIN1ZHCCIyk",
              "enr:-L64QLKGahA2AQwFUrX1rpad2zfSgtSwdFUSAH2vLwYkFaGIFtaCKwllLVeRyaxm_EiJA_AnIut11VBWssanktwEzmOCAQyHYXR0bmV0c4j__________4RldGgykDz6O6yAAABx__________-CaWSCdjSCaXCEojetBIlzZWNwMjU2azGhAmIKKR-unrW_VMUSW9ctYQVt4rYRD7HmQ48xkM-yNyxKiHN5bmNuZXRzBoN0Y3CCIyiDdWRwgiMo",
              "enr:-Ly4QBKxH0EE-Z1VHY7GbxgV6axbnD0jJoeHsj0tOY7DeOyqW1GhIrgEyxb6Rl_rS10qrgrBtJOI8Yt3bd7rXHk3GBlsh2F0dG5ldHOI__________-EZXRoMpA8-jusgAAAcf__________gmlkgnY0gmlwhKfr5v6Jc2VjcDI1NmsxoQPmax4TV2mAzlHJV1J0l-6tQkHui-iIJ7mcCiyE9YREMohzeW5jbmV0cwyDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QKEbHPy_jbA3xy_ZR04LVyJ8x2vGoVSUZ2QvoLHTHiCoeWraxyWwl3MhRupM0aXbr8U_OBJ2GkqZAxbY1I5boJtRh2F0dG5ldHOI__________-EZXRoMpA8-jusgAAAcf__________gmlkgnY0gmlwhAWhjUqJc2VjcDI1NmsxoQLTpctSHKHGN7nGTQmCP4-PSTtSYcppPqGTkvCbR-iUAIhzeW5jbmV0cw-DdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QBPqYWxS4x6UuU2IbDFGRYpMj-z1-rtoRFXGw6uJ0fQ0Rix0Vtak2dSl0SO0w50WKTSmFubSpHkxLmeHJ7kZ-S1Rh2F0dG5ldHOI__________-EZXRoMpA8-jusgAAAcf__________gmlkgnY0gmlwhAWhhAmJc2VjcDI1NmsxoQPpPhUwcdObdY1ERHpiR2X7vaAZ05xwHs1uLEIUjea044hzeW5jbmV0cwmDdGNwgiMog3VkcIIjKA",
              "enr:-L64QOfVzGCvyI73fW6IFzugYZr0QfYItn0j19P8zgbmgFdJKIdFLUp7lynEwy0U9YgFhKF4NF4PumailtLAmUv4bM2CApmHYXR0bmV0c4j__________4RldGgykDz6O6yAAABx__________-CaWSCdjSCaXCEh7WWsIlzZWNwMjU2azGhAsMdsKC6SYYlIN7huLAhhxxRzOJOka7gpfnFZ2Auq0kiiHN5bmNuZXRzBoN0Y3CCIyiDdWRwgiMo",
              "enr:-L64QNCPH53Je5MJ_TbKHnPSqKO1XZtywJK4gF4UA3UcQyHZEJKpcPHbXYnibrDUB7XEbZ1NW2INUK9uSD2ecOVVXfmCARCHYXR0bmV0c4j__________4RldGgykDz6O6yAAABx__________-CaWSCdjSCaXCEQWz6UYlzZWNwMjU2azGhAovELkeemN_zzm-wEyQJo8p0DgiM4o32zSDQkiR1LOIIiHN5bmNuZXRzA4N0Y3CCIyiDdWRwgiMo",
              "enr:-L64QESLzEbBz8I38oLg1PX1ATTGZUQ5KUadgy4UAZqSsutLdW4rSASTCFKL0ssqmq0lUXEF7aP-4gvuDB9IvVb42syCAx6HYXR0bmV0c4j__________4RldGgykDz6O6yAAABx__________-CaWSCdjSCaXCEw8ndRIlzZWNwMjU2azGhAirZcWMVxDPb5T4exQOfGRxIHICCcAxSpi1_mCaehUgyiHN5bmNuZXRzDIN0Y3CCIyiDdWRwgiMo",
              "enr:-L24QN8Y-8WTMuwF8ePM2wOjzlMdLOYwl3QJmXs1KILv6ZZwVovYC822cb-nh1R2U3Hi6AiHS5SsINNrHzLQVzFrsduBg4dhdHRuZXRziP__________hGV0aDKQPPo7rIAAAHH__________4JpZIJ2NIJpcITDyd1CiXNlY3AyNTZrMaECroNSTYv0Gy272DBfn-in38LLREpMwzOP18LoLrYJ4jeIc3luY25ldHMJg3RjcIIjKIN1ZHCCIyg");
    }

    private Builder applySepoliaNetworkDefaults() {
      return applyTestnetDefaults()
          .constants(SEPOLIA.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(1273020)
          .defaultInitialState(
              "https://github.com/eth-clients/merge-testnets/raw/9c873ab67b902aa676370a549129e5e91013afa3/sepolia/genesis.ssz")
          .customGenesisState(
              "https://github.com/eth-clients/merge-testnets/raw/9c873ab67b902aa676370a549129e5e91013afa3/sepolia/genesis.ssz")
          .discoveryBootnodes(
              // EF bootnodes
              "enr:-Iq4QMCTfIMXnow27baRUb35Q8iiFHSIDBJh6hQM5Axohhf4b6Kr_cOCu0htQ5WvVqKvFgY28893DHAg8gnBAXsAVqmGAX53x8JggmlkgnY0gmlwhLKAlv6Jc2VjcDI1NmsxoQK6S-Cii_KmfFdUJL2TANL3ksaKUnNXvTCv1tLwXs0QgIN1ZHCCIyk",
              "enr:-KG4QE5OIg5ThTjkzrlVF32WT_-XT14WeJtIz2zoTqLLjQhYAmJlnk4ItSoH41_2x0RX0wTFIe5GgjRzU2u7Q1fN4vADhGV0aDKQqP7o7pAAAHAyAAAAAAAAAIJpZIJ2NIJpcISlFsStiXNlY3AyNTZrMaEC-Rrd_bBZwhKpXzFCrStKp1q_HmGOewxY3KwM8ofAj_ODdGNwgiMog3VkcIIjKA",
              // Teku bootnode
              "enr:-Ly4QFoZTWR8ulxGVsWydTNGdwEESueIdj-wB6UmmjUcm-AOPxnQi7wprzwcdo7-1jBW_JxELlUKJdJES8TDsbl1EdNlh2F0dG5ldHOI__78_v2bsV-EZXRoMpA2-lATkAAAcf__________gmlkgnY0gmlwhBLYJjGJc2VjcDI1NmsxoQI0gujXac9rMAb48NtMqtSTyHIeNYlpjkbYpWJw46PmYYhzeW5jbmV0cw-DdGNwgiMog3VkcIIjKA",
              // Another bootnode
               "enr:-L64QC9Hhov4DhQ7mRukTOz4_jHm4DHlGL726NWH4ojH1wFgEwSin_6H95Gs6nW2fktTWbPachHJ6rUFu0iJNgA0SB2CARqHYXR0bmV0c4j__________4RldGgykDb6UBOQAABx__________-CaWSCdjSCaXCEA-2vzolzZWNwMjU2azGhA17lsUg60R776rauYMdrAz383UUgESoaHEzMkvm4K6k6iHN5bmNuZXRzD4N0Y3CCIyiDdWRwgiMo");
    }

    public Builder applyKilnNetworkDefaults() {
      return applyTestnetDefaults()
          .forkChoiceBeforeProposingEnabled(true)
          .constants(KILN.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(0)
          .defaultInitialState(
              "https://github.com/eth-clients/merge-testnets/raw/a44b13a8d495984f4bc9770348a1c451f615be76/kiln/genesis.ssz")
          .customGenesisState(
              "https://github.com/eth-clients/merge-testnets/raw/a44b13a8d495984f4bc9770348a1c451f615be76/kiln/genesis.ssz")
          .discoveryBootnodes(
              "enr:-Iq4QMCTfIMXnow27baRUb35Q8iiFHSIDBJh6hQM5Axohhf4b6Kr_cOCu0htQ5WvVqKvFgY28893DHAg8gnBAXsAVqmGAX53x8JggmlkgnY0gmlwhLKAlv6Jc2VjcDI1NmsxoQK6S-Cii_KmfFdUJL2TANL3ksaKUnNXvTCv1tLwXs0QgIN1ZHCCIyk",
              "enr:-KG4QFkPJUFWuONp5grM94OJvNht9wX6N36sA4wqucm6Z02ECWBQRmh6AzndaLVGYBHWre67mjK-E0uKt2CIbWrsZ_8DhGV0aDKQc6pfXHAAAHAyAAAAAAAAAIJpZIJ2NIJpcISl6LTmiXNlY3AyNTZrMaEDHlSNOgYrNWP8_l_WXqDMRvjv6gUAvHKizfqDDVc8feaDdGNwgiMog3VkcIIjKA",
              "enr:-MK4QI-wkVW1PxL4ksUM4H_hMgTTwxKMzvvDMfoiwPBuRxcsGkrGPLo4Kho3Ri1DEtJG4B6pjXddbzA9iF2gVctxv42GAX9v5WG5h2F0dG5ldHOIAAAAAAAAAACEZXRoMpBzql9ccAAAcDIAAAAAAAAAgmlkgnY0gmlwhKRcjMiJc2VjcDI1NmsxoQK1fc46pmVHKq8HNYLkSVaUv4uK2UBsGgjjGWU6AAhAY4hzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA");
    }

    public Builder applyGnosisNetworkDefaults() {
      return reset()
          .constants(GNOSIS.configName())
          .initialStateFromClasspath("gnosis-genesis.ssz")
          .genesisStateFromClasspath("gnosis-genesis.ssz")
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(19469077)
          .discoveryBootnodes(
              // Gnosis Chain Team bootnodes
              "enr:-IS4QGmLwm7gFd0L0CEisllrb1op3v-wAGSc7_pwSMGgN3bOS9Fz7m1dWbwuuPHKqeETz9MbhjVuoWk0ohkyRv98kVoBgmlkgnY0gmlwhGjtlgaJc2VjcDI1NmsxoQLMdh0It9fJbuiLydZ9fpF6MRzgNle0vODaDiMqhbC7WIN1ZHCCIyg",
              "enr:-IS4QFUVG3dvLPCUEI7ycRvFm0Ieg_ITa5tALmJ9LI7dJ6ieT3J4fF9xLRjOoB4ApV-Rjp7HeLKzyTWG1xRdbFBNZPQBgmlkgnY0gmlwhErP5weJc2VjcDI1NmsxoQOBbaJBvx0-w_pyZUhQl9A510Ho2T0grE0K8JevzES99IN1ZHCCIyg",
              "enr:-Ku4QOQk8V-Hu2gxFzRXmLYIO4AvWDZhoMFwTf3n3DYm_mbsWv0ZitoqiN6JZUUj6Li6e1Jk1w2zFSVHKPMUP1g5tsgBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD5Jd3FAAAAZP__________gmlkgnY0gmlwhC1PTpmJc2VjcDI1NmsxoQL1Ynt5PoA0UOcHa1Rfn98rmnRlLzNuWTePPP4m4qHVroN1ZHCCKvg",
              "enr:-Ku4QFaTwgoms-EiiRIfHUH3FXprWUFgjHg4UuWvilqoUQtDbmTszVIxUEOwQUmA2qkiP-T9wXjc_rVUuh9cU7WgwbgBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD5Jd3FAAAAZP__________gmlkgnY0gmlwhC0hBmCJc2VjcDI1NmsxoQOpsg1XCrXmCwZKcSTcycLwldoKUMHPUpMEVGeg_EEhuYN1ZHCCKvg");
    }
  }
}
