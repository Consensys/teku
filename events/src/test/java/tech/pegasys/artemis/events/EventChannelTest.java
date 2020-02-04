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

package tech.pegasys.artemis.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tech.pegasys.artemis.events.AsyncEventDeliverer.QueueReader;

class EventChannelTest {
  private final ChannelExceptionHandler exceptionHandler = mock(ChannelExceptionHandler.class);

  @Test
  public void shouldRejectClassesThatAreNotInterfaces() {
    assertThatThrownBy(() -> EventChannel.create(String.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectInterfacesWithNonVoidMethods() {
    assertThatThrownBy(() -> EventChannel.create(Supplier.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectInterfacesWithDeclaredExceptions() {
    assertThatThrownBy(() -> EventChannel.create(WithException.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldDeliverCallsToSubscribers() {
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class);
    final Runnable subscriber = mock(Runnable.class);
    channel.subscribe(subscriber);

    channel.getPublisher().run();

    verify(subscriber).run();
  }

  @Test
  public void shouldDeliverCallsToMultipleSubscribers() {
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class);
    final Runnable subscriber1 = mock(Runnable.class);
    final Runnable subscriber2 = mock(Runnable.class);
    channel.subscribe(subscriber1);
    channel.subscribe(subscriber2);

    channel.getPublisher().run();

    verify(subscriber1).run();
    verify(subscriber2).run();
  }

  @Test
  public void shouldDeliverCallsToCorrectMethod() {
    final EventChannel<MultipleMethods> channel = EventChannel.create(MultipleMethods.class);
    final MultipleMethods subscriber = mock(MultipleMethods.class);
    channel.subscribe(subscriber);

    channel.getPublisher().method2();
    verify(subscriber).method2();
    verifyNoMoreInteractions(subscriber);

    channel.getPublisher().method1();
    verify(subscriber).method1();
    verifyNoMoreInteractions(subscriber);
  }

  @Test
  public void shouldReportExceptionsToExceptionHandler() throws Exception {
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class, exceptionHandler);
    final Runnable subscriber = mock(Runnable.class);
    final RuntimeException exception = new RuntimeException("Nope");
    doThrow(exception).when(subscriber).run();

    channel.subscribe(subscriber);
    channel.getPublisher().run();

    verify(exceptionHandler)
        .handleException(exception, subscriber, Runnable.class.getMethod("run"), null);
  }

  @Test
  public void shouldNotProxyToString() {
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class);
    final Runnable publisher = channel.getPublisher();
    final String toString = publisher.toString();
    assertThat(toString).contains(DirectEventDeliverer.class.getName());
  }

  @Test
  public void publisherShouldNotBeEqualToAnything() {
    // Mostly we just want it to not throw exceptions when equals is called.
    final EventChannel<Runnable> channel = EventChannel.create(Runnable.class);
    final Runnable publisher = channel.getPublisher();
    assertThat(publisher).isNotEqualTo("Foo");
    assertThat(publisher).isNotEqualTo(null);
    assertThat(publisher).isNotEqualTo(EventChannel.create(Runnable.class).getPublisher());
    assertThat(publisher).isNotEqualTo(publisher);
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void shouldDeliverEventsAsync() throws Exception {
    final ExecutorService executor = mock(ExecutorService.class);
    final EventChannel<EventWithArgument> channel =
        EventChannel.createAsync(EventWithArgument.class, executor);
    final EventWithArgument subscriber = mock(EventWithArgument.class);
    channel.subscribe(subscriber);

    // Publish a sequence of events
    channel.getPublisher().method1("Event1");
    channel.getPublisher().method2("Event2");
    channel.getPublisher().method1("Event3");

    verifyNoInteractions(subscriber);

    // Now actually run the consuming thread
    final ArgumentCaptor<QueueReader> consumerCaptor = ArgumentCaptor.forClass(QueueReader.class);
    verify(executor).execute(consumerCaptor.capture());
    consumerCaptor.getValue().deliverNextEvent();
    consumerCaptor.getValue().deliverNextEvent();
    consumerCaptor.getValue().deliverNextEvent();

    // Verify that the events were delivered in order
    final InOrder inOrder = inOrder(subscriber);
    inOrder.verify(subscriber).method1("Event1");
    inOrder.verify(subscriber).method2("Event2");
    inOrder.verify(subscriber).method1("Event3");
    inOrder.verifyNoMoreInteractions();
  }

  private interface WithException {
    void someMethod() throws Exception;
  }

  private interface MultipleMethods {
    void method1();

    void method2();

    void method3();
  }

  private interface EventWithArgument {
    void method1(String value);

    void method2(String value);
  }
}
