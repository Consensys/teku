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

package tech.pegasys.teku.validator.remote;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class WebSocketBeaconChainEventAdapterTest {

  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);
  private final BeaconChainEventMapper mapper = new BeaconChainEventMapper(validatorTimingChannel);

  @BeforeEach
  public void beforeEach() {
    reset(validatorTimingChannel);
  }

  @Test
  public void mapAttestationEvent() {
    final BeaconChainEvent event = new BeaconChainEvent(BeaconChainEvent.ATTESTATION, UInt64.ONE);

    mapper.map(event);

    verify(validatorTimingChannel).onAttestationCreationDue(eq(UInt64.ONE));
  }

  @Test
  public void mapAggregationEvent() {
    final BeaconChainEvent event = new BeaconChainEvent(BeaconChainEvent.AGGREGATION, UInt64.ONE);

    mapper.map(event);

    verify(validatorTimingChannel).onAttestationAggregationDue(eq(UInt64.ONE));
  }

  @Test
  public void mapImportedBlockEvent() {
    final BeaconChainEvent event =
        new BeaconChainEvent(BeaconChainEvent.IMPORTED_BLOCK, UInt64.ONE);

    mapper.map(event);

    verify(validatorTimingChannel).onBlockImportedForSlot(eq(UInt64.ONE));
  }

  @Test
  public void mapOnSlotEvent() {
    final BeaconChainEvent event = new BeaconChainEvent(BeaconChainEvent.ON_SLOT, UInt64.ONE);

    mapper.map(event);

    verify(validatorTimingChannel).onSlot(eq(UInt64.ONE));
    verify(validatorTimingChannel).onBlockProductionDue(eq(UInt64.ONE));
  }

  @Test
  public void mapReorgOccurredEvent() {
    final BeaconChainEvent event =
        new BeaconChainEvent(BeaconChainEvent.REORG_OCCURRED, UInt64.ONE);

    mapper.map(event);

    verify(validatorTimingChannel).onChainReorg(eq(UInt64.ONE));
  }

  @Test
  public void mapNonMappedEvent_ShouldDoNothing() {
    final BeaconChainEvent event = new BeaconChainEvent("foo", UInt64.ONE);

    mapper.map(event);

    verifyNoInteractions(validatorTimingChannel);
  }
}
