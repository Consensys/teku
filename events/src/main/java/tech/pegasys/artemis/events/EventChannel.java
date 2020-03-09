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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.joining;
import static tech.pegasys.artemis.events.LoggingChannelExceptionHandler.LOGGING_EXCEPTION_HANDLER;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.artemis.util.async.SafeFuture;

public class EventChannel<T> {

  private final T publisher;
  private final EventDeliverer<T> invoker;
  private final boolean allowMultipleSubscribers;
  private final AtomicBoolean hasSubscriber = new AtomicBoolean(false);

  private EventChannel(
      final T publisher, final EventDeliverer<T> invoker, final boolean allowMultipleSubscribers) {
    this.publisher = publisher;
    this.invoker = invoker;
    this.allowMultipleSubscribers = allowMultipleSubscribers;
  }

  public static <T> EventChannel<T> create(
      final Class<T> channelInterface, final MetricsSystem metricsSystem) {
    return create(channelInterface, LOGGING_EXCEPTION_HANDLER, metricsSystem);
  }

  public static <T> EventChannel<T> create(
      final Class<T> channelInterface,
      final ChannelExceptionHandler exceptionHandler,
      final MetricsSystem metricsSystem) {
    return create(channelInterface, new DirectEventDeliverer<>(exceptionHandler, metricsSystem));
  }

  public static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface, final MetricsSystem metricsSystem) {
    return createAsync(channelInterface, LOGGING_EXCEPTION_HANDLER, metricsSystem);
  }

  public static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface,
      final ChannelExceptionHandler exceptionHandler,
      final MetricsSystem metricsSystem) {
    return createAsync(
        channelInterface,
        Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat(channelInterface.getSimpleName() + "-%d")
                .build()),
        exceptionHandler,
        metricsSystem);
  }

  static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface,
      final ExecutorService executor,
      final MetricsSystem metricsSystem) {
    return createAsync(channelInterface, executor, LOGGING_EXCEPTION_HANDLER, metricsSystem);
  }

  static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface,
      final ExecutorService executor,
      final ChannelExceptionHandler exceptionHandler,
      final MetricsSystem metricsSystem) {
    return create(
        channelInterface, new AsyncEventDeliverer<>(executor, exceptionHandler, metricsSystem));
  }

  private static <T> EventChannel<T> create(
      final Class<T> channelInterface, final EventDeliverer<T> eventDeliverer) {
    checkArgument(channelInterface.isInterface(), "Must provide an interface for the channel");
    final String illegalMethods =
        Stream.of(channelInterface.getMethods())
            .filter(method -> !isReturnTypeAllowed(method) || method.getExceptionTypes().length > 0)
            .map(Method::getName)
            .collect(joining(", "));
    checkArgument(
        illegalMethods.isEmpty(),
        "All methods must have a return type that is void or compatible with SafeFuture and no exceptions but "
            + illegalMethods
            + " did not");
    final boolean hasReturnValues =
        Stream.of(channelInterface.getMethods())
            .anyMatch(method -> hasAllowedAsyncReturnValue(method.getReturnType()));
    @SuppressWarnings("unchecked")
    final T publisher =
        (T)
            Proxy.newProxyInstance(
                channelInterface.getClassLoader(),
                new Class<?>[] {channelInterface},
                eventDeliverer);

    return new EventChannel<>(publisher, eventDeliverer, !hasReturnValues);
  }

  private static boolean isReturnTypeAllowed(final Method method) {
    final Class<?> returnType = method.getReturnType();
    // Allow void
    return returnType.equals(Void.TYPE)
        // Allow Future, CompletableStage or CompletableFuture
        || hasAllowedAsyncReturnValue(returnType);
  }

  private static boolean hasAllowedAsyncReturnValue(final Class<?> returnType) {
    return Future.class.isAssignableFrom(returnType)
        && returnType.isAssignableFrom(SafeFuture.class);
  }

  public T getPublisher() {
    return publisher;
  }

  public void subscribe(final T listener) {
    subscribeMultithreaded(listener, 1);
  }

  public void subscribeMultithreaded(final T listener, final int numberOfThreads) {
    checkArgument(numberOfThreads > 0, "Number of threads must be at least 1");
    if (!hasSubscriber.compareAndSet(false, true) && !allowMultipleSubscribers) {
      throw new IllegalStateException("Only one subscriber is supported by this event channel");
    }
    invoker.subscribe(listener, numberOfThreads);
  }

  public void stop() {
    invoker.stop();
  }
}
