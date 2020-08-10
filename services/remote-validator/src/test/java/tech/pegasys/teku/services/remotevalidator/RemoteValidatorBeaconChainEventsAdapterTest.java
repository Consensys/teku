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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

class RemoteValidatorBeaconChainEventsAdapterTest {

  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);

  private final BeaconChainEventsListener listener = mock(BeaconChainEventsListener.class);

  private final EventBus eventBus = mock(EventBus.class);

  private final EventChannels eventChannels = mock(EventChannels.class);

  private final ArgumentCaptor<BeaconChainEvent> beaconChainEventArgCaptor =
      ArgumentCaptor.forClass(BeaconChainEvent.class);

  private RemoteValidatorBeaconChainEventsAdapter eventsAdapter =
      new RemoteValidatorBeaconChainEventsAdapter(serviceConfig, listener);

  @BeforeEach
  public void beforeEach() {
    reset(serviceConfig, listener, eventBus, eventChannels);

    lenient().when(serviceConfig.getEventBus()).thenReturn(eventBus);
    lenient().when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    lenient().when(eventChannels.subscribe(any(), any())).thenReturn(eventChannels);
  }

  @Test
  public void whenEventsAdapterStarts_ShouldRegisterItselfOnEventBus() {
    eventsAdapter.start();

    verify(eventBus).register(same(eventsAdapter));
  }

  @Test
  public void whenEventsAdapterStarts_ShouldSubscribeToEventChannel() {
    eventsAdapter.start();

    verify(eventChannels).subscribe(eq(SlotEventsChannel.class), same(eventsAdapter));
    verify(eventChannels).subscribe(eq(ReorgEventChannel.class), same(eventsAdapter));
  }

  @Test
  public void onBroadcastAttestationEvent_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final BroadcastAttestationEvent event = new BroadcastAttestationEvent(UInt64.ONE);
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.ATTESTATION, UInt64.ONE);

    eventsAdapter.onBroadcastAttestationEvent(event);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void onAggregationEvent_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final BroadcastAggregatesEvent event = new BroadcastAggregatesEvent(UInt64.ONE);
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.AGGREGATION, UInt64.ONE);

    eventsAdapter.onAggregationEvent(event);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void onImportedBlockEvent_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final SignedBeaconBlock signedBeaconBlock = mock(SignedBeaconBlock.class);
    when(signedBeaconBlock.getSlot()).thenReturn(UInt64.ONE);
    final ImportedBlockEvent event = new ImportedBlockEvent(signedBeaconBlock);
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.IMPORTED_BLOCK, UInt64.ONE);

    eventsAdapter.onImportedBlockEvent(event);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void onSlot_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final UInt64 slot = UInt64.ONE;
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.ON_SLOT, slot);

    eventsAdapter.onSlot(slot);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void reorgOccurred_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final UInt64 slot = UInt64.ONE;
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.REORG_OCCURRED, slot);

    eventsAdapter.reorgOccurred(Bytes32.ZERO, slot);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }
}
