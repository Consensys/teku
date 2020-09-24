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

package tech.pegasys.teku.beaconrestapi.handlers.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.FinalizedCheckpointEvent;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

public class EventSubscriptionManagerTest {
  private final JsonProvider jsonProvider = new JsonProvider();
  private final DataStructureUtil data = new DataStructureUtil();
  private final ArgumentCaptor<String> stringArgs = ArgumentCaptor.forClass(String.class);
  protected final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  // chain reorg fields
  private final UInt64 slot = UInt64.valueOf("1024100");
  private final UInt64 epoch = compute_epoch_at_slot(slot);
  private final UInt64 depth = UInt64.valueOf(100);
  private final ChainReorgEvent sampleEvent =
      new ChainReorgEvent(
          slot,
          depth,
          data.randomBytes32(),
          data.randomBytes32(),
          data.randomBytes32(),
          data.randomBytes32(),
          epoch);

  private final FinalizedCheckpointEvent sampleCheckpointEvent =
      new FinalizedCheckpointEvent(data.randomBytes32(), data.randomBytes32(), epoch);

  private final AsyncContext async = mock(AsyncContext.class);
  private final EventChannels channels = mock(EventChannels.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse res = mock(HttpServletResponse.class);
  private final ServletResponse srvResponse = mock(ServletResponse.class);
  private final ServletOutputStream outputStream = mock(ServletOutputStream.class);
  private final Context ctx = new Context(req, res, Collections.emptyMap());

  private EventSubscriptionManager manager;

  @BeforeEach
  void setup() throws IOException {
    when(req.getAsyncContext()).thenReturn(async);
    when(async.getResponse()).thenReturn(srvResponse);
    when(srvResponse.getOutputStream()).thenReturn(outputStream);
    final SseClient client1 = new SseClient(ctx);
    manager = new EventSubscriptionManager(chainDataProvider, jsonProvider, channels);
    manager.registerClient(client1);
  }

  @Test
  void shouldPropagateReorgMessages() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=chain_reorg");

    triggerReorgEvent();
    verify(outputStream).print(stringArgs.capture());
    final String eventString = stringArgs.getValue();
    assertThat(eventString).contains("event: chain_reorg\n");
    final ChainReorgEvent event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), ChainReorgEvent.class);

    assertThat(event).isEqualTo(sampleEvent);
  }

  @Test
  void shouldPropagateFinalizedCheckpointMessages() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=finalized_checkpoint");
    when(chainDataProvider.getStateRootFromBlockRoot(sampleCheckpointEvent.block))
        .thenReturn(Optional.of(sampleCheckpointEvent.state));

    triggerFinalizedCheckpointEvent();
    verify(outputStream).print(stringArgs.capture());
    final String eventString = stringArgs.getValue();
    assertThat(eventString).contains("event: finalized_checkpoint\n");
    final FinalizedCheckpointEvent event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), FinalizedCheckpointEvent.class);

    assertThat(event).isEqualTo(sampleCheckpointEvent);
  }

  @Test
  void shouldNotGetFinalizedCheckpointIfNotSubscribed() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=head");

    triggerFinalizedCheckpointEvent();
    verify(outputStream, never()).print(anyString());
  }

  @Test
  void shouldNotGetReorgIfNotSubscribed() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=head");

    triggerReorgEvent();
    verify(outputStream, never()).print(anyString());
  }

  private void triggerFinalizedCheckpointEvent() {
    manager.onNewFinalizedCheckpoint(
        new Checkpoint(sampleCheckpointEvent.epoch, sampleCheckpointEvent.block));
  }

  private void triggerReorgEvent() {
    manager.reorgOccurred(
        sampleEvent.newHeadBlock,
        sampleEvent.slot,
        sampleEvent.newHeadState,
        sampleEvent.oldHeadBlock,
        sampleEvent.oldHeadState,
        sampleEvent.slot.minus(depth));
  }
}
