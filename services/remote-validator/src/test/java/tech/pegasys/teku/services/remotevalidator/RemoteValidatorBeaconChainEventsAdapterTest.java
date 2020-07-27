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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.teku.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.teku.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

@ExtendWith(MockitoExtension.class)
class RemoteValidatorBeaconChainEventsAdapterTest {

  private RemoteValidatorBeaconChainEventsAdapter eventsAdapter;

  @Mock private ServiceConfig serviceConfig;

  @Mock private BeaconChainEventsListener listener;

  @Mock private EventBus eventBus;

  @Mock private EventChannels eventChannels;

  @Captor private ArgumentCaptor<BeaconChainEvent> beaconChainEventArgCaptor;

  @BeforeEach
  public void beforeEach() {
    lenient().when(serviceConfig.getEventBus()).thenReturn(eventBus);
    lenient().when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    lenient().when(eventChannels.subscribe(any(), any())).thenReturn(eventChannels);

    eventsAdapter = new RemoteValidatorBeaconChainEventsAdapter(serviceConfig, listener);
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
    final BroadcastAttestationEvent event = new BroadcastAttestationEvent(UnsignedLong.ONE);
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.ATTESTATION, UnsignedLong.ONE);

    eventsAdapter.onBroadcastAttestationEvent(event);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void onAggregationEvent_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final BroadcastAggregatesEvent event = new BroadcastAggregatesEvent(UnsignedLong.ONE);
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.AGGREGATION, UnsignedLong.ONE);

    eventsAdapter.onAggregationEvent(event);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void onImportedBlockEvent_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final SignedBeaconBlock signedBeaconBlock = mock(SignedBeaconBlock.class);
    when(signedBeaconBlock.getSlot()).thenReturn(UnsignedLong.ONE);
    final ImportedBlockEvent event = new ImportedBlockEvent(signedBeaconBlock);
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.IMPORTED_BLOCK, UnsignedLong.ONE);

    eventsAdapter.onImportedBlockEvent(event);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void onSlot_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final UnsignedLong slot = UnsignedLong.ONE;
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.ON_SLOT, slot);

    eventsAdapter.onSlot(slot);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }

  @Test
  public void reorgOccurred_EventAdapterShouldInvokeListenerAndAdaptEvent() {
    final UnsignedLong slot = UnsignedLong.ONE;
    final BeaconChainEvent expectedAdaptedEvent =
        new BeaconChainEvent(BeaconChainEvent.REORG_OCCURRED, slot);

    eventsAdapter.reorgOccurred(Bytes32.ZERO, slot);
    verify(listener).onEvent(beaconChainEventArgCaptor.capture());

    assertThat(beaconChainEventArgCaptor.getValue()).isEqualTo(expectedAdaptedEvent);
  }
}
