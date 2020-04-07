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

package tech.pegasys.artemis.validator.anticorruption;

import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.artemis.util.time.channels.SlotEventsChannel;
import tech.pegasys.artemis.validator.api.ValidatorTimingChannel;

/**
 * Converts events from the {@link com.google.common.eventbus.EventBus} to the new validator client
 * {@link tech.pegasys.artemis.events.EventChannels}.
 */
class BeaconChainEventAdapter implements SlotEventsChannel {

  private final ValidatorTimingChannel validatorTimingChannel;

  public BeaconChainEventAdapter(final ValidatorTimingChannel validatorTimingChannel) {
    this.validatorTimingChannel = validatorTimingChannel;
  }

  @Subscribe
  public void onBroadcastAttestationEvent(final BroadcastAttestationEvent event) {
    validatorTimingChannel.onAttestationCreationDue(event.getNodeSlot());
  }

  @Subscribe
  public void onAggregationEvent(BroadcastAggregatesEvent event) {
    validatorTimingChannel.onAttestationAggregationDue(event.getSlot());
  }

  @Override
  public void onSlot(final UnsignedLong slot) {
    validatorTimingChannel.onSlot(slot);
    validatorTimingChannel.onBlockProductionDue(slot);
  }
}
