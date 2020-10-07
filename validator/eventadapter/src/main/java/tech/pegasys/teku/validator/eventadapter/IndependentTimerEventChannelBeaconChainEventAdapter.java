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

package tech.pegasys.teku.validator.eventadapter;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.util.time.TimeProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class IndependentTimerEventChannelBeaconChainEventAdapter
    implements ChainHeadChannel, BeaconChainEventAdapter {

  private final TimeBasedEventAdapter timeBasedEventAdapter;
  private final ValidatorTimingChannel validatorTimingChannel;
  private final EventChannels eventChannels;

  public IndependentTimerEventChannelBeaconChainEventAdapter(
      final EventChannels eventChannels,
      final TimeBasedEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel) {
    this.eventChannels = eventChannels;
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    this.validatorTimingChannel = validatorTimingChannel;
  }

  public static BeaconChainEventAdapter create(
      final ServiceConfig serviceConfig, final AsyncRunner asyncRunner) {
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();
    final ValidatorTimingChannel validatorTimingChannel =
        serviceConfig.getEventChannels().getPublisher(ValidatorTimingChannel.class);
    final ValidatorApiChannel validatorApiChannel =
        serviceConfig.getEventChannels().getPublisher(ValidatorApiChannel.class, asyncRunner);
    final TimeBasedEventAdapter timeBasedEventAdapter =
        new TimeBasedEventAdapter(
            new GenesisTimeProvider(validatorApiChannel, asyncRunner),
            new TimedEventQueue(asyncRunner, timeProvider),
            timeProvider,
            validatorTimingChannel);
    return new IndependentTimerEventChannelBeaconChainEventAdapter(
        serviceConfig.getEventChannels(), timeBasedEventAdapter, validatorTimingChannel);
  }

  @Override
  public SafeFuture<Void> start() {
    return timeBasedEventAdapter
        .start()
        .thenRun(() -> eventChannels.subscribe(ChainHeadChannel.class, this));
  }

  @Override
  public SafeFuture<Void> stop() {
    return timeBasedEventAdapter.stop();
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final Optional<ReorgContext> optionalReorgContext) {
    optionalReorgContext.ifPresent(
        reorgContext ->
            validatorTimingChannel.onChainReorg(slot, reorgContext.getCommonAncestorSlot()));
    validatorTimingChannel.onAttestationCreationDue(slot);
  }
}
