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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public class EventChannels {

  private final ConcurrentMap<Class<?>, EventChannel<?>> channels = new ConcurrentHashMap<>();
  private final Function<Class<?>, EventChannel<?>> eventChannelFactory;

  public EventChannels(
      final ChannelExceptionHandler exceptionHandler, final MetricsSystem metricsSystem) {
    this(
        channelInterface ->
            EventChannel.createAsync(channelInterface, exceptionHandler, metricsSystem));
  }

  public EventChannels(final MetricsSystem metricsSystem) {
    this(channelInterface -> EventChannel.createAsync(channelInterface, metricsSystem));
  }

  EventChannels(final Function<Class<?>, EventChannel<?>> eventChannelFactory) {
    this.eventChannelFactory = eventChannelFactory;
  }

  public <T> T getPublisher(final Class<T> channelInterface) {
    return getChannel(channelInterface).getPublisher();
  }

  public <T> EventChannels subscribe(final Class<T> channelInterface, final T subscriber) {
    return subscribeMultithreaded(channelInterface, subscriber, 1);
  }

  /**
   * Adds a subscriber to this channel where events are handled by multiple threads concurrently.
   *
   * <p>Note that only async event channels can use multiple threads. Synchronous channels will
   * always use the publisher thread to process events.
   *
   * <p>Events are still placed into an ordered queue and started in order, but as multiple threads
   * pull from the queue, the execution order can no longer be guaranteed.
   *
   * @param channelInterface the channel to subscribe to
   * @param subscriber the subscriber to notify of events
   * @param requestedParallelism the number of threads to use to process events
   */
  public <T> EventChannels subscribeMultithreaded(
      final Class<T> channelInterface, final T subscriber, final int requestedParallelism) {
    getChannel(channelInterface).subscribeMultithreaded(subscriber, requestedParallelism);
    return this;
  }

  @SuppressWarnings("unchecked")
  private <T> EventChannel<T> getChannel(final Class<T> channelInterface) {
    return (EventChannel<T>) channels.computeIfAbsent(channelInterface, eventChannelFactory);
  }

  public void stop() {
    channels.values().forEach(EventChannel::stop);
  }
}
