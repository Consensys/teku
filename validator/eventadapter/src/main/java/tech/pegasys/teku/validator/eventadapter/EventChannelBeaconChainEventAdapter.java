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

import com.google.common.eventbus.Subscribe;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

/**
 * Converts events from the {@link com.google.common.eventbus.EventBus} to the new validator client
 * {@link EventChannels}.
 */
public class EventChannelBeaconChainEventAdapter
    implements SlotEventsChannel, ReorgEventChannel, BeaconChainEventAdapter {

  private final ServiceConfig config;
  private final ValidatorTimingChannel validatorTimingChannel;

  public EventChannelBeaconChainEventAdapter(final ServiceConfig config) {
    this.config = config;
    this.validatorTimingChannel =
        config.getEventChannels().getPublisher(ValidatorTimingChannel.class);
  }

  @Override
  public SafeFuture<Void> start() {
    config.getEventBus().register(this);
    config
        .getEventChannels()
        .subscribe(SlotEventsChannel.class, this)
        .subscribe(ReorgEventChannel.class, this);

    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }

  @Subscribe
  public void onBroadcastAttestationEvent(final BroadcastAttestationEvent event) {
    validatorTimingChannel.onAttestationCreationDue(event.getNodeSlot());
  }

  @Subscribe
  public void onAggregationEvent(BroadcastAggregatesEvent event) {
    validatorTimingChannel.onAttestationAggregationDue(event.getSlot());
  }

  @Subscribe
  public void onImportedBlockEvent(ImportedBlockEvent event) {
    validatorTimingChannel.onBlockImportedForSlot(event.getBlock().getSlot());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    validatorTimingChannel.onSlot(slot);
    validatorTimingChannel.onBlockProductionDue(slot);
  }

  @Override
  public void reorgOccurred(
      final Bytes32 bestBlockRoot,
      final UInt64 bestSlot,
      final Bytes32 bestStateRoot,
      final Bytes32 oldBestBlockRoot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot) {
    validatorTimingChannel.onChainReorg(bestSlot, commonAncestorSlot);
  }
}
