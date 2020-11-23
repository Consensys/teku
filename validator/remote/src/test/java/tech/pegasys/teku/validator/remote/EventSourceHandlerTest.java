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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.launchdarkly.eventsource.MessageEvent;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class EventSourceHandlerTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  private final EventSourceHandler handler = new EventSourceHandler(validatorTimingChannel);

  @Test
  void onOpen_shouldNotifyOfPotentialMissedEvents() {
    handler.onOpen();

    verify(validatorTimingChannel).onPossibleMissedEvents();
  }

  @Test
  void onMessage_shouldHandleHeadEvent() throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final HeadEvent event =
        new HeadEvent(
            slot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());
    handler.onMessage(EventType.head.name(), new MessageEvent(jsonProvider.objectToJSON(event)));

    verify(validatorTimingChannel).onAttestationCreationDue(slot);
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldHandleChainReorgEvent() throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final UInt64 reorgDepth = UInt64.valueOf(6);
    final UInt64 commonAncestorSlot = UInt64.valueOf(128);
    final ChainReorgEvent event =
        new ChainReorgEvent(
            slot,
            reorgDepth,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEpoch());
    handler.onMessage(
        EventType.chain_reorg.name(), new MessageEvent(jsonProvider.objectToJSON(event)));

    verify(validatorTimingChannel).onChainReorg(event.slot, commonAncestorSlot);
    verifyNoMoreInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldHandleInvalidMessage() throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final HeadEvent event =
        new HeadEvent(
            slot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            false,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());
    // Head message with a reorg type
    final MessageEvent messageEvent = new MessageEvent(jsonProvider.objectToJSON(event));
    assertDoesNotThrow(() -> handler.onMessage(EventType.chain_reorg.name(), messageEvent));
    verifyNoInteractions(validatorTimingChannel);
  }

  @Test
  void onMessage_shouldHandleUnparsableMessage() {
    // Head message with a reorg type
    final MessageEvent messageEvent = new MessageEvent("{this isn't json!}");
    assertDoesNotThrow(() -> handler.onMessage(EventType.chain_reorg.name(), messageEvent));
    verifyNoInteractions(validatorTimingChannel);
  }
}
