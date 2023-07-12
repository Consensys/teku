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
import static tech.pegasys.teku.spec.networks.Eth2Network.CHIADO;
import static tech.pegasys.teku.spec.networks.Eth2Network.GNOSIS;
import static tech.pegasys.teku.spec.networks.Eth2Network.LESS_SWIFT;
import static tech.pegasys.teku.spec.networks.Eth2Network.MAINNET;
import static tech.pegasys.teku.spec.networks.Eth2Network.MINIMAL;
import static tech.pegasys.teku.spec.networks.Eth2Network.PRATER;
import static tech.pegasys.teku.spec.networks.Eth2Network.SEPOLIA;
import static tech.pegasys.teku.spec.networks.Eth2Network.SWIFT;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class Eth2NetworkConfiguration {
  private static final int DEFAULT_STARTUP_TARGET_PEER_COUNT = 5;
  private static final int DEFAULT_STARTUP_TIMEOUT_SECONDS = 30;

  public static final boolean DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED = true;

  public static final String INITIAL_STATE_URL_PATH = "eth/v2/debug/beacon/states/finalized";

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
  private final Optional<UInt64> capellaForkEpoch;
  private final Optional<UInt64> denebForkEpoch;
  private final Eth1Address eth1DepositContractAddress;
  private final Optional<UInt64> eth1DepositContractDeployBlock;
  private final Optional<String> trustedSetup;

  private final boolean forkChoiceUpdateHeadOnBlockImportEnabled;
  private final Optional<Bytes32> terminalBlockHashOverride;
  private final Optional<UInt256> totalTerminalDifficultyOverride;
  private final Optional<UInt64> terminalBlockHashEpochOverride;
  private final Optional<Eth2Network> eth2Network;

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
      final Optional<String> trustedSetup,
      final boolean forkChoiceUpdateHeadOnBlockImportEnabled,
      final Optional<UInt64> altairForkEpoch,
      final Optional<UInt64> bellatrixForkEpoch,
      final Optional<UInt64> capellaForkEpoch,
      final Optional<UInt64> denebForkEpoch,
      final Optional<Bytes32> terminalBlockHashOverride,
      final Optional<UInt256> totalTerminalDifficultyOverride,
      final Optional<UInt64> terminalBlockHashEpochOverride,
      final Optional<Eth2Network> eth2Network) {
    this.spec = spec;
    this.constants = constants;
    this.initialState = initialState;
    this.usingCustomInitialState = usingCustomInitialState;
    this.genesisState = genesisState;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.discoveryBootnodes = discoveryBootnodes;
    this.altairForkEpoch = altairForkEpoch;
    this.bellatrixForkEpoch = bellatrixForkEpoch;
    this.capellaForkEpoch = capellaForkEpoch;
    this.denebForkEpoch = denebForkEpoch;
    this.eth1DepositContractAddress =
        eth1DepositContractAddress == null
            ? spec.getGenesisSpecConfig().getDepositContractAddress()
            : eth1DepositContractAddress;
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
    this.trustedSetup = trustedSetup;
    this.forkChoiceUpdateHeadOnBlockImportEnabled = forkChoiceUpdateHeadOnBlockImportEnabled;
    this.terminalBlockHashOverride = terminalBlockHashOverride;
    this.totalTerminalDifficultyOverride = totalTerminalDifficultyOverride;
    this.terminalBlockHashEpochOverride = terminalBlockHashEpochOverride;
    this.eth2Network = eth2Network;
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

  public Optional<String> getTrustedSetup() {
    return trustedSetup;
  }

  public boolean isForkChoiceUpdateHeadOnBlockImportEnabled() {
    return forkChoiceUpdateHeadOnBlockImportEnabled;
  }

  public Optional<UInt64> getForkEpoch(final SpecMilestone specMilestone) {
    switch (specMilestone) {
      case ALTAIR:
        return altairForkEpoch;
      case BELLATRIX:
        return bellatrixForkEpoch;
      case CAPELLA:
        return capellaForkEpoch;
      case DENEB:
        return denebForkEpoch;
      default:
        return Optional.empty();
    }
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

  public Optional<Eth2Network> getEth2Network() {
    return eth2Network;
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
    private Optional<String> trustedSetup = Optional.empty();
    private Optional<UInt64> altairForkEpoch = Optional.empty();
    private Optional<UInt64> bellatrixForkEpoch = Optional.empty();
    private Optional<UInt64> capellaForkEpoch = Optional.empty();
    private Optional<UInt64> denebForkEpoch = Optional.empty();
    private Optional<Bytes32> terminalBlockHashOverride = Optional.empty();
    private Optional<UInt256> totalTerminalDifficultyOverride = Optional.empty();
    private Optional<UInt64> terminalBlockHashEpochOverride = Optional.empty();
    private int safeSlotsToImportOptimistically = DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;
    private Spec spec;
    private boolean forkChoiceUpdateHeadOnBlockImportEnabled =
        DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED;

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
                  builder.capellaBuilder(
                      capellaBuilder ->
                          capellaForkEpoch.ifPresent(capellaBuilder::capellaForkEpoch));
                  builder.denebBuilder(
                      denebBuilder -> {
                        denebForkEpoch.ifPresent(denebBuilder::denebForkEpoch);
                        trustedSetup.ifPresent(denebBuilder::trustedSetupPath);
                      });
                });
      }
      // if the deposit contract was not set, default from constants
      if (eth1DepositContractAddress == null) {
        eth1DepositContractAddress(spec.getGenesisSpec().getConfig().getDepositContractAddress());
      }
      final Optional<Eth2Network> eth2Network = Eth2Network.fromStringLenient(constants);
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
          trustedSetup,
          forkChoiceUpdateHeadOnBlockImportEnabled,
          altairForkEpoch,
          bellatrixForkEpoch,
          capellaForkEpoch,
          denebForkEpoch,
          terminalBlockHashOverride,
          totalTerminalDifficultyOverride,
          terminalBlockHashEpochOverride,
          eth2Network);
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
          Optional.ofNullable(Eth2NetworkConfiguration.class.getResource(filename))
              .map(URL::toExternalForm);
      return this;
    }

    public Builder customGenesisState(final String genesisState) {
      this.genesisState = Optional.of(genesisState);
      return this;
    }

    public Builder genesisStateFromClasspath(final String filename) {
      this.genesisState =
          Optional.ofNullable(Eth2NetworkConfiguration.class.getResource(filename))
              .map(URL::toExternalForm);
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

    public Builder trustedSetup(final String trustedSetup) {
      this.trustedSetup = Optional.of(trustedSetup);
      return this;
    }

    public Builder trustedSetupFromClasspath(final String filename) {
      this.trustedSetup =
          Optional.ofNullable(Eth2NetworkConfiguration.class.getResource(filename))
              .map(URL::toExternalForm);
      return this;
    }

    public Builder forkChoiceUpdateHeadOnBlockImportEnabled(
        final boolean forkChoiceUpdateHeadOnBlockImportEnabled) {
      this.forkChoiceUpdateHeadOnBlockImportEnabled = forkChoiceUpdateHeadOnBlockImportEnabled;
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

    public Builder capellaForkEpoch(final UInt64 capellaForkEpoch) {
      this.capellaForkEpoch = Optional.of(capellaForkEpoch);
      return this;
    }

    public Builder denebForkEpoch(final UInt64 denebForkEpoch) {
      this.denebForkEpoch = Optional.of(denebForkEpoch);
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
          .ifPresentOrElse(
              this::applyNetworkDefaults, () -> resetAndApplyBasicDefaults(networkName));
      return this;
    }

    private Builder resetAndApplyBasicDefaults(final String networkName) {
      return reset().trustedSetupFromClasspath("mainnet-trusted-setup.txt").constants(networkName);
    }

    public Builder applyNetworkDefaults(final Eth2Network network) {
      switch (network) {
        case MAINNET:
          return applyMainnetNetworkDefaults();
        case MINIMAL:
          return applyMinimalNetworkDefaults();
        case PRATER:
          return applyPraterNetworkDefaults();
        case SEPOLIA:
          return applySepoliaNetworkDefaults();
        case GNOSIS:
          return applyGnosisNetworkDefaults();
        case CHIADO:
          return applyChiadoNetworkDefaults();
        case SWIFT:
          return applySwiftNetworkDefaults();
        case LESS_SWIFT:
          return applyLessSwiftNetworkDefaults();
        default:
          return resetAndApplyBasicDefaults(network.configName());
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
      trustedSetup = Optional.empty();
      return this;
    }

    public Builder applyTestnetDefaults() {
      return reset();
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
          .trustedSetupFromClasspath("mainnet-trusted-setup.txt")
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
              "enr:-Le4QPUXJS2BTORXxyx2Ia-9ae4YqA_JWX3ssj4E_J-3z1A-HmFGrU8BpvpqhNabayXeOZ2Nq_sbeDgtzMJpLLnXFgAChGV0aDKQtTA_KgEAAAAAIgEAAAAAAIJpZIJ2NIJpcISsaa0Zg2lwNpAkAIkHAAAAAPA8kv_-awoTiXNlY3AyNTZrMaEDHAD2JKYevx89W0CcFJFiskdcEzkH_Wdv9iW42qLK79ODdWRwgiMohHVkcDaCI4I",
              "enr:-Le4QLHZDSvkLfqgEo8IWGG96h6mxwe_PsggC20CL3neLBjfXLGAQFOPSltZ7oP6ol54OvaNqO02Rnvb8YmDR274uq8ChGV0aDKQtTA_KgEAAAAAIgEAAAAAAIJpZIJ2NIJpcISLosQxg2lwNpAqAX4AAAAAAPA8kv_-ax65iXNlY3AyNTZrMaEDBJj7_dLFACaxBfaI8KZTh_SSJUjhyAyfshimvSqo22WDdWRwgiMohHVkcDaCI4I",
              "enr:-Le4QH6LQrusDbAHPjU_HcKOuMeXfdEB5NJyXgHWFadfHgiySqeDyusQMvfphdYWOzuSZO9Uq2AMRJR5O4ip7OvVma8BhGV0aDKQtTA_KgEAAAAAIgEAAAAAAIJpZIJ2NIJpcISLY9ncg2lwNpAkAh8AgQIBAAAAAAAAAAmXiXNlY3AyNTZrMaECDYCZTZEksF-kmgPholqgVt8IXr-8L7Nu7YrZ7HUpgxmDdWRwgiMohHVkcDaCI4I",
              "enr:-Le4QIqLuWybHNONr933Lk0dcMmAB5WgvGKRyDihy1wHDIVlNuuztX62W51voT4I8qD34GcTEOTmag1bcdZ_8aaT4NUBhGV0aDKQtTA_KgEAAAAAIgEAAAAAAIJpZIJ2NIJpcISLY04ng2lwNpAkAh8AgAIBAAAAAAAAAA-fiXNlY3AyNTZrMaEDscnRV6n1m-D9ID5UsURk0jsoKNXt1TIrj8uKOGW6iluDdWRwgiMohHVkcDaCI4I",
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
              "enr:-Ly4QFPk-cTMxZ3jWTafiNblEZkQIXGF2aVzCIGW0uHp6KaEAvBMoctE8S7YU0qZtuS7By0AA4YMfKoN9ls_GJRccVpFh2F0dG5ldHOI__________-EZXRoMpCC9KcrAgAQIIS2AQAAAAAAgmlkgnY0gmlwhKh3joWJc2VjcDI1NmsxoQKrxz8M1IHwJqRIpDqdVW_U1PeixMW5SfnBD-8idYIQrIhzeW5jbmV0cw-DdGNwgiMog3VkcIIjKA",
              // Lighthouse bootnode #2
              "enr:-L64QJmwSDtaHVgGiqIxJWUtxWg6uLCipsms6j-8BdsOJfTWAs7CLF9HJnVqFE728O-JYUDCxzKvRdeMqBSauHVCMdaCAVWHYXR0bmV0c4j__________4RldGgykIL0pysCABAghLYBAAAAAACCaWSCdjSCaXCEQWxOdolzZWNwMjU2azGhA7Qmod9fK86WidPOzLsn5_8QyzL7ZcJ1Reca7RnD54vuiHN5bmNuZXRzD4N0Y3CCIyiDdWRwgiMo",
              // Nimbus bootstrap nodes
              "enr:-LK4QMzPq4Q7w5R-rnGQDcI8BYky6oPVBGQTbS1JJLVtNi_8PzBLV7Bdzsoame9nJK5bcJYpGHn4SkaDN2CM6tR5G_4Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpB53wQoAAAQIP__________gmlkgnY0gmlwhAN4yvyJc2VjcDI1NmsxoQKa8Qnp_P2clLIP6VqLKOp_INvEjLszalEnW0LoBZo4YYN0Y3CCI4yDdWRwgiOM",
              "enr:-LK4QLM_pPHa78R8xlcU_s40Y3XhFjlb3kPddW9lRlY67N5qeFE2Wo7RgzDgRs2KLCXODnacVHMFw1SfpsW3R474RZEBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpB53wQoAAAQIP__________gmlkgnY0gmlwhANBY-yJc2VjcDI1NmsxoQNsZkFXgKbTzuxF7uwxlGauTGJelE6HD269CcFlZ_R7A4N0Y3CCI4yDdWRwgiOM");
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

    public Builder applyGnosisNetworkDefaults() {
      return reset()
          .constants(GNOSIS.configName())
          .initialStateFromClasspath("gnosis-genesis.ssz")
          .genesisStateFromClasspath("gnosis-genesis.ssz")
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(19469077)
          .discoveryBootnodes(
              // Gnosis Chain Team bootnodes
              "enr:-Ly4QMU1y81COwm1VZgxGF4_eZ21ub9-GHF6dXZ29aEJ0oZpcV2Rysw-viaEKfpcpu9ZarILJLxFZjcKOjE0Sybs3MQBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhANLnx-Jc2VjcDI1NmsxoQKoaYT8I-wf2I_f_ii6EgoSSXj5T3bhiDyW-7ZLsY3T64hzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QBf76jLiCA_pDXoZjhyRbuwzFOscFY-MIKkPnmHPQbvaKhIDZutfe38G9ibzgQP0RKrTo3vcWOy4hf_8wOZ-U5MBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhBLGgjaJc2VjcDI1NmsxoQLGeo0Q4lDvxIjHjnkAqEuETTaFIjsNrEcSpdDhcHXWFYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QLjZUWdqUO_RwyDqCAccIK5-MbLRD6A2c7oBuVbBgBnWDkEf0UKJVAaJqi2pO101WVQQLYSnYgz1Q3pRhYdrlFoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhANA8sSJc2VjcDI1NmsxoQK4TC_EK1jSs0VVPUpOjIo1rhJmff2SLBPFOWSXMwdLVYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QKwX2rTFtKWKQHSGQFhquxsxL1jewO8JB1MG-jgHqAZVFWxnb3yMoQqnYSV1bk25-_jiLuhIulxar3RBWXEDm6EBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhAN-qZeJc2VjcDI1NmsxoQI7EPGMpecl0QofLp4Wy_lYNCCChUFEH6kY7k-oBGkPFIhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QPoChSQTleJROee1-k-4HOEgKqL9kLksE-tEiVqcY9kwF9V53aBg-MruD7Yx4Aks3LAeJpKXAS4ntMrIdqvQYc8Ch2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhGsWBHiJc2VjcDI1NmsxoQKwGQrwOSBJB_DtQOkFZVAY4YQfMAbUVxFpL5WgrzEddYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QBbaKRSX4SncCOxTTL611Kxlz-zYFrIn-k_63jGIPK_wbvFghVUHJICPCxufgTX5h79jvgfPr-2hEEQEdziGQ5MCh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhAMazo6Jc2VjcDI1NmsxoQKt-kbM9isuWp8djhyEq6-4MLv1Sy7dOXeMOMdPgwu9LohzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QKJ5BzgFyJ6BaTlGY0C8ROzl508U3GA6qxdG5Gn2hxdke6nQO187pYlLvhp82Dez4PQn436Fts1F0WAm-_5l2LACh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhA-YLVKJc2VjcDI1NmsxoQI8_Lvr6p_TkcAu8KorKacfUEnoOon0tdO0qWhriPdBP4hzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QJMtoiX2bPnVbiQOJCLbtUlqdqZk7kCJQln_W1bp1vOHcxWowE-iMXkKC4_uOb0o73wAW71WYi80Dlsg-7a5wiICh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhDbP3KmJc2VjcDI1NmsxoQNvcfKYUqcemLFlpKxl7JcQJwQ3L9unYL44gY2aEiRnI4hzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA");
    }

    public Builder applyChiadoNetworkDefaults() {
      return reset()
          .constants(CHIADO.configName())
          .initialStateFromClasspath("chiado-genesis.ssz")
          .genesisStateFromClasspath("chiado-genesis.ssz")
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(155435)
          .discoveryBootnodes(
              // chiado-lighthouse-0
              "enr:-L64QOijsdi9aVIawMb5h5PWueaPM9Ai6P17GNPFlHzz7MGJQ8tFMdYrEx8WQitNKLG924g2Q9cCdzg54M0UtKa3QIKCMxaHYXR0bmV0c4j__________4RldGgykDE2cEMCAABv__________-CaWSCdjSCaXCEi5AaWYlzZWNwMjU2azGhA8CjTkD4m1s8FbKCN18LgqlYcE65jrT148vFtwd9U62SiHN5bmNuZXRzD4N0Y3CCIyiDdWRwgiMo",
              // chiado-lighthouse-1
              "enr:-L64QKYKGQj5ybkfBxyFU5IEVzP7oJkGHJlie4W8BCGAYEi4P0mmMksaasiYF789mVW_AxYVNVFUjg9CyzmdvpyWQ1KCMlmHYXR0bmV0c4j__________4RldGgykDE2cEMCAABv__________-CaWSCdjSCaXCEi5CtNolzZWNwMjU2azGhAuA7BAwIijy1z81AO9nz_MOukA1ER68rGA67PYQ5pF1qiHN5bmNuZXRzD4N0Y3CCIyiDdWRwgiMo",
              // chiado-lodestar-0
              "enr:-Ly4QJJUnV9BxP_rw2Bv7E9iyw4sYS2b4OQZIf4Mu_cA6FljJvOeSTQiCUpbZhZjR4R0VseBhdTzrLrlHrAuu_OeZqgJh2F0dG5ldHOI__________-EZXRoMpAxNnBDAgAAb___________gmlkgnY0gmlwhIuQGnOJc2VjcDI1NmsxoQPT_u3IjDtB2r-nveH5DhUmlM8F2IgLyxhmwmqW4L5k3ohzeW5jbmV0cw-DdGNwgiMog3VkcIIjKA",
              // chiado-prysm-0
              "enr:-MK4QCkOyqOTPX1_-F-5XVFjPclDUc0fj3EeR8FJ5-hZjv6ARuGlFspM0DtioHn1r6YPUXkOg2g3x6EbeeKdsrvVBYmGAYQKrixeh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAxNnBDAgAAb___________gmlkgnY0gmlwhIuQGlWJc2VjcDI1NmsxoQKdW3-DgLExBkpLGMRtuM88wW_gZkC7Yeg0stYDTrlynYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              // chiado-teku-0
              "enr:-Ly4QLYLNqrjvSxD3lpAPBUNlxa6cIbe79JqLZLFcZZjWoCjZcw-85agLUErHiygG2weRSCLnd5V460qTbLbwJQsfZkoh2F0dG5ldHOI__________-EZXRoMpAxNnBDAgAAb___________gmlkgnY0gmlwhKq7mu-Jc2VjcDI1NmsxoQP900YAYa9kdvzlSKGjVo-F3XVzATjOYp3BsjLjSophO4hzeW5jbmV0cw-DdGNwgiMog3VkcIIjKA",
              // chiado-teku-1
              "enr:-Ly4QCGeYvTCNOGKi0mKRUd45rLj96b4pH98qG7B9TCUGXGpHZALtaL2-XfjASQyhbCqENccI4PGXVqYTIehNT9KJMQgh2F0dG5ldHOI__________-EZXRoMpAxNnBDAgAAb___________gmlkgnY0gmlwhIuQrVSJc2VjcDI1NmsxoQP9iDchx2PGl3JyJ29B9fhLCvVMN6n23pPAIIeFV-sHOIhzeW5jbmV0cw-DdGNwgiMog3VkcIIjKA",
              // GnosisDAO Bootnode: 3.71.132.231
              "enr:-Ly4QAtr21x5Ps7HYhdZkIBRBgcBkvlIfEel1YNjtFWf4cV3au2LgBGICz9PtEs9-p2HUl_eME8m1WImxTxSB3AkCMwBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAxNnBDAgAAb___________gmlkgnY0gmlwhANHhOeJc2VjcDI1NmsxoQNLp1QPV8-pyMCohOtj6xGtSBM_GtVTqzlbvNsCF4ezkYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              // GnosisDAO Bootnode: 3.69.35.13
              "enr:-Ly4QLgn8Bx6faigkKUGZQvd1HDToV2FAxZIiENK-lczruzQb90qJK-4E65ADly0s4__dQOW7IkLMW7ZAyJy2vtiLy8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAxNnBDAgAAb___________gmlkgnY0gmlwhANFIw2Jc2VjcDI1NmsxoQMa-fWEy9UJHfOl_lix3wdY5qust78sHAqZnWwEiyqKgYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA");
    }
  }
}
