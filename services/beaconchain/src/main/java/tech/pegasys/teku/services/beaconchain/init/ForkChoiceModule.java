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

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceExecutor;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.ForkChoiceNotifierExecutor;
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
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

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
      DebugDataDumper debugDataDumper,
      TickProcessor tickProcessor,
      MergeTransitionBlockValidator mergeTransitionBlockValidator) {
    return new ForkChoice(
        spec,
        forkChoiceExecutor,
        recentChainData,
        blobSidecarManager,
        forkChoiceNotifier,
        forkChoiceStateProvider,
        tickProcessor,
        mergeTransitionBlockValidator,
        eth2NetworkConfig.isForkChoiceLateBlockReorgEnabled(),
        debugDataDumper,
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

    return new ForkChoiceNotifierImpl(
        forkChoiceStateProvider,
        forkChoiceNotifierExecutor,
        timeProvider,
        spec,
        executionLayer,
        recentChainData,
        proposersDataManager);
  }
}
