/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory.DEFAULT_MAX_QUEUE_SIZE;
import static tech.pegasys.teku.spec.constants.NetworkConstants.DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;
import static tech.pegasys.teku.spec.networks.Eth2Network.CHIADO;
import static tech.pegasys.teku.spec.networks.Eth2Network.EPHEMERY;
import static tech.pegasys.teku.spec.networks.Eth2Network.GNOSIS;
import static tech.pegasys.teku.spec.networks.Eth2Network.HOLESKY;
import static tech.pegasys.teku.spec.networks.Eth2Network.LESS_SWIFT;
import static tech.pegasys.teku.spec.networks.Eth2Network.LUKSO;
import static tech.pegasys.teku.spec.networks.Eth2Network.MAINNET;
import static tech.pegasys.teku.spec.networks.Eth2Network.MINIMAL;
import static tech.pegasys.teku.spec.networks.Eth2Network.SEPOLIA;
import static tech.pegasys.teku.spec.networks.Eth2Network.SWIFT;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class Eth2NetworkConfiguration {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_STARTUP_TARGET_PEER_COUNT = 5;
  private static final int DEFAULT_STARTUP_TIMEOUT_SECONDS = 30;

  public static final boolean DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED = false;

  public static final boolean DEFAULT_FORK_CHOICE_UPDATED_ALWAYS_SEND_PAYLOAD_ATTRIBUTES = false;

  public static final boolean DEFAULT_ALLOW_SYNC_OUTSIDE_WEAK_SUBJECTIVITY_PERIOD = false;

  public static final int DEFAULT_ASYNC_P2P_MAX_THREADS = 10;

  public static final int DEFAULT_ASYNC_P2P_MAX_QUEUE = DEFAULT_MAX_QUEUE_SIZE;

  // at least 5, but happily up to 12
  public static final int DEFAULT_VALIDATOR_EXECUTOR_THREADS =
      Math.max(5, Math.min(Runtime.getRuntime().availableProcessors(), 12));

  public static final int DEFAULT_ASYNC_BEACON_CHAIN_MAX_THREADS =
      Math.max(Runtime.getRuntime().availableProcessors(), DEFAULT_VALIDATOR_EXECUTOR_THREADS);

  public static final int DEFAULT_ASYNC_BEACON_CHAIN_MAX_QUEUE = DEFAULT_MAX_QUEUE_SIZE;

  public static final String FINALIZED_STATE_URL_PATH = "eth/v2/debug/beacon/states/finalized";
  public static final String GENESIS_STATE_URL_PATH = "eth/v2/debug/beacon/states/genesis";
  // 26 thousand years should be enough
  public static final Integer MAX_EPOCHS_STORE_BLOBS = Integer.MAX_VALUE;

  private static final String MAINNET_TRUSTED_SETUP_FILENAME = "mainnet-trusted-setup.txt";
  private static final String MINIMAL_TRUSTED_SETUP_FILENAME = "minimal-trusted-setup.txt";

  private final Spec spec;
  private final String constants;
  private final StateBoostrapConfig stateBoostrapConfig;
  private final int startupTargetPeerCount;
  private final int startupTimeoutSeconds;
  private final List<String> discoveryBootnodes;
  private final Optional<UInt64> altairForkEpoch;
  private final Optional<UInt64> bellatrixForkEpoch;
  private final Optional<UInt64> capellaForkEpoch;
  private final Optional<UInt64> denebForkEpoch;
  private final Optional<UInt64> electraForkEpoch;
  private final Eth1Address eth1DepositContractAddress;
  private final Optional<UInt64> eth1DepositContractDeployBlock;
  private final Optional<String> trustedSetup;
  private final Optional<Bytes32> terminalBlockHashOverride;
  private final Optional<UInt256> totalTerminalDifficultyOverride;
  private final Optional<UInt64> terminalBlockHashEpochOverride;
  private final Optional<Eth2Network> eth2Network;
  private final Optional<Integer> epochsStoreBlobs;
  private final int asyncP2pMaxThreads;
  private final int asyncBeaconChainMaxThreads;
  private final int asyncBeaconChainMaxQueue;
  private final int asyncP2pMaxQueue;
  private final boolean forkChoiceLateBlockReorgEnabled;
  private final boolean forkChoiceUpdatedAlwaysSendPayloadAttributes;

  private Eth2NetworkConfiguration(
      final Spec spec,
      final String constants,
      final StateBoostrapConfig stateBoostrapConfig,
      final int startupTargetPeerCount,
      final int startupTimeoutSeconds,
      final List<String> discoveryBootnodes,
      final Eth1Address eth1DepositContractAddress,
      final Optional<UInt64> eth1DepositContractDeployBlock,
      final Optional<String> trustedSetup,
      final Optional<UInt64> altairForkEpoch,
      final Optional<UInt64> bellatrixForkEpoch,
      final Optional<UInt64> capellaForkEpoch,
      final Optional<UInt64> denebForkEpoch,
      final Optional<UInt64> electraForkEpoch,
      final Optional<Bytes32> terminalBlockHashOverride,
      final Optional<UInt256> totalTerminalDifficultyOverride,
      final Optional<UInt64> terminalBlockHashEpochOverride,
      final Optional<Eth2Network> eth2Network,
      final Optional<Integer> epochsStoreBlobs,
      final int asyncP2pMaxThreads,
      final int asyncP2pMaxQueue,
      final int asyncBeaconChainMaxThreads,
      final int asyncBeaconChainMaxQueue,
      final boolean forkChoiceLateBlockReorgEnabled,
      final boolean forkChoiceUpdatedAlwaysSendPayloadAttributes) {
    this.spec = spec;
    this.constants = constants;
    this.stateBoostrapConfig = stateBoostrapConfig;
    this.startupTargetPeerCount = startupTargetPeerCount;
    this.startupTimeoutSeconds = startupTimeoutSeconds;
    this.discoveryBootnodes = discoveryBootnodes;
    this.altairForkEpoch = altairForkEpoch;
    this.bellatrixForkEpoch = bellatrixForkEpoch;
    this.capellaForkEpoch = capellaForkEpoch;
    this.denebForkEpoch = denebForkEpoch;
    this.electraForkEpoch = electraForkEpoch;
    this.eth1DepositContractAddress =
        eth1DepositContractAddress == null
            ? spec.getGenesisSpecConfig().getDepositContractAddress()
            : eth1DepositContractAddress;
    this.eth1DepositContractDeployBlock = eth1DepositContractDeployBlock;
    this.trustedSetup = trustedSetup;
    this.terminalBlockHashOverride = terminalBlockHashOverride;
    this.totalTerminalDifficultyOverride = totalTerminalDifficultyOverride;
    this.terminalBlockHashEpochOverride = terminalBlockHashEpochOverride;
    this.eth2Network = eth2Network;
    this.epochsStoreBlobs = epochsStoreBlobs;
    this.asyncP2pMaxThreads = asyncP2pMaxThreads;
    this.asyncP2pMaxQueue = asyncP2pMaxQueue;
    this.asyncBeaconChainMaxThreads = asyncBeaconChainMaxThreads;
    this.asyncBeaconChainMaxQueue = asyncBeaconChainMaxQueue;
    this.forkChoiceLateBlockReorgEnabled = forkChoiceLateBlockReorgEnabled;
    this.forkChoiceUpdatedAlwaysSendPayloadAttributes =
        forkChoiceUpdatedAlwaysSendPayloadAttributes;

    LOG.debug(
        "P2P async queue - {} threads, max queue size {} ", asyncP2pMaxThreads, asyncP2pMaxQueue);
    LOG.debug(
        "P2p beacon chain queue - {} threads, max queue size {} ",
        asyncBeaconChainMaxThreads,
        asyncBeaconChainMaxQueue);
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
   * @return The constants resource name or url
   * @deprecated Constants should be accessed via {@link SpecVersion}
   */
  @Deprecated
  public String getConstants() {
    return constants;
  }

  public StateBoostrapConfig getNetworkBoostrapConfig() {
    return stateBoostrapConfig;
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

  public Optional<UInt64> getForkEpoch(final SpecMilestone specMilestone) {
    return switch (specMilestone) {
      case ALTAIR -> altairForkEpoch;
      case BELLATRIX -> bellatrixForkEpoch;
      case CAPELLA -> capellaForkEpoch;
      case DENEB -> denebForkEpoch;
      case ELECTRA -> electraForkEpoch;
      default -> Optional.empty();
    };
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

  public Optional<Integer> getEpochsStoreBlobs() {
    return epochsStoreBlobs;
  }

  public int getAsyncP2pMaxThreads() {
    return asyncP2pMaxThreads;
  }

  public int getAsyncP2pMaxQueue() {
    return asyncP2pMaxQueue;
  }

  public int getAsyncBeaconChainMaxThreads() {
    return asyncBeaconChainMaxThreads;
  }

  public int getAsyncBeaconChainMaxQueue() {
    return asyncBeaconChainMaxQueue;
  }

  public boolean isForkChoiceLateBlockReorgEnabled() {
    return forkChoiceLateBlockReorgEnabled;
  }

  public boolean isForkChoiceUpdatedAlwaysSendPayloadAttributes() {
    return forkChoiceUpdatedAlwaysSendPayloadAttributes;
  }

  @Override
  public String toString() {
    return constants;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Eth2NetworkConfiguration that = (Eth2NetworkConfiguration) o;
    return startupTargetPeerCount == that.startupTargetPeerCount
        && startupTimeoutSeconds == that.startupTimeoutSeconds
        && asyncP2pMaxThreads == that.asyncP2pMaxThreads
        && asyncBeaconChainMaxThreads == that.asyncBeaconChainMaxThreads
        && asyncBeaconChainMaxQueue == that.asyncBeaconChainMaxQueue
        && asyncP2pMaxQueue == that.asyncP2pMaxQueue
        && forkChoiceLateBlockReorgEnabled == that.forkChoiceLateBlockReorgEnabled
        && forkChoiceUpdatedAlwaysSendPayloadAttributes
            == that.forkChoiceUpdatedAlwaysSendPayloadAttributes
        && Objects.equals(spec, that.spec)
        && Objects.equals(constants, that.constants)
        && Objects.equals(stateBoostrapConfig, that.stateBoostrapConfig)
        && Objects.equals(discoveryBootnodes, that.discoveryBootnodes)
        && Objects.equals(altairForkEpoch, that.altairForkEpoch)
        && Objects.equals(bellatrixForkEpoch, that.bellatrixForkEpoch)
        && Objects.equals(capellaForkEpoch, that.capellaForkEpoch)
        && Objects.equals(denebForkEpoch, that.denebForkEpoch)
        && Objects.equals(electraForkEpoch, that.electraForkEpoch)
        && Objects.equals(eth1DepositContractAddress, that.eth1DepositContractAddress)
        && Objects.equals(eth1DepositContractDeployBlock, that.eth1DepositContractDeployBlock)
        && Objects.equals(trustedSetup, that.trustedSetup)
        && Objects.equals(terminalBlockHashOverride, that.terminalBlockHashOverride)
        && Objects.equals(totalTerminalDifficultyOverride, that.totalTerminalDifficultyOverride)
        && Objects.equals(terminalBlockHashEpochOverride, that.terminalBlockHashEpochOverride)
        && Objects.equals(eth2Network, that.eth2Network)
        && Objects.equals(epochsStoreBlobs, that.epochsStoreBlobs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        spec,
        constants,
        stateBoostrapConfig,
        startupTargetPeerCount,
        startupTimeoutSeconds,
        discoveryBootnodes,
        altairForkEpoch,
        bellatrixForkEpoch,
        capellaForkEpoch,
        denebForkEpoch,
        electraForkEpoch,
        eth1DepositContractAddress,
        eth1DepositContractDeployBlock,
        trustedSetup,
        terminalBlockHashOverride,
        totalTerminalDifficultyOverride,
        terminalBlockHashEpochOverride,
        eth2Network,
        epochsStoreBlobs,
        asyncP2pMaxThreads,
        asyncBeaconChainMaxThreads,
        asyncBeaconChainMaxQueue,
        asyncP2pMaxQueue,
        forkChoiceLateBlockReorgEnabled,
        forkChoiceUpdatedAlwaysSendPayloadAttributes);
  }

  public static class Builder {
    private static final String EPOCHS_STORE_BLOBS_MAX_KEYWORD = "MAX";
    private String constants;
    private Optional<String> genesisState = Optional.empty();
    private Optional<String> initialState = Optional.empty();
    private Optional<String> checkpointSyncUrl = Optional.empty();
    private boolean isUsingCustomInitialState = false;
    private boolean allowSyncOutsideWeakSubjectivityPeriod =
        DEFAULT_ALLOW_SYNC_OUTSIDE_WEAK_SUBJECTIVITY_PERIOD;
    private int startupTargetPeerCount = DEFAULT_STARTUP_TARGET_PEER_COUNT;
    private int startupTimeoutSeconds = DEFAULT_STARTUP_TIMEOUT_SECONDS;
    private int asyncP2pMaxThreads = DEFAULT_ASYNC_P2P_MAX_THREADS;
    private OptionalInt asyncP2pMaxQueue = OptionalInt.empty();
    private int asyncBeaconChainMaxThreads = DEFAULT_ASYNC_BEACON_CHAIN_MAX_THREADS;
    private OptionalInt asyncBeaconChainMaxQueue = OptionalInt.empty();
    private List<String> discoveryBootnodes = new ArrayList<>();
    private Eth1Address eth1DepositContractAddress;
    private Optional<UInt64> eth1DepositContractDeployBlock = Optional.empty();
    private Optional<String> trustedSetup = Optional.empty();
    private Optional<UInt64> altairForkEpoch = Optional.empty();
    private Optional<UInt64> bellatrixForkEpoch = Optional.empty();
    private Optional<UInt64> capellaForkEpoch = Optional.empty();
    private Optional<UInt64> denebForkEpoch = Optional.empty();
    private Optional<UInt64> electraForkEpoch = Optional.empty();
    private Optional<Bytes32> terminalBlockHashOverride = Optional.empty();
    private Optional<UInt256> totalTerminalDifficultyOverride = Optional.empty();
    private Optional<UInt64> terminalBlockHashEpochOverride = Optional.empty();
    private int safeSlotsToImportOptimistically = DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;
    private String epochsStoreBlobs;
    private Spec spec;
    private boolean forkChoiceLateBlockReorgEnabled = DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;
    private boolean forkChoiceUpdatedAlwaysSendPayloadAttributes =
        DEFAULT_FORK_CHOICE_UPDATED_ALWAYS_SEND_PAYLOAD_ATTRIBUTES;

    public void spec(final Spec spec) {
      this.spec = spec;
    }

    public Eth2NetworkConfiguration build() {
      checkNotNull(constants, "Missing constants");
      validateCommandLineParameters();

      final Optional<Integer> maybeEpochsStoreBlobs =
          validateAndParseEpochsStoreBlobs(epochsStoreBlobs);
      if (spec == null) {
        spec =
            SpecFactory.create(
                constants,
                builder -> {
                  // Ephemery network field change periodically, update to current
                  if (constants.equals(EPHEMERY.configName())) {
                    EphemeryNetwork.updateConfig(builder);
                  }
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
                        if (maybeEpochsStoreBlobs.isPresent()) {
                          denebBuilder.epochsStoreBlobs(maybeEpochsStoreBlobs);
                        }
                        if (trustedSetup.isEmpty()) {
                          LOG.warn(
                              "Setting a default for trusted setup as nothing was set explicitly");
                          trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME);
                        }
                      });
                  builder.electraBuilder(
                      electraBuilder ->
                          electraForkEpoch.ifPresent(electraBuilder::electraForkEpoch));
                });
      }
      if (spec.getForkSchedule().getSupportedMilestones().contains(SpecMilestone.DENEB)
          && trustedSetup.isEmpty()) {
        throw new InvalidConfigurationException(
            "Trusted Setup was not configured but deneb fork epoch has been set, cannot start with supplied configuration.");
      }
      // if the deposit contract was not set, default from constants
      if (eth1DepositContractAddress == null) {
        eth1DepositContractAddress(spec.getGenesisSpec().getConfig().getDepositContractAddress());
      }
      final Optional<Eth2Network> eth2Network = Eth2Network.fromStringLenient(constants);
      return new Eth2NetworkConfiguration(
          spec,
          constants,
          new StateBoostrapConfig(
              genesisState,
              initialState,
              checkpointSyncUrl,
              isUsingCustomInitialState,
              allowSyncOutsideWeakSubjectivityPeriod),
          startupTargetPeerCount,
          startupTimeoutSeconds,
          discoveryBootnodes,
          eth1DepositContractAddress,
          eth1DepositContractDeployBlock,
          trustedSetup,
          altairForkEpoch,
          bellatrixForkEpoch,
          capellaForkEpoch,
          denebForkEpoch,
          electraForkEpoch,
          terminalBlockHashOverride,
          totalTerminalDifficultyOverride,
          terminalBlockHashEpochOverride,
          eth2Network,
          maybeEpochsStoreBlobs,
          asyncP2pMaxThreads,
          asyncP2pMaxQueue.orElse(DEFAULT_ASYNC_P2P_MAX_QUEUE),
          asyncBeaconChainMaxThreads,
          asyncBeaconChainMaxQueue.orElse(DEFAULT_ASYNC_BEACON_CHAIN_MAX_QUEUE),
          forkChoiceLateBlockReorgEnabled,
          forkChoiceUpdatedAlwaysSendPayloadAttributes);
    }

    private void validateCommandLineParameters() {
      checkArgument(
          safeSlotsToImportOptimistically >= 0, "Safe slots to import optimistically must be >= 0");

      checkArgument(
          asyncP2pMaxThreads > 1,
          "P2P Max threads must be >= 2 (Xnetwork-async-p2p-max-threads - default 10)");
      checkArgument(
          asyncP2pMaxThreads < 256,
          "P2P Max threads must be <= 255 (Xnetwork-async-p2p-max-threads - default 10)");
      checkArgument(
          asyncP2pMaxQueue.orElse(DEFAULT_ASYNC_P2P_MAX_QUEUE) >= 2000,
          "P2P Max Queue size must be at least 2000 (Xnetwork-async-p2p-max-queue - default 10000)");

      checkArgument(
          asyncBeaconChainMaxThreads > 1,
          "BeaconChain Max threads must be >= 2 (Xnetwork-async-beaconchain-max-threads - default 5)");
      checkArgument(
          asyncBeaconChainMaxThreads < 256,
          "BeaconChain Max threads must be <= 255 (Xnetwork-async-beaconchain-max-threads - default 5)");
      checkArgument(
          asyncBeaconChainMaxQueue.orElse(DEFAULT_ASYNC_BEACON_CHAIN_MAX_QUEUE) >= 2000,
          "BeaconChain Max Queue size must be at least 2000 (Xnetwork-async-beaconchain-max-queue - default 10000)");
    }

    public Builder constants(final String constants) {
      this.constants = constants;
      return this;
    }

    public Builder checkpointSyncUrl(final String checkpointSyncUrl) {
      this.checkpointSyncUrl = Optional.of(checkpointSyncUrl);
      this.genesisState =
          Optional.of(UrlSanitizer.appendPath(checkpointSyncUrl, GENESIS_STATE_URL_PATH));
      this.initialState =
          Optional.of(UrlSanitizer.appendPath(checkpointSyncUrl, FINALIZED_STATE_URL_PATH));
      return this;
    }

    /** Used when the user specifies the --initial-state option in the CLI. */
    public Builder customInitialState(final String initialState) {
      this.initialState = Optional.of(initialState);
      this.isUsingCustomInitialState = true;
      return this;
    }

    /**
     * Used to load initial states from a URL.
     *
     * @param initialState The URL pointing to a initial state resource (e.g. a file on GitHub or an
     *     Beacon API debug state endpoint.
     */
    public Builder defaultInitialStateFromUrl(final String initialState) {
      this.initialState = Optional.of(initialState);
      return this;
    }

    /**
     * Used to load initial states from SSZ files within our distributed jar.
     *
     * @param filename the name of the ssz file (e.g. "mainnet-genesis.ssz")
     */
    public Builder defaultInitialStateFromClasspath(final String filename) {
      Optional.ofNullable(Eth2NetworkConfiguration.class.getResource(filename))
          .map(URL::toExternalForm)
          .ifPresent(path -> this.initialState = Optional.of(path));
      return this;
    }

    public Builder customGenesisState(final String genesisState) {
      this.genesisState = Optional.of(genesisState);
      return this;
    }

    public Builder ignoreWeakSubjectivityPeriodEnabled(
        final boolean ignoreWeakSubjectivityPeriodEnabled) {
      this.allowSyncOutsideWeakSubjectivityPeriod = ignoreWeakSubjectivityPeriodEnabled;
      return this;
    }

    public Builder asyncP2pMaxThreads(final int asyncP2pMaxThreads) {
      this.asyncP2pMaxThreads = asyncP2pMaxThreads;
      return this;
    }

    public Builder asyncP2pMaxQueue(final Integer asyncP2pMaxQueue) {
      this.asyncP2pMaxQueue = OptionalInt.of(asyncP2pMaxQueue);
      return this;
    }

    public Builder asyncP2pMaxQueueIfDefault(final Integer asyncP2pMaxQueue) {
      if (this.asyncP2pMaxQueue.isEmpty()) {
        return asyncP2pMaxQueue(asyncP2pMaxQueue);
      }
      return this;
    }

    public Builder asyncBeaconChainMaxThreads(final int asyncBeaconChainMaxThreads) {
      this.asyncBeaconChainMaxThreads = asyncBeaconChainMaxThreads;
      return this;
    }

    public Builder asyncBeaconChainMaxQueue(final int asyncBeaconChainMaxQueue) {
      this.asyncBeaconChainMaxQueue = OptionalInt.of(asyncBeaconChainMaxQueue);
      return this;
    }

    public Builder asyncBeaconChainMaxQueueIfDefault(final int asyncBeaconChainMaxQueue) {
      if (this.asyncBeaconChainMaxQueue.isEmpty()) {
        return asyncBeaconChainMaxQueue(asyncBeaconChainMaxQueue);
      }
      return this;
    }

    public Builder genesisStateFromClasspath(final String filename) {
      Optional.ofNullable(Eth2NetworkConfiguration.class.getResource(filename))
          .map(URL::toExternalForm)
          .ifPresent(path -> this.genesisState = Optional.of(path));
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

    public Builder electraForkEpoch(final UInt64 electraForkEpoch) {
      this.electraForkEpoch = Optional.of(electraForkEpoch);
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

    public Builder epochsStoreBlobs(final String epochsStoreBlobs) {
      this.epochsStoreBlobs = epochsStoreBlobs;
      return this;
    }

    public Builder applyNetworkDefaults(final String networkName) {
      Eth2Network.fromStringLenient(networkName)
          .ifPresentOrElse(
              this::applyNetworkDefaults, () -> resetAndApplyBasicDefaults(networkName));
      return this;
    }

    private Builder resetAndApplyBasicDefaults(final String networkName) {
      return reset()
          .trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME)
          .constants(networkName);
    }

    public Builder applyNetworkDefaults(final Eth2Network network) {
      return switch (network) {
        case MAINNET -> applyMainnetNetworkDefaults();
        case MINIMAL -> applyMinimalNetworkDefaults();
        case SEPOLIA -> applySepoliaNetworkDefaults();
        case LUKSO -> applyLuksoNetworkDefaults();
        case HOLESKY -> applyHoleskyNetworkDefaults();
        case EPHEMERY -> applyEphemeryNetworkDefaults();
        case GNOSIS -> applyGnosisNetworkDefaults();
        case CHIADO -> applyChiadoNetworkDefaults();
        case SWIFT -> applySwiftNetworkDefaults();
        case LESS_SWIFT -> applyLessSwiftNetworkDefaults();
      };
    }

    private Builder reset() {
      constants = null;
      genesisState = Optional.empty();
      initialState = Optional.empty();
      checkpointSyncUrl = Optional.empty();
      isUsingCustomInitialState = false;
      allowSyncOutsideWeakSubjectivityPeriod = DEFAULT_ALLOW_SYNC_OUTSIDE_WEAK_SUBJECTIVITY_PERIOD;
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
      return applyTestnetDefaults()
          .trustedSetupFromClasspath(MINIMAL_TRUSTED_SETUP_FILENAME)
          .constants(MINIMAL.configName())
          .startupTargetPeerCount(0);
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
          .defaultInitialStateFromClasspath("mainnet-genesis.ssz")
          .genesisStateFromClasspath("mainnet-genesis.ssz")
          .trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME)
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(11052984)
          .discoveryBootnodes(
              // PegaSys Teku
              "enr:-KG4QNTx85fjxABbSq_Rta9wy56nQ1fHK0PewJbGjLm1M4bMGx5-3Qq4ZX2-iFJ0pys_O90sVXNNOxp2E7afBsGsBrgDhGV0aDKQu6TalgMAAAD__________4JpZIJ2NIJpcIQEnfA2iXNlY3AyNTZrMaECGXWQ-rQ2KZKRH1aOW4IlPDBkY4XDphxg9pxKytFCkayDdGNwgiMog3VkcIIjKA",
              "enr:-KG4QF4B5WrlFcRhUU6dZETwY5ZzAXnA0vGC__L1Kdw602nDZwXSTs5RFXFIFUnbQJmhNGVU6OIX7KVrCSTODsz1tK4DhGV0aDKQu6TalgMAAAD__________4JpZIJ2NIJpcIQExNYEiXNlY3AyNTZrMaECQmM9vp7KhaXhI-nqL_R0ovULLCFSFTa9CPPSdb1zPX6DdGNwgiMog3VkcIIjKA",
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

    private Builder applySepoliaNetworkDefaults() {
      return applyTestnetDefaults()
          .constants(SEPOLIA.configName())
          .startupTimeoutSeconds(120)
          .trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME)
          .eth1DepositContractDeployBlock(1273020)
          .defaultInitialStateFromUrl(
              "https://github.com/eth-clients/merge-testnets/raw/9c873ab67b902aa676370a549129e5e91013afa3/sepolia/genesis.ssz")
          .customGenesisState(
              "https://github.com/eth-clients/merge-testnets/raw/9c873ab67b902aa676370a549129e5e91013afa3/sepolia/genesis.ssz")
          .discoveryBootnodes(
              // EF bootnodes
              "enr:-Ku4QDZ_rCowZFsozeWr60WwLgOfHzv1Fz2cuMvJqN5iJzLxKtVjoIURY42X_YTokMi3IGstW5v32uSYZyGUXj9Q_IECh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCo_ujukAAAaf__________gmlkgnY0gmlwhIpEe5iJc2VjcDI1NmsxoQNHTpFdaNSCEWiN_QqT396nb0PzcUpLe3OVtLph-AciBYN1ZHCCIy0",
              "enr:-Ku4QHRyRwEPT7s0XLYzJ_EeeWvZTXBQb4UCGy1F_3m-YtCNTtDlGsCMr4UTgo4uR89pv11uM-xq4w6GKfKhqU31hTgCh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCo_ujukAAAaf__________gmlkgnY0gmlwhIrFM7WJc2VjcDI1NmsxoQI4diTwChN3zAAkarf7smOHCdFb1q3DSwdiQ_Lc_FdzFIN1ZHCCIy0",
              "enr:-Ku4QOkvvf0u5Hg4-HhY-SJmEyft77G5h3rUM8VF_e-Hag5cAma3jtmFoX4WElLAqdILCA-UWFRN1ZCDJJVuEHrFeLkDh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCo_ujukAAAaf__________gmlkgnY0gmlwhJK-AWeJc2VjcDI1NmsxoQLFcT5VE_NMiIC8Ll7GypWDnQ4UEmuzD7hF_Hf4veDJwIN1ZHCCIy0",
              "enr:-Ku4QH6tYsHKITYeHUu5kdfXgEZWI18EWk_2RtGOn1jBPlx2UlS_uF3Pm5Dx7tnjOvla_zs-wwlPgjnEOcQDWXey51QCh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCo_ujukAAAaf__________gmlkgnY0gmlwhIs7Mc6Jc2VjcDI1NmsxoQIET4Mlv9YzhrYhX_H9D7aWMemUrvki6W4J2Qo0YmFMp4N1ZHCCIy0",
              "enr:-Ku4QDmz-4c1InchGitsgNk4qzorWMiFUoaPJT4G0IiF8r2UaevrekND1o7fdoftNucirj7sFFTTn2-JdC2Ej0p1Mn8Ch2F0dG5ldHOIAAAAAAAAAACEZXRoMpCo_ujukAAAaf__________gmlkgnY0gmlwhKpA-liJc2VjcDI1NmsxoQMpHP5U1DK8O_JQU6FadmWbE42qEdcGlllR8HcSkkfWq4N1ZHCCIy0",
              // Teku bootnode
              "enr:-KO4QP7MmB3juk8rUjJHcUoxZDU9Np4FlW0HyDEGIjSO7GD9PbSsabu7713cWSUWKDkxIypIXg1A-6lG7ySRGOMZHeGCAmuEZXRoMpDTH2GRkAAAc___________gmlkgnY0gmlwhBSoyGOJc2VjcDI1NmsxoQNta5b_bexSSwwrGW2Re24MjfMntzFd0f2SAxQtMj3ueYN0Y3CCIyiDdWRwgiMo",
              // Another bootnode
              "enr:-L64QC9Hhov4DhQ7mRukTOz4_jHm4DHlGL726NWH4ojH1wFgEwSin_6H95Gs6nW2fktTWbPachHJ6rUFu0iJNgA0SB2CARqHYXR0bmV0c4j__________4RldGgykDb6UBOQAABx__________-CaWSCdjSCaXCEA-2vzolzZWNwMjU2azGhA17lsUg60R776rauYMdrAz383UUgESoaHEzMkvm4K6k6iHN5bmNuZXRzD4N0Y3CCIyiDdWRwgiMo",
              // Lodestart bootnode
              "enr:-KG4QJejf8KVtMeAPWFhN_P0c4efuwu1pZHELTveiXUeim6nKYcYcMIQpGxxdgT2Xp9h-M5pr9gn2NbbwEAtxzu50Y8BgmlkgnY0gmlwhEEVkQCDaXA2kCoBBPnAEJg4AAAAAAAAAAGJc2VjcDI1NmsxoQLEh_eVvk07AQABvLkTGBQTrrIOQkzouMgSBtNHIRUxOIN1ZHCCIyiEdWRwNoIjKA");
    }

    private Builder applyLuksoNetworkDefaults() {
      return applyTestnetDefaults()
          .constants(LUKSO.configName())
          .startupTimeoutSeconds(120)
          .eth1DepositContractDeployBlock(0)
          .defaultInitialStateFromClasspath("lukso-genesis.ssz")
          .genesisStateFromClasspath("lukso-genesis.ssz")
          .discoveryBootnodes(
              // Consensus layer bootnodes
              "enr:-MK4QJ-Bt9HATy4GQawPbDDTArtnt_phuWiVVoWKhS7-DSNjVzmGKBI9xKzpyRtpeCWd3qA9737FTdkKGDgtHfF4N-6GAYlzJCVRh2F0dG5ldHOIAAAAAAAAAACEZXRoMpA2ulfbQgAABP__________gmlkgnY0gmlwhCKTScGJc2VjcDI1NmsxoQJNpNUERqKhA8eDDC4tovG3a59NXVOW16JDFAWXoFFTEYhzeW5jbmV0cwCDdGNwgjLIg3VkcIIu4A",
              "enr:-MK4QDOs4pISOkkYbVHnGYHC5EhYCsVzwguun6sFZjLTqrY6Kx_AoE-YyHvqBIHDUwyQqESC4-B3o6DigPQNfKpdhXiGAYgmPWCdh2F0dG5ldHOIAAAAAAAAAACEZXRoMpA2ulfbQgAABP__________gmlkgnY0gmlwhCIgwNOJc2VjcDI1NmsxoQNGVC8JPcsqsZPoohLP1ujAYpBfS0dBwiz4LeoUQ-k5OohzeW5jbmV0cwCDdGNwgjLIg3VkcIIu4A");
    }

    public Builder applyGnosisNetworkDefaults() {
      return reset()
          .constants(GNOSIS.configName())
          .defaultInitialStateFromClasspath("gnosis-genesis.ssz")
          .genesisStateFromClasspath("gnosis-genesis.ssz")
          .startupTimeoutSeconds(120)
          .trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME)
          .eth1DepositContractDeployBlock(19469077)
          .discoveryBootnodes(
              // Gnosis Chain Team bootnodes
              "enr:-Ly4QIAhiTHk6JdVhCdiLwT83wAolUFo5J4nI5HrF7-zJO_QEw3cmEGxC1jvqNNUN64Vu-xxqDKSM528vKRNCehZAfEBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhEFtZ5SJc2VjcDI1NmsxoQJwgL5C-30E8RJmW8gCb7sfwWvvfre7wGcCeV4X1G2wJYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QDhEjlkf8fwO5uWAadexy88GXZneTuUCIPHhv98v8ZfXMtC0S1S_8soiT0CMEgoeLe9Db01dtkFQUnA9YcnYC_8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhEFtZ5WJc2VjcDI1NmsxoQMRSho89q2GKx_l2FZhR1RmnSiQr6o_9hfXfQUuW6bjMohzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QLKgv5M2D4DYJgo6s4NG_K4zu4sk5HOLCfGCdtgoezsbfRbfGpQ4iSd31M88ec3DHA5FWVbkgIas9EaJeXia0nwBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhI1eYRaJc2VjcDI1NmsxoQLpK_A47iNBkVjka9Mde1F-Kie-R0sq97MCNKCxt2HwOIhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QF_0qvji6xqXrhQEhwJR1W9h5dXV7ZjVCN_NlosKxcgZW6emAfB_KXxEiPgKr_-CZG8CWvTiojEohG1ewF7P368Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhI1eYUqJc2VjcDI1NmsxoQIpNRUT6llrXqEbjkAodsZOyWv8fxQkyQtSvH4sg2D7n4hzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QCD5D99p36WafgTSxB6kY7D2V1ca71C49J4VWI2c8UZCCPYBvNRWiv0-HxOcbpuUdwPVhyWQCYm1yq2ZH0ukCbQBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCCS-QxAgAAZP__________gmlkgnY0gmlwhI1eYVSJc2VjcDI1NmsxoQJJMSV8iSZ8zvkgbi8cjIGEUVJeekLqT0LQha_co-siT4hzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              "enr:-KK4QKXJq1QOVWuJAGige4uaT8LRPQGCVRf3lH3pxjaVScMRUfFW1eiiaz8RwOAYvw33D4EX-uASGJ5QVqVCqwccxa-Bi4RldGgykCGm-DYDAABk__________-CaWSCdjSCaXCEM0QnzolzZWNwMjU2azGhAhNvrRkpuK4MWTf3WqiOXSOePL8Zc-wKVpZ9FQx_BDadg3RjcIIjKIN1ZHCCIyg",
              "enr:-LO4QO87Rn2ejN3SZdXkx7kv8m11EZ3KWWqoIN5oXwQ7iXR9CVGd1dmSyWxOL1PGsdIqeMf66OZj4QGEJckSi6okCdWBpIdhdHRuZXRziAAAAABgAAAAhGV0aDKQPr_UhAQAAGT__________4JpZIJ2NIJpcIQj0iX1iXNlY3AyNTZrMaEDd-_eqFlWWJrUfEp8RhKT9NxdYaZoLHvsp3bbejPyOoeDdGNwgiMog3VkcIIjKA",
              "enr:-LK4QIJUAxX9uNgW4ACkq8AixjnSTcs9sClbEtWRq9F8Uy9OEExsr4ecpBTYpxX66cMk6pUHejCSX3wZkK2pOCCHWHEBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpA-v9SEBAAAZP__________gmlkgnY0gmlwhCPSnDuJc2VjcDI1NmsxoQNuaAjFE-ANkH3pbeBdPiEIwjR5kxFuKaBWxHkqFuPz5IN0Y3CCIyiDdWRwgiMo");
    }

    public Builder applyChiadoNetworkDefaults() {
      return reset()
          .constants(CHIADO.configName())
          .defaultInitialStateFromClasspath("chiado-genesis.ssz")
          .genesisStateFromClasspath("chiado-genesis.ssz")
          .startupTimeoutSeconds(120)
          .trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME)
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
              "enr:-Ly4QLgn8Bx6faigkKUGZQvd1HDToV2FAxZIiENK-lczruzQb90qJK-4E65ADly0s4__dQOW7IkLMW7ZAyJy2vtiLy8Bh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAxNnBDAgAAb___________gmlkgnY0gmlwhANFIw2Jc2VjcDI1NmsxoQMa-fWEy9UJHfOl_lix3wdY5qust78sHAqZnWwEiyqKgYhzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              // GnosisDAO Bootnode: 35.206.174.92
              "enr:-KG4QF7z4LUdMfgwvh-fS-MDv_1hPSUCqGfyOWGLNJuoBHKFAMSHz8geQn8v3qDDbuSQKud3WIAjKqR4gqJoLBUEJ08ZhGV0aDKQDc1ElgAAAG___________4JpZIJ2NIJpcIQjzq5ciXNlY3AyNTZrMaECt7YO363pV54d3QdgnluL5kxzhCR_k0yM9C-G6bqMGoKDdGNwgiMog3VkcIIjKA",
              // GnosisDAO Bootnode: 35.210.126.23
              "enr:-LK4QCUTEmZrT1AgCKdyVgwnHL5J0VSoxsyjruAtwo-owBTBVEOyAnQRVNXlcW5aL-ycntk5oHDrKCR-DXZAlUAKpjEBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCdM7Z1BAAAb___________gmlkgnY0gmlwhCPSfheJc2VjcDI1NmsxoQNpdf8U9pzsU9m6Hzgd1rmTI-On-QImJnkZBGqDp4org4N0Y3CCIyiDdWRwgiMo");
    }

    private Builder applyHoleskyNetworkDefaults() {
      return applyTestnetDefaults()
          .constants(HOLESKY.configName())
          .startupTimeoutSeconds(120)
          .trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME)
          .eth1DepositContractDeployBlock(0)
          .defaultInitialStateFromUrl(
              "https://checkpoint-sync.holesky.ethpandaops.io/eth/v2/debug/beacon/states/finalized")
          .customGenesisState(
              "https://github.com/eth-clients/holesky/raw/59cb4fcbc8b39e431c1d737937ae8188f4a19a98/custom_config_data/genesis.ssz")
          .discoveryBootnodes(
              // EF bootnodes
              "enr:-Ku4QFo-9q73SspYI8cac_4kTX7yF800VXqJW4Lj3HkIkb5CMqFLxciNHePmMt4XdJzHvhrCC5ADI4D_GkAsxGJRLnQBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAhnTT-AQFwAP__________gmlkgnY0gmlwhLKAiOmJc2VjcDI1NmsxoQORcM6e19T1T9gi7jxEZjk_sjVLGFscUNqAY9obgZaxbIN1ZHCCIyk",
              "enr:-Ku4QPG7F72mbKx3gEQEx07wpYYusGDh-ni6SNkLvOS-hhN-BxIggN7tKlmalb0L5JPoAfqD-akTZ-gX06hFeBEz4WoBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpAhnTT-AQFwAP__________gmlkgnY0gmlwhJK-DYCJc2VjcDI1NmsxoQKLVXFOhp2uX6jeT0DvvDpPcU8FWMjQdR4wMuORMhpX24N1ZHCCIyk",
              "enr:-KG4QF6d6vMSboSujAXTI4vYqArccm0eIlXfcxf2Lx_VE1q6IkQo_2D5LAO3ZSBVUs0w5rrVDmABJZuMzISe_pZundADhGV0aDKQqX6DZjABcAAAAQAAAAAAAIJpZIJ2NIJpcISygIjpiXNlY3AyNTZrMaEDF3aSa7QSCvdqLpANNd8GML4PLEZVg45fKQwMWhDZjd2DdGNwgiMog3VkcIIjKA",
              "enr:-Ly4QJLXSSAj3ggPBIcodvBU6IyfpU_yW7E9J-5syoJorBuvcYj_Fokcjr303bQoTdWXADf8po0ssh75Mr5wVGzZZsMBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpCpfoNmMAFwAAABAAAAAAAAgmlkgnY0gmlwhJK-DYCJc2VjcDI1NmsxoQJrIlXIQDvQ6t9yDySqJYDXgZgLXzTvq8W7OI51jfmxJohzeW5jbmV0cwCDdGNwgiMog3VkcIIjKA",
              // Sigma Prime
              "enr:-Le4QLoE1wFHSlGcm48a9ZESb_MRLqPPu6G0vHqu4MaUcQNDHS69tsy-zkN0K6pglyzX8m24mkb-LtBcbjAYdP1uxm4BhGV0aDKQabfZdAQBcAAAAQAAAAAAAIJpZIJ2NIJpcIQ5gR6Wg2lwNpAgAUHQBwEQAAAAAAAAADR-iXNlY3AyNTZrMaEDPMSNdcL92uNIyCsS177Z6KTXlbZakQqxv3aQcWawNXeDdWRwgiMohHVkcDaCI4I",
              // TEKU bootnode
              "enr:-LS4QG0uV4qvcpJ-HFDJRGBmnlD3TJo7yc4jwK8iP7iKaTlfQ5kZvIDspLMJhk7j9KapuL9yyHaZmwTEZqr10k9XumyCEcmHYXR0bmV0c4gAAAAABgAAAIRldGgykGm32XQEAXAAAAEAAAAAAACCaWSCdjSCaXCErK4j-YlzZWNwMjU2azGhAgfWRBEJlb7gAhXIB5ePmjj2b8io0UpEenq1Kl9cxStJg3RjcIIjKIN1ZHCCIyg");
    }

    private Builder applyEphemeryNetworkDefaults() {
      return applyTestnetDefaults()
          .constants(EPHEMERY.configName())
          .startupTimeoutSeconds(120)
          .trustedSetupFromClasspath(MAINNET_TRUSTED_SETUP_FILENAME)
          .eth1DepositContractDeployBlock(0)
          .checkpointSyncUrl("https://ephemery.beaconstate.ethstaker.cc")
          .discoveryBootnodes(
              "enr:-Iq4QNMYHuJGbnXyBj6FPS2UkOQ-hnxT-mIdNMMr7evR9UYtLemaluorL6J10RoUG1V4iTPTEbl3huijSNs5_ssBWFiGAYhBNHOzgmlkgnY0gmlwhIlKy_CJc2VjcDI1NmsxoQNULnJBzD8Sakd9EufSXhM4rQTIkhKBBTmWVJUtLCp8KoN1ZHCCIyk",
              "enr:-LK4QDvXfoKH4pVoVoJx3vz0q-3nFtxYKgIacrYPuorPO-KrGlOwQOlCDEPh0e_1x9O2Ob6YWajVU6y7IjIGYOQfXkwEh2F0dG5ldHOIAAAAAACAAQCEZXRoMpDKcygcUAAQG___________gmlkgnY0gmlwhIlKy_CJc2VjcDI1NmsxoQOqgG9xgvsFBhOI6mfWosFJheJOxvVz2zlbQzMeK-S7dIN0Y3CCI4yDdWRwgiOM",
              "enr:-Jq4QI0JCZcwDmfiuBbzjtmE_QTQVi4-jRUJko5RMq1RCjQeTXncHIoCtriXgU_FrCtl9R2AKSyHcmF0fCuS4pIL4h0BhGV0aDKQynMoHFAAEBv__________4JpZIJ2NIJpcIRBbZouiXNlY3AyNTZrMaEC8GXWOjFPp85Cpv9CY6V-CfzNPkMm0VyNNeuiFxfjg3mDdWRwgiMp",
              "enr:-Iq4QIc297-de1P6hznMX2cIdVsQkve9BD9NUsJ7vVQa7eh5UpekA9rLid5A-yLiS3gZwOGugYZPi58x76zNs2cEQFCGAYhBJlTYgmlkgnY0gmlwhEFtmi6Jc2VjcDI1NmsxoQJDyix-IHa_mVwLBEN9NeG8I-RUjNQK_MGxk9OqRQUAtIN1ZHCCIyg",
              "enr:-MS4QPNnPV4zZJkeytVQTm8fg3Mrtyq7l3oVy9ht4229w5OUOftE2EsXAfgxEopHavIPTzdWGchD-rXDh_eS6fdF_dsBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpDKcygcUAAQG___________gmlkgnY0gmlwhKfrAbmEcXVpY4IjUYlzZWNwMjU2azGhAwnM8CLwGlnZFe7XhDoC4PSYZMvWypChdu0NX9vmCGjKiHN5bmNuZXRzAIN0Y3CCI1CDdWRwgiNQ");
    }

    private Optional<Integer> validateAndParseEpochsStoreBlobs(final String epochsStoreBlobs) {
      if (epochsStoreBlobs == null || epochsStoreBlobs.isBlank()) {
        return Optional.empty();
      }
      if (epochsStoreBlobs.equalsIgnoreCase(EPOCHS_STORE_BLOBS_MAX_KEYWORD)) {
        return Optional.of(MAX_EPOCHS_STORE_BLOBS);
      }
      final int epochsStoreBlobsInt;
      try {
        epochsStoreBlobsInt = Integer.parseInt(epochsStoreBlobs);
      } catch (final NumberFormatException ex) {
        throw new InvalidConfigurationException(
            "Expecting number or "
                + EPOCHS_STORE_BLOBS_MAX_KEYWORD
                + " keyword for the number of the epochs to store blobs for");
      }
      checkArgument(
          epochsStoreBlobsInt > 0, "Number of the epochs to store blobs for should be > 0");
      return Optional.of(epochsStoreBlobsInt);
    }

    public Builder forkChoiceLateBlockReorgEnabled(final boolean forkChoiceLateBlockReorgEnabled) {
      this.forkChoiceLateBlockReorgEnabled = forkChoiceLateBlockReorgEnabled;
      return this;
    }

    public Builder forkChoiceUpdatedAlwaysSendPayloadAttributes(
        final boolean forkChoiceUpdatedAlwaysSendPayloadAttributes) {
      this.forkChoiceUpdatedAlwaysSendPayloadAttributes =
          forkChoiceUpdatedAlwaysSendPayloadAttributes;
      return this;
    }
  }
}
