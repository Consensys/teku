package tech.pegasys.teku.services.beaconchain.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.networks.StateBoostrapConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration;
import tech.pegasys.teku.services.beaconchain.WeakSubjectivityInitializer;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.services.beaconchain.init.SpecModule.CurrentSlotProvider;
import tech.pegasys.teku.services.beaconchain.init.WSModule.WeakSubjectivityPeriodValidator;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.EarliestAvailableBlockSlot;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityCalculator;

import java.io.IOException;
import java.util.Optional;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

@Module
public interface StorageModule {

  String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";

  @Binds
  StorageQueryChannel bindStorageQueryChannel(CombinedStorageChannel combinedStorageChannel);

  @Binds
  StorageUpdateChannel bindStorageUpdateChannel(CombinedStorageChannel combinedStorageChannel);

  @Provides
  @Singleton
  static KeyValueStore<String, Bytes> keyValueStore(DataDirLayout dataDirLayout) {
    return new FileKeyValueStore(
        dataDirLayout.getBeaconDataDirectory().resolve(KEY_VALUE_STORE_SUBDIRECTORY));
  }

  @Provides
  @Singleton
  static EarliestAvailableBlockSlot earliestAvailableBlockSlot(
      StoreConfig storeConfig,
      TimeProvider timeProvider,
      StorageQueryChannel storageQueryChannel) {
    return new EarliestAvailableBlockSlot(
        storageQueryChannel, timeProvider, storeConfig.getEarliestAvailableBlockSlotFrequency());
  }

  @Provides
  @Singleton
  static CombinedChainDataClient combinedChainDataClient(
      Spec spec,
      StorageQueryChannel storageQueryChannel,
      RecentChainData recentChainData,
      EarliestAvailableBlockSlot earliestAvailableBlockSlot) {
    return new CombinedChainDataClient(
        recentChainData, storageQueryChannel, spec, earliestAvailableBlockSlot);
  }

  @Provides
  @Singleton
  static SafeFuture<RecentChainData> recentChainDataFuture(
      @BeaconAsyncRunner AsyncRunner beaconAsyncRunner,
      TimeProvider timeProvider,
      MetricsSystem metricsSystem,
      Spec spec,
      StoreConfig storeConfig,
      StorageQueryChannel storageQueryChannel,
      StorageUpdateChannel storageUpdateChannel,
      BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      VoteUpdateChannel voteUpdateChannel,
      FinalizedCheckpointChannel finalizedCheckpointChannel,
      ChainHeadChannel chainHeadChannel,
      ValidatorIsConnectedProvider validatorIsConnectedProvider) {

    return StorageBackedRecentChainData.create(
        metricsSystem,
        storeConfig,
        beaconAsyncRunner,
        timeProvider,
        blockBlobSidecarsTrackersPool::getBlock,
        blockBlobSidecarsTrackersPool::getBlobSidecar,
        storageQueryChannel,
        storageUpdateChannel,
        voteUpdateChannel,
        finalizedCheckpointChannel,
        chainHeadChannel,
        validatorIsConnectedProvider,
        spec);
  }

  @Provides
  @Singleton
  // TODO producer ?
  static RecentChainData recentChainData(
      Eth2NetworkConfiguration eth2NetworkConfig,
      SafeFuture<RecentChainData> recentChainDataFuture,
      StatusLogger statusLogger,
      Lazy<WeakSubjectivityPeriodValidator> weakSubjectivityPeriodValidator,
      Lazy<RecentChainDataStateInitializer> recentChainDataStateInitializer) {

    RecentChainData recentChainData = recentChainDataFuture.join();

    boolean isAllowSyncOutsideWeakSubjectivityPeriod =
        eth2NetworkConfig.getNetworkBoostrapConfig().isAllowSyncOutsideWeakSubjectivityPeriod();
    boolean isUsingCustomInitialState =
        eth2NetworkConfig.getNetworkBoostrapConfig().isUsingCustomInitialState();

    if (isAllowSyncOutsideWeakSubjectivityPeriod) {
      statusLogger.warnIgnoringWeakSubjectivityPeriod();
    }

    // Setup chain storage
    if (recentChainData.isPreGenesis()) {
      recentChainDataStateInitializer.get().setupInitialState(recentChainData);
    } else {
      if (isUsingCustomInitialState) {
        statusLogger.warnInitialStateIgnored();
      }
      if (!isAllowSyncOutsideWeakSubjectivityPeriod) {
        weakSubjectivityPeriodValidator.get().validate(recentChainData);
      }
    }

    return recentChainData;
  }

  class RecentChainDataStateInitializer {

    @Inject TimeProvider timeProvider;
    @Inject BeaconChainConfiguration beaconConfig;
    @Inject Spec spec;
    @Inject CurrentSlotProvider currentSlotProvider;
    @Inject WeakSubjectivityInitializer weakSubjectivityInitializer;
    @Inject EventLogger eventLogger;
    @Inject StatusLogger statusLogger;

    public void setupInitialState(RecentChainData recentChainData) {

      final Optional<AnchorPoint> initialAnchor =
          tryLoadingAnchorPointFromInitialState(beaconConfig.eth2NetworkConfig())
              .or(
                  () ->
                      attemptToLoadAnchorPoint(
                          beaconConfig.eth2NetworkConfig().getNetworkBoostrapConfig().getGenesisState()));

    /*
     If flag to allow sync outside of weak subjectivity period has been set, we pass an instance of
     WeakSubjectivityPeriodCalculator to the WeakSubjectivityInitializer. Otherwise, we pass an Optional.empty().
    */
      boolean isAllowSyncOutsideWeakSubjectivityPeriod =
          beaconConfig.eth2NetworkConfig().getNetworkBoostrapConfig().isAllowSyncOutsideWeakSubjectivityPeriod();

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
            final UInt64 currentSlot = currentSlotProvider.getCurrentSlot(anchor.getState().getGenesisTime());
            weakSubjectivityInitializer.validateInitialAnchor(anchor, currentSlot, spec, maybeWsCalculator);
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

    protected void setupInteropState(RecentChainData recentChainData) {
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
}
