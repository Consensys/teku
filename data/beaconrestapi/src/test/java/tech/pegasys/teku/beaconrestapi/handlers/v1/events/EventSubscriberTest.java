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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.EventSubscriptionManager.EventSource;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.provider.JsonProvider;

public class EventSubscriberTest {
  private static final int MAX_PENDING_EVENTS = 10;
  private final AsyncContext asyncContext = mock(AsyncContext.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse res = mock(HttpServletResponse.class);
  private final Runnable onCloseCallback = mock(Runnable.class);
  private final ServletResponse servletResponse = mock(ServletResponse.class);
  private final TestServletOutputStream outputStream = new TestServletOutputStream();
  private final JsonProvider jsonProvider = new JsonProvider();

  private final Context context = new Context(req, res, Collections.emptyMap());
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final List<EventType> allEventTypes =
      Arrays.stream(EventType.values()).collect(Collectors.toList());

  private SseClient sseClient;

  @BeforeEach
  public void setup() throws IOException {
    when(req.getAsyncContext()).thenReturn(asyncContext);
    when(asyncContext.getResponse()).thenReturn(servletResponse);
    when(servletResponse.getOutputStream()).thenReturn(outputStream);
    sseClient = new SseClient(context);
  }

  @Test
  void shouldGetSseClient() {
    EventSubscriber eventSubscriber =
        new EventSubscriber(
            List.of("head"), sseClient, onCloseCallback, asyncRunner, MAX_PENDING_EVENTS);
    assertThat(eventSubscriber.getSseClient()).isEqualTo(sseClient);
  }

  @Test
  void shouldDisconnectAfterTooManyRequestsAreLogged() throws Exception {
    EventSubscriber eventSubscriber =
        new EventSubscriber(
            List.of("head"), sseClient, onCloseCallback, asyncRunner, MAX_PENDING_EVENTS);

    for (int i = 0; i < MAX_PENDING_EVENTS + 1; i++) {
      verify(onCloseCallback, never()).run();
      eventSubscriber.onEvent(EventType.head, event("test"));
    }
    verify(onCloseCallback).run();
  }

  @Test
  void shouldStopSendingEventsWhenQueueOverflows() throws Exception {
    EventSubscriber eventSubscriber =
        new EventSubscriber(
            List.of("head"), sseClient, onCloseCallback, asyncRunner, MAX_PENDING_EVENTS);

    for (int i = 0; i < MAX_PENDING_EVENTS + 1; i++) {
      verify(onCloseCallback, never()).run();
      eventSubscriber.onEvent(EventType.head, event("test"));
    }
    verify(onCloseCallback).run();
    verify(asyncContext).complete();
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  @Test
  void shouldSubscribeToMultipleEventsSuccessfully() throws IOException {
    EventSubscriber eventSubscriber =
        new EventSubscriber(
            allEventTypes.stream().map(EventType::name).collect(Collectors.toList()),
            sseClient,
            onCloseCallback,
            asyncRunner,
            MAX_PENDING_EVENTS);
    for (EventType eventType : allEventTypes) {
      eventSubscriber.onEvent(eventType, event("test"));
    }
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.countEvents()).isEqualTo(allEventTypes.size());
  }

  @Test
  void shouldNotDisconnectIfQueueProcessingCatchesUp() throws IOException {
    EventSubscriber eventSubscriber =
        new EventSubscriber(
            List.of("head"), sseClient, onCloseCallback, asyncRunner, MAX_PENDING_EVENTS);

    for (int i = 0; i < MAX_PENDING_EVENTS; i++) {
      eventSubscriber.onEvent(EventType.head, event("test"));
    }
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.countEvents()).isEqualTo(10);

    for (int i = 0; i < MAX_PENDING_EVENTS; i++) {
      eventSubscriber.onEvent(EventType.head, event("test"));
    }

    verify(onCloseCallback, never()).run();
  }

  @ParameterizedTest
  @EnumSource(EventType.class)
  void shouldNotSendEventsIfNotSubscribed(final EventType eventType) throws Exception {
    EventSubscriber subscriber =
        new EventSubscriber(
            List.of(eventType.name()), sseClient, onCloseCallback, asyncRunner, MAX_PENDING_EVENTS);
    for (EventType val : allEventTypes) {
      if (val.compareTo(eventType) != 0) {
        subscriber.onEvent(val, event("test"));
      }
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(EventType.class)
  void shouldSendEventsIfSubscribed(final EventType eventType) throws IOException {
    EventSubscriber subscriber =
        new EventSubscriber(
            List.of(eventType.name()), sseClient, onCloseCallback, asyncRunner, MAX_PENDING_EVENTS);

    subscriber.onEvent(eventType, event("test"));

    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.countEvents()).isEqualTo(1);
  }

  private EventSource event(final String message) {
    return new EventSource(jsonProvider, message);
  }
}
