/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.remote.eventsource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.launchdarkly.eventsource.MessageEvent;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class EventSourceHandlerTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);
  final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final EventSourceHandler handler =
      new EventSourceHandler(validatorTimingChannel, metricsSystem, true);

  @Test
  void onOpen_shouldNotifyOfPotentialMissedEvents() {
    handler.onOpen();

    verify(validatorTimingChannel).onPossibleMissedEvents();
  }

  @Test
  void onMessage_shouldHandleHeadEvent() throws Exception {
    final UInt64 slot = UInt64.valueOf(134);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final HeadEvent event =
        new HeadEvent(
            slot,
            blockRoot,
            dataStructureUtil.randomBytes32(),
            false,
            false,
            previousDutyDependentRoot,
            currentDutyDependentRoot);
    handler.onMessage(EventType.head.name(), new MessageEvent(jsonProvider.objectToJSON(event)));

    verify(validatorTimingChannel)
        .onHeadUpdate(
            eq(slot), eq(previousDutyDependentRoot), eq(currentDutyDependentRoot), eq(blockRoot));
    verify(validatorTimingChannel).onAttestationCreationDue(slot);
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

  @Test
  void onHeadEvent_shouldNotGenerateEarlyAttestationsIfNotEnabled() throws Exception {
    final EventSourceHandler onTimeHandler =
        new EventSourceHandler(validatorTimingChannel, metricsSystem, false);

    final UInt64 slot = UInt64.valueOf(134);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final HeadEvent event =
        new HeadEvent(
            slot,
            blockRoot,
            dataStructureUtil.randomBytes32(),
            false,
            false,
            previousDutyDependentRoot,
            currentDutyDependentRoot);
    onTimeHandler.onMessage(
        EventType.head.name(), new MessageEvent(jsonProvider.objectToJSON(event)));

    verify(validatorTimingChannel)
        .onHeadUpdate(
            eq(slot), eq(previousDutyDependentRoot), eq(currentDutyDependentRoot), eq(blockRoot));
    verifyNoMoreInteractions(validatorTimingChannel);
  }
}
