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

package tech.pegasys.teku.services.remotevalidator;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;
import tech.pegasys.teku.validator.remote.BeaconChainEvent;
import tech.pegasys.teku.validator.remote.BeaconChainReorgEvent;

class RemoteValidatorBeaconChainEventsAdapter implements SlotEventsChannel, ChainHeadChannel {

  private final ServiceConfig config;
  private final BeaconChainEventsListener listener;

  RemoteValidatorBeaconChainEventsAdapter(
      final ServiceConfig config, final BeaconChainEventsListener listener) {
    checkNotNull(config, "ServiceConfig can't be null");
    checkNotNull(listener, "BeaconChainEventsListener can't be null");

    this.config = config;
    this.listener = listener;
  }

  void start() {
    config.getEventBus().register(this);
    config
        .getEventChannels()
        .subscribe(SlotEventsChannel.class, this)
        .subscribe(ChainHeadChannel.class, this);
  }

  @Subscribe
  public void onBroadcastAttestationEvent(final BroadcastAttestationEvent event) {
    final BeaconChainEvent beaconChainEvent =
        new BeaconChainEvent(BeaconChainEvent.ATTESTATION, event.getNodeSlot());
    listener.onEvent(beaconChainEvent);
  }

  @Subscribe
  public void onAggregationEvent(final BroadcastAggregatesEvent event) {
    final BeaconChainEvent beaconChainEvent =
        new BeaconChainEvent(BeaconChainEvent.AGGREGATION, event.getSlot());
    listener.onEvent(beaconChainEvent);
  }

  @Subscribe
  public void onImportedBlockEvent(final ImportedBlockEvent event) {
    final BeaconChainEvent beaconChainEvent =
        new BeaconChainEvent(BeaconChainEvent.IMPORTED_BLOCK, event.getBlock().getSlot());
    listener.onEvent(beaconChainEvent);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final BeaconChainEvent beaconChainEvent = new BeaconChainEvent(BeaconChainEvent.ON_SLOT, slot);
    listener.onEvent(beaconChainEvent);
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final Optional<ReorgContext> optionalReorgContext) {
    optionalReorgContext.ifPresent(
        reorgContext -> {
          final BeaconChainReorgEvent beaconChainEvent =
              new BeaconChainReorgEvent(
                  BeaconChainEvent.REORG_OCCURRED, slot, reorgContext.getCommonAncestorSlot());
          listener.onEvent(beaconChainEvent);
        });
  }
}
