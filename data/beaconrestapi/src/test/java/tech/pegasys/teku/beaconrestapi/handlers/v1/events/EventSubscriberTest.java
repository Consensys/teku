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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.EventSubscriptionManager.EventSource;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

public class EventSubscriberTest {
  private static final int MAX_PENDING_EVENTS = 10;
  private final AsyncContext asyncContext = mock(AsyncContext.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse res = mock(HttpServletResponse.class);
  private final Runnable onCloseCallback = mock(Runnable.class);
  private final TestServletOutputStream outputStream = new TestServletOutputStream();
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1000);

  private final Context context = new StubContext(req, res);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final List<EventType> allEventTypes =
      Arrays.stream(EventType.values()).collect(Collectors.toList());

  private SseClient sseClient;

  @BeforeEach
  public void setup() throws IOException {
    when(req.getAsyncContext()).thenReturn(asyncContext);
    when(asyncContext.getResponse()).thenReturn(res);
    when(res.getOutputStream()).thenReturn(outputStream);
    sseClient = new SseClient(context);
  }

  @Test
  void shouldGetSseClient() {
    EventSubscriber eventSubscriber = createSubscriber("head");
    assertThat(eventSubscriber.getSseClient()).isEqualTo(sseClient);
  }

  @Test
  void shouldDisconnectWhenQueueSizeTooBigForTooLong() throws Exception {
    EventSubscriber eventSubscriber = createSubscriber("head");

    for (int i = 0; i < MAX_PENDING_EVENTS + 1; i++) {
      verify(onCloseCallback, never()).run();
      eventSubscriber.onEvent(EventType.head, event("test"));
    }
    verifyNoInteractions(onCloseCallback);
    timeProvider.advanceTimeByMillis(EventSubscriber.EXCESSIVE_QUEUING_TOLERANCE_MS);
    eventSubscriber.onEvent(EventType.head, event("foo"));
    verify(onCloseCallback).run();
  }

  @Test
  void shouldNotDisconnectWhenMaxQueueSizeBriefly() throws Exception {
    EventSubscriber eventSubscriber = createSubscriber("head");

    // Max size exceeded
    for (int i = 0; i < MAX_PENDING_EVENTS + 1; i++) {
      verify(onCloseCallback, never()).run();
      eventSubscriber.onEvent(EventType.head, event("test"));
    }
    verifyNoInteractions(onCloseCallback);

    // But we drain the queue just before the time tolerance is reached
    timeProvider.advanceTimeByMillis(EventSubscriber.EXCESSIVE_QUEUING_TOLERANCE_MS - 1);
    asyncRunner.executeQueuedActions();
    verifyNoInteractions(onCloseCallback);

    // And so we shouldn't get disconnected
    timeProvider.advanceTimeByMillis(1);
    eventSubscriber.onEvent(EventType.head, event("head"));
    verifyNoInteractions(onCloseCallback);
  }

  @Test
  void shouldStopSendingEventsWhenQueueOverflows() throws Exception {
    EventSubscriber eventSubscriber = createSubscriber("head");

    for (int i = 0; i < MAX_PENDING_EVENTS + 1; i++) {
      verify(onCloseCallback, never()).run();
      eventSubscriber.onEvent(EventType.head, event("test"));
    }
    timeProvider.advanceTimeByMillis(EventSubscriber.EXCESSIVE_QUEUING_TOLERANCE_MS);
    eventSubscriber.onEvent(EventType.head, event("test"));

    verify(onCloseCallback).run();
    verify(asyncContext).complete();
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  @Test
  void shouldOnlyDisconnectOnce() throws Exception {
    EventSubscriber eventSubscriber = createSubscriber("head");

    for (int i = 0; i < MAX_PENDING_EVENTS + 1; i++) {
      verify(onCloseCallback, never()).run();
      eventSubscriber.onEvent(EventType.head, event("test"));
    }
    timeProvider.advanceTimeByMillis(EventSubscriber.EXCESSIVE_QUEUING_TOLERANCE_MS);

    // Multiple events are delivered before the close callback can actually run and unsubscribe
    // but we should only disconnect once
    eventSubscriber.onEvent(EventType.head, event("test"));
    eventSubscriber.onEvent(EventType.head, event("test"));
    eventSubscriber.onEvent(EventType.head, event("test"));

    verify(onCloseCallback, atMostOnce()).run();
    verify(asyncContext, atMostOnce()).complete();
  }

  @Test
  void shouldSubscribeToMultipleEventsSuccessfully() throws IOException {
    EventSubscriber eventSubscriber =
        createSubscriber(allEventTypes.stream().map(EventType::name).toArray(String[]::new));
    for (EventType eventType : allEventTypes) {
      eventSubscriber.onEvent(eventType, event("test"));
    }
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(2);
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.countEvents()).isEqualTo(allEventTypes.size());
    assertThat(outputStream.countComments()).isEqualTo(1);
  }

  @Test
  void shouldNotDisconnectIfQueueProcessingCatchesUp() throws IOException {
    EventSubscriber eventSubscriber = createSubscriber("head");

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
    EventSubscriber subscriber = createSubscriber(eventType.name());
    for (EventType val : allEventTypes) {
      if (val.compareTo(eventType) != 0) {
        subscriber.onEvent(val, event("test"));
      }
    }

    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.countEvents()).isEqualTo(0);
    assertThat(outputStream.countComments()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(EventType.class)
  void shouldSendEventsIfSubscribed(final EventType eventType) throws IOException {
    EventSubscriber subscriber = createSubscriber(eventType.name());

    subscriber.onEvent(eventType, event("test"));

    assertThat(asyncRunner.countDelayedActions()).isEqualTo(2);
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.countEvents()).isEqualTo(1);
    assertThat(outputStream.countComments()).isEqualTo(1);
  }

  @Test
  void shouldSendKeepAlive() {
    createSubscriber(EventType.voluntary_exit.name());

    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    assertThat(outputStream.countEvents()).isEqualTo(0);
    assertThat(outputStream.countComments()).isEqualTo(1);

    // Keep alive schedules another to be run
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
  }

  @Test
  void shouldStopSendingKeepAliveWhenSseClientCloses() {
    createSubscriber(EventType.voluntary_exit.name());

    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    sseClient.close();
    asyncRunner.executeQueuedActions();

    // Keep alive should not schedule another to be run
    assertThat(asyncRunner.countDelayedActions()).isZero();
  }

  private EventSource<String> event(final String message) {
    return new EventSource<>(new TestEvent(message));
  }

  private EventSubscriber createSubscriber(final String... eventTypes) {
    return new EventSubscriber(
        List.of(eventTypes),
        sseClient,
        onCloseCallback,
        asyncRunner,
        timeProvider,
        MAX_PENDING_EVENTS);
  }
}
