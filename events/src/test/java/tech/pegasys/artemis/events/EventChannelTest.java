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
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tech.pegasys.artemis.events.AsyncEventDeliverer.QueueReader;
import tech.pegasys.artemis.util.async.SafeFuture;

class EventChannelTest {
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final ChannelExceptionHandler exceptionHandler = mock(ChannelExceptionHandler.class);

  @Test
  public void shouldRejectClassesThatAreNotInterfaces() {
    assertThatThrownBy(() -> EventChannel.create(String.class, metricsSystem))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectInterfacesWithNonVoidMethods() {
    assertThatThrownBy(() -> EventChannel.create(WithReturnType.class, metricsSystem))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRejectInterfacesWithoutChannelAnnotation() {
    assertThatThrownBy(() -> EventChannel.create(Runnable.class, metricsSystem))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldRejectInterfacesWithDeclaredExceptions() {
    assertThatThrownBy(() -> EventChannel.create(WithException.class, metricsSystem))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldDeliverCallsToSubscribers() {
    final EventChannel<TestChannel> channel = EventChannel.create(TestChannel.class, metricsSystem);
    final TestChannel subscriber = mock(TestChannel.class);
    channel.subscribe(subscriber);

    channel.getPublisher().run();

    verify(subscriber).run();
  }

  @Test
  public void shouldDeliverCallsToMultipleSubscribers() {
    final EventChannel<TestChannel> channel = EventChannel.create(TestChannel.class, metricsSystem);
    final TestChannel subscriber1 = mock(TestChannel.class);
    final TestChannel subscriber2 = mock(TestChannel.class);
    channel.subscribe(subscriber1);
    channel.subscribe(subscriber2);

    channel.getPublisher().run();

    verify(subscriber1).run();
    verify(subscriber2).run();
  }

  @Test
  public void shouldDeliverCallsToCorrectMethod() {
    final EventChannel<MultipleMethods> channel =
        EventChannel.create(MultipleMethods.class, metricsSystem);
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
  public void shouldReturnFutureResults() {
    final EventChannel<WithFuture> channel = EventChannel.create(WithFuture.class, metricsSystem);
    final SafeFuture<String> expected = new SafeFuture<>();
    final WithFuture subscriber = () -> expected;
    channel.subscribe(subscriber);

    final SafeFuture<String> result = channel.getPublisher().getFutureString();
    assertThat(result).isNotDone();

    expected.complete("Yay");
    assertThat(result).isCompletedWithValue("Yay");
  }

  @Test
  public void shouldDisallowMultipleSubscribersWhenMethodsHaveReturnValues() {
    final EventChannel<WithFuture> channel = EventChannel.create(WithFuture.class, metricsSystem);
    channel.subscribe(SafeFuture::new);
    assertThatThrownBy(() -> channel.subscribe(SafeFuture::new))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldReportExceptionsToExceptionHandler() throws Exception {
    final EventChannel<TestChannel> channel =
        EventChannel.create(TestChannel.class, exceptionHandler, metricsSystem);
    final TestChannel subscriber = mock(TestChannel.class);
    final RuntimeException exception = new RuntimeException("Nope");
    doThrow(exception).when(subscriber).run();

    channel.subscribe(subscriber);
    channel.getPublisher().run();

    verify(exceptionHandler)
        .handleException(exception, subscriber, TestChannel.class.getMethod("run"), null);
  }

  @Test
  public void shouldNotProxyToString() {
    final EventChannel<TestChannel> channel = EventChannel.create(TestChannel.class, metricsSystem);
    final TestChannel publisher = channel.getPublisher();
    final String toString = publisher.toString();
    assertThat(toString).contains(DirectEventDeliverer.class.getName());
  }

  @Test
  public void publisherShouldNotBeEqualToAnything() {
    // Mostly we just want it to not throw exceptions when equals is called.
    final EventChannel<TestChannel> channel = EventChannel.create(TestChannel.class, metricsSystem);
    final TestChannel publisher = channel.getPublisher();
    assertThat(publisher).isNotEqualTo("Foo");
    assertThat(publisher).isNotEqualTo(null);
    assertThat(publisher)
        .isNotEqualTo(EventChannel.create(TestChannel.class, metricsSystem).getPublisher());
    assertThat(publisher).isNotEqualTo(publisher);
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void shouldDeliverEventsAsync() throws Exception {
    final ExecutorService executor = mock(ExecutorService.class);
    final EventChannel<EventWithArgument> channel =
        EventChannel.createAsync(EventWithArgument.class, executor, metricsSystem);
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

  @Test
  @SuppressWarnings("rawtypes")
  public void shouldReturnFutureResultsAsync() throws Exception {
    final ExecutorService executor = mock(ExecutorService.class);
    final EventChannel<WithFuture> channel =
        EventChannel.createAsync(WithFuture.class, executor, metricsSystem);
    final SafeFuture<String> expected = SafeFuture.completedFuture("Yay");
    final WithFuture subscriber = () -> expected;
    channel.subscribe(subscriber);

    final SafeFuture<String> result = channel.getPublisher().getFutureString();
    assertThat(result).isNotDone(); // Hasn't been delivered to the subscriber yet

    // Now actually run the consuming thread
    final ArgumentCaptor<QueueReader> consumerCaptor = ArgumentCaptor.forClass(QueueReader.class);
    verify(executor).execute(consumerCaptor.capture());
    consumerCaptor.getValue().deliverNextEvent();

    assertThat(result).isCompletedWithValue("Yay");
  }

  @Channel
  private interface WithException {
    void someMethod() throws Exception;
  }

  @Channel
  private interface MultipleMethods {
    void method1();

    void method2();

    void method3();
  }

  @Channel
  private interface EventWithArgument {
    void method1(String value);

    void method2(String value);
  }

  @Channel
  private interface WithFuture {
    SafeFuture<String> getFutureString();
  }

  @Channel
  private interface WithReturnType {
    boolean get();
  }
}
