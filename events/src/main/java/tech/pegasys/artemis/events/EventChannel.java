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
import java.util.stream.Stream;

public class EventChannel<T> {

  private final T publisher;
  private final EventDeliverer<T> invoker;

  public EventChannel(final T publisher, final EventDeliverer<T> invoker) {
    this.publisher = publisher;
    this.invoker = invoker;
  }

  public static <T> EventChannel<T> create(final Class<T> channelInterface) {
    return create(channelInterface, LOGGING_EXCEPTION_HANDLER);
  }

  public static <T> EventChannel<T> create(
      final Class<T> channelInterface, final ChannelExceptionHandler exceptionHandler) {
    return create(channelInterface, new DirectEventDeliverer<>(exceptionHandler));
  }

  public static <T> EventChannel<T> createAsync(final Class<T> channelInterface) {
    return createAsync(channelInterface, LOGGING_EXCEPTION_HANDLER);
  }

  public static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface, final ChannelExceptionHandler exceptionHandler) {
    return createAsync(
        channelInterface,
        Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat(channelInterface.getSimpleName() + "-%d")
                .build()),
        exceptionHandler);
  }

  static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface, final ExecutorService executor) {
    return createAsync(channelInterface, executor, LOGGING_EXCEPTION_HANDLER);
  }

  static <T> EventChannel<T> createAsync(
      final Class<T> channelInterface,
      final ExecutorService executor,
      final ChannelExceptionHandler exceptionHandler) {
    return create(channelInterface, new AsyncEventDeliverer<>(executor, exceptionHandler));
  }

  private static <T> EventChannel<T> create(
      final Class<T> channelInterface, final EventDeliverer<T> eventDeliverer) {
    checkArgument(channelInterface.isInterface(), "Must provide an interface for the channel");
    final String nonVoidMethods =
        Stream.of(channelInterface.getMethods())
            .filter(
                method ->
                    !method.getReturnType().equals(Void.TYPE)
                        || method.getExceptionTypes().length > 0)
            .map(Method::getName)
            .collect(joining(", "));
    checkArgument(
        nonVoidMethods.isEmpty(),
        "All methods must have a void return type and no exceptions but "
            + nonVoidMethods
            + " did not");
    @SuppressWarnings("unchecked")
    final T publisher =
        (T)
            Proxy.newProxyInstance(
                channelInterface.getClassLoader(),
                new Class<?>[] {channelInterface},
                eventDeliverer);

    return new EventChannel<>(publisher, eventDeliverer);
  }

  public T getPublisher() {
    return publisher;
  }

  public void subscribe(T listener) {
    invoker.subscribe(listener);
  }

  public void stop() {
    invoker.stop();
  }
}
