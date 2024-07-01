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
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionChannel;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannelSubscriber;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.services.beaconchain.init.AsyncRunnerModule.BeaconAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

@Module
public interface ChannelsModule {

  // Publishers

  @Provides
  static SlotEventsChannel slotEventsChannel(final EventChannels eventChannels) {
    return eventChannels.getPublisher(SlotEventsChannel.class);
  }

  @Provides
  static ExecutionLayerChannel executionLayerChannel(
      final EventChannels eventChannels, @BeaconAsyncRunner final AsyncRunner asyncRunner) {
    return eventChannels.getPublisher(ExecutionLayerChannel.class, asyncRunner);
  }

  @Provides
  static BlockImportChannel blockImportChannel(
      final EventChannels eventChannels, @BeaconAsyncRunner final AsyncRunner asyncRunner) {
    return eventChannels.getPublisher(BlockImportChannel.class, asyncRunner);
  }

  @Provides
  static CombinedStorageChannel combinedStorageChannel(
      final EventChannels eventChannels, @BeaconAsyncRunner final AsyncRunner asyncRunner) {
    return eventChannels.getPublisher(CombinedStorageChannel.class, asyncRunner);
  }

  @Provides
  static ValidatorApiChannel validatorApiChannel(
      final EventChannels eventChannels, @BeaconAsyncRunner final AsyncRunner asyncRunner) {
    return eventChannels.getPublisher(ValidatorApiChannel.class, asyncRunner);
  }

  @Provides
  static ActiveValidatorChannel activeValidatorChannel(
      final EventChannels eventChannels, @BeaconAsyncRunner final AsyncRunner asyncRunner) {
    return eventChannels.getPublisher(ActiveValidatorChannel.class, asyncRunner);
  }

  @Provides
  static Eth1DepositStorageChannel eth1DepositStorageChannel(
      final EventChannels eventChannels, @BeaconAsyncRunner final AsyncRunner asyncRunner) {
    return eventChannels.getPublisher(Eth1DepositStorageChannel.class, asyncRunner);
  }

  @Provides
  static ExecutionClientVersionChannel executionClientVersionChannel(
      final EventChannels eventChannels) {
    return eventChannels.getPublisher(ExecutionClientVersionChannel.class);
  }

  @Provides
  static BlockGossipChannel blockGossipChannel(final EventChannels eventChannels) {
    return eventChannels.getPublisher(BlockGossipChannel.class);
  }

  @Provides
  static BlobSidecarGossipChannel blobSidecarGossipChannel(
      final EventChannels eventChannels, final Spec spec) {
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      return eventChannels.getPublisher(BlobSidecarGossipChannel.class);
    } else {
      return BlobSidecarGossipChannel.NOOP;
    }
  }

  @Provides
  static ReceivedBlockEventsChannel receivedBlockEventsChannel(final EventChannels eventChannels) {
    return eventChannels.getPublisher(ReceivedBlockEventsChannel.class);
  }

  @Provides
  static ChainHeadChannel chainHeadChannel(final EventChannels eventChannels) {
    return eventChannels.getPublisher(ChainHeadChannel.class);
  }

  @Provides
  static VoteUpdateChannel voteUpdateChannel(final EventChannels eventChannels) {
    return eventChannels.getPublisher(VoteUpdateChannel.class);
  }

  @Provides
  static FinalizedCheckpointChannel finalizedCheckpointChannel(
      final EventChannels eventChannels, @BeaconAsyncRunner final AsyncRunner asyncRunner) {
    return eventChannels.getPublisher(FinalizedCheckpointChannel.class, asyncRunner);
  }

  @Provides
  static ValidatorTimingChannel validatorTimingChannel(final EventChannels eventChannels) {
    return eventChannels.getPublisher(ValidatorTimingChannel.class);
  }

  // Subscribers

  @Provides
  static EventChannelSubscriber<SlotEventsChannel> slotEventsChannelSubscriber(
      final EventChannels eventChannels) {
    return eventChannels.createSubscriber(SlotEventsChannel.class);
  }

  @Provides
  static EventChannelSubscriber<FinalizedCheckpointChannel> finalizedCheckpointChannelSubscriber(
      final EventChannels eventChannels) {
    return eventChannels.createSubscriber(FinalizedCheckpointChannel.class);
  }

  @Provides
  static EventChannelSubscriber<ChainHeadChannel> chainHeadChannelSubscriber(
      final EventChannels eventChannels) {
    return eventChannels.createSubscriber(ChainHeadChannel.class);
  }

  @Provides
  static EventChannelSubscriber<Eth1EventsChannel> eth1EventsChannelSubscriber(
      final EventChannels eventChannels) {
    return eventChannels.createSubscriber(Eth1EventsChannel.class);
  }

  @Provides
  static EventChannelSubscriber<ExecutionClientVersionChannel>
      executionClientVersionChannelSubscriber(final EventChannels eventChannels) {
    return eventChannels.createSubscriber(ExecutionClientVersionChannel.class);
  }

  @Provides
  static EventChannelSubscriber<ExecutionClientEventsChannel>
      executionClientEventsChannelSubscriber(final EventChannels eventChannels) {
    return eventChannels.createSubscriber(ExecutionClientEventsChannel.class);
  }

  @Provides
  static EventChannelSubscriber<ReceivedBlockEventsChannel> receivedBlockEventsChannelSubscriber(
      final EventChannels eventChannels) {
    return eventChannels.createSubscriber(ReceivedBlockEventsChannel.class);
  }

  @Provides
  static EventChannelSubscriber<BlockImportChannel> blockImportChannelSubscriber(
      final EventChannels eventChannels) {
    return eventChannels.createSubscriber(BlockImportChannel.class);
  }

  @Provides
  static EventChannelSubscriber<ValidatorApiChannel> validatorApiChannelSubscriber(
      final EventChannels eventChannels, final BeaconRestApiConfig beaconRestApiConfig) {
    return eventChannels.createSubscriberMultithreaded(
        ValidatorApiChannel.class, beaconRestApiConfig.getValidatorThreads());
  }
}
