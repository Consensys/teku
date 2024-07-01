/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.services.beaconchain.init;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.networks.StateBoostrapConfig;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.beaconchain.WeakSubjectivityInitializer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityCalculator;

public class RecentChainDataStateInitializer {

  @Inject TimeProvider timeProvider;
  @Inject BeaconChainConfiguration beaconConfig;
  @Inject Spec spec;
  @Inject SpecModule.CurrentSlotProvider currentSlotProvider;
  @Inject WeakSubjectivityInitializer weakSubjectivityInitializer;
  @Inject EventLogger eventLogger;
  @Inject StatusLogger statusLogger;

  @Inject
  public RecentChainDataStateInitializer() {}

  public void setupInitialState(final RecentChainData recentChainData) {

    final Optional<AnchorPoint> initialAnchor =
        tryLoadingAnchorPointFromInitialState(beaconConfig.eth2NetworkConfig())
            .or(
                () ->
                    attemptToLoadAnchorPoint(
                        beaconConfig
                            .eth2NetworkConfig()
                            .getNetworkBoostrapConfig()
                            .getGenesisState()));

    /*
     If flag to allow sync outside of weak subjectivity period has been set, we pass an instance of
     WeakSubjectivityPeriodCalculator to the WeakSubjectivityInitializer. Otherwise, we pass an Optional.empty().
    */
    boolean isAllowSyncOutsideWeakSubjectivityPeriod =
        beaconConfig
            .eth2NetworkConfig()
            .getNetworkBoostrapConfig()
            .isAllowSyncOutsideWeakSubjectivityPeriod();

    final Optional<WeakSubjectivityCalculator> maybeWsCalculator;
    if (isAllowSyncOutsideWeakSubjectivityPeriod) {
      maybeWsCalculator = Optional.empty();
    } else {
      maybeWsCalculator =
          Optional.of(WeakSubjectivityCalculator.create(beaconConfig.weakSubjectivity()));
    }

    // Validate
    initialAnchor.ifPresent(
        anchor -> {
          final UInt64 currentSlot =
              currentSlotProvider.getCurrentSlot(anchor.getState().getGenesisTime());
          weakSubjectivityInitializer.validateInitialAnchor(
              anchor, currentSlot, spec, maybeWsCalculator);
        });

    if (initialAnchor.isPresent()) {
      final AnchorPoint anchor = initialAnchor.get();
      recentChainData.initializeFromAnchorPoint(anchor, timeProvider.getTimeInSeconds());
      if (anchor.isGenesis()) {
        eventLogger.genesisEvent(
            anchor.getStateRoot(),
            recentChainData.getBestBlockRoot().orElseThrow(),
            anchor.getState().getGenesisTime());
      }
    } else if (beaconConfig.interopConfig().isInteropEnabled()) {
      setupInteropState(recentChainData);
    } else if (!beaconConfig.powchainConfig().isEnabled()) {
      throw new InvalidConfigurationException(
          "ETH1 is disabled but initial state is unknown. Enable ETH1 or specify an initial state"
              + ".");
    }
  }

  private Optional<AnchorPoint> tryLoadingAnchorPointFromInitialState(
      final Eth2NetworkConfiguration networkConfiguration) {
    Optional<AnchorPoint> initialAnchor = Optional.empty();

    try {
      initialAnchor =
          attemptToLoadAnchorPoint(
              networkConfiguration.getNetworkBoostrapConfig().getInitialState());
    } catch (final InvalidConfigurationException e) {
      final StateBoostrapConfig stateBoostrapConfig =
          networkConfiguration.getNetworkBoostrapConfig();
      if (stateBoostrapConfig.isUsingCustomInitialState()
          && !stateBoostrapConfig.isUsingCheckpointSync()) {
        throw e;
      }
      statusLogger.warnFailedToLoadInitialState(e.getMessage());
    }

    return initialAnchor;
  }

  protected Optional<AnchorPoint> attemptToLoadAnchorPoint(final Optional<String> initialState) {
    return weakSubjectivityInitializer.loadInitialAnchorPoint(spec, initialState);
  }

  protected void setupInteropState(final RecentChainData recentChainData) {
    final InteropConfig config = beaconConfig.interopConfig();
    statusLogger.generatingMockStartGenesis(
        config.getInteropGenesisTime(), config.getInteropNumberOfValidators());

    Optional<ExecutionPayloadHeader> executionPayloadHeader = Optional.empty();
    if (config.getInteropGenesisPayloadHeader().isPresent()) {
      try {
        executionPayloadHeader =
            Optional.of(
                spec.deserializeJsonExecutionPayloadHeader(
                    new ObjectMapper(),
                    config.getInteropGenesisPayloadHeader().get().toFile(),
                    GENESIS_SLOT));
      } catch (IOException e) {
        throw new RuntimeException(
            "Unable to load payload header from " + config.getInteropGenesisPayloadHeader().get(),
            e);
      }
    }

    final BeaconState genesisState =
        new GenesisStateBuilder()
            .spec(spec)
            .genesisTime(config.getInteropGenesisTime())
            .addMockValidators(config.getInteropNumberOfValidators())
            .executionPayloadHeader(executionPayloadHeader)
            .build();

    recentChainData.initializeFromGenesis(genesisState, timeProvider.getTimeInSeconds());

    eventLogger.genesisEvent(
        genesisState.hashTreeRoot(),
        recentChainData.getBestBlockRoot().orElseThrow(),
        genesisState.getGenesisTime());
  }
}
