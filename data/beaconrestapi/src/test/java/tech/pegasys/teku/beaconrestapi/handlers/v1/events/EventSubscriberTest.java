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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.events.EventSubscriber.MAX_PENDING_EVENTS;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;

public class EventSubscriberTest {
  private final AsyncContext asyncContext = mock(AsyncContext.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse res = mock(HttpServletResponse.class);
  private final Runnable onCloseCallback = mock(Runnable.class);
  private final ServletResponse servletResponse = mock(ServletResponse.class);
  private final ServletOutputStream outputStream = mock(ServletOutputStream.class);

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
        new EventSubscriber(List.of("head"), sseClient, onCloseCallback, asyncRunner);
    assertThat(eventSubscriber.getSseClient()).isEqualTo(sseClient);
  }

  @Test
  void shouldDisconnectAfterTooManyRequestsAreLogged() {
    EventSubscriber eventSubscriber =
        new EventSubscriber(List.of("head"), sseClient, onCloseCallback, asyncRunner);

    for (int i = 0; i < MAX_PENDING_EVENTS + 1; i++) {
      verify(onCloseCallback, never()).run();
      eventSubscriber.onEvent(EventType.head, "test");
    }
    verify(onCloseCallback).run();
  }

  @Test
  void shouldSubscribeToMultipleEventsSuccessfully() throws IOException {
    EventSubscriber eventSubscriber =
        new EventSubscriber(
            allEventTypes.stream().map(EventType::name).collect(Collectors.toList()),
            sseClient,
            onCloseCallback,
            asyncRunner);
    allEventTypes.forEach(eventType -> eventSubscriber.onEvent(eventType, "test"));
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    verify(outputStream, times(allEventTypes.size())).print(anyString());
  }

  @Test
  void shouldNotDisconnectIfQueueProcessingCatchesUp() throws IOException {
    EventSubscriber eventSubscriber =
        new EventSubscriber(List.of("head"), sseClient, onCloseCallback, asyncRunner);

    for (int i = 0; i < MAX_PENDING_EVENTS; i++) {
      eventSubscriber.onEvent(EventType.head, "test");
    }
    asyncRunner.executeQueuedActions();
    verify(outputStream, times(10)).print(anyString());

    for (int i = 0; i < MAX_PENDING_EVENTS; i++) {
      eventSubscriber.onEvent(EventType.head, "test");
    }

    verify(onCloseCallback, never()).run();
  }

  @ParameterizedTest
  @EnumSource(EventType.class)
  void shouldNotSendEventsIfNotSubscribed(final EventType eventType) {
    EventSubscriber subscriber =
        new EventSubscriber(List.of(eventType.name()), sseClient, onCloseCallback, asyncRunner);
    allEventTypes.stream()
        .filter(val -> val.compareTo(eventType) != 0)
        .forEach(value -> subscriber.onEvent(value, "test"));
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(EventType.class)
  void shouldSendEventsIfSubscribed(final EventType eventType) throws IOException {
    EventSubscriber subscriber =
        new EventSubscriber(List.of(eventType.name()), sseClient, onCloseCallback, asyncRunner);

    subscriber.onEvent(eventType, "test");

    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeQueuedActions();
    verify(outputStream).print(anyString());
  }
}
