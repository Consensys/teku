package tech.pegasys.teku.services.beaconchain.init;

import dagger.Module;
import dagger.Provides;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceExecutor;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceNotifierExecutor;
import tech.pegasys.teku.services.beaconchain.init.PowModule.ProposerDefaultFeeRecipient;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierImpl;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.util.P2PDebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

import javax.inject.Singleton;
import java.util.Optional;

@Module
public interface ForkChoiceModule {

  @Provides
  @Singleton
  static ForkChoice forkChoice(
      Spec spec,
      Eth2NetworkConfiguration eth2NetworkConfig,
      @ForkChoiceExecutor AsyncRunnerEventThread forkChoiceExecutor,
      MetricsSystem metricsSystem,
      RecentChainData recentChainData,
      BlobSidecarManager blobSidecarManager,
      ForkChoiceNotifier forkChoiceNotifier,
      ForkChoiceStateProvider forkChoiceStateProvider,
      P2PDebugDataDumper p2pDebugDataDumper,
      MergeTransitionBlockValidator mergeTransitionBlockValidator) {
    return new ForkChoice(
        spec,
        forkChoiceExecutor,
        recentChainData,
        blobSidecarManager,
        forkChoiceNotifier,
        forkChoiceStateProvider,
        // TODO to revise
        new TickProcessor(spec, recentChainData),
        mergeTransitionBlockValidator,
        eth2NetworkConfig.isForkChoiceLateBlockReorgEnabled(),
        p2pDebugDataDumper,
        metricsSystem);
  }

  @Provides
  @Singleton
  static ForkChoiceTrigger forkChoiceTrigger(ForkChoice forkChoice) {
    return new ForkChoiceTrigger(forkChoice);
  }

  @Provides
  @Singleton
  static ForkChoiceStateProvider forkChoiceStateProvider(
      @ForkChoiceExecutor AsyncRunnerEventThread forkChoiceExecutor,
      RecentChainData recentChainData) {
    return new ForkChoiceStateProvider(forkChoiceExecutor, recentChainData);
  }

  @Provides
  @Singleton
  static ForkChoiceNotifier forkChoiceNotifier(
      Spec spec,
      @ForkChoiceNotifierExecutor AsyncRunnerEventThread forkChoiceNotifierExecutor,
      TimeProvider timeProvider,
      ProposersDataManager proposersDataManager,
      ExecutionLayerChannel executionLayer,
      RecentChainData recentChainData,
      ForkChoiceStateProvider forkChoiceStateProvider) {

    forkChoiceNotifierExecutor.start(); // TODO why ?

    return
        new ForkChoiceNotifierImpl(
            forkChoiceStateProvider,
            forkChoiceNotifierExecutor,
            timeProvider,
            spec,
            executionLayer,
            recentChainData,
            proposersDataManager);
  }
}
