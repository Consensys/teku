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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;

class EventChannelsTest {
  private final EventChannels channels =
      new EventChannels(channel -> EventChannel.create(channel, new NoOpMetricsSystem()));

  @Test
  public void shouldBeAbleToGetPublisherBeforeSubscriber() {
    final TestChannel publisher = channels.getPublisher(TestChannel.class);

    final TestChannel subscriber = mock(TestChannel.class);
    channels.subscribe(TestChannel.class, subscriber);

    publisher.run();

    verify(subscriber).run();
  }

  @Test
  public void shouldBeAbleToSubscribeBeforePublisherIsCreated() {
    final TestChannel subscriber = mock(TestChannel.class);
    channels.subscribe(TestChannel.class, subscriber);

    final TestChannel publisher = channels.getPublisher(TestChannel.class);

    publisher.run();

    verify(subscriber).run();
  }

  @Test
  public void shouldBeAbleToAddMultipleSubscribers() {
    final TestChannel subscriber1 = mock(TestChannel.class);
    final TestChannel subscriber2 = mock(TestChannel.class);

    channels.subscribe(TestChannel.class, subscriber1);
    final TestChannel publisher = channels.getPublisher(TestChannel.class);
    channels.subscribe(TestChannel.class, subscriber2);

    publisher.run();

    verify(subscriber1).run();
    verify(subscriber2).run();
  }

  @Test
  public void shouldReturnSamePublisherForSameChannel() {
    final TestChannel publisher1 = channels.getPublisher(TestChannel.class);
    final TestChannel publisher2 = channels.getPublisher(TestChannel.class);

    assertThat(publisher1).isSameAs(publisher2);
  }

  @Test
  public void shouldKeepDifferentChannelsSeparate() {
    final TestChannel testChannelSubscriber = mock(TestChannel.class);
    final SimpleConsumer consumerSubscriber = mock(SimpleConsumer.class);
    channels.subscribe(TestChannel.class, testChannelSubscriber);
    channels.subscribe(SimpleConsumer.class, consumerSubscriber);

    final TestChannel testChannelPublisher = channels.getPublisher(TestChannel.class);
    final SimpleConsumer consumerPublisher = channels.getPublisher(SimpleConsumer.class);

    testChannelPublisher.run();
    verify(testChannelSubscriber).run();
    verifyNoInteractions(consumerSubscriber);

    consumerPublisher.accept(1);
    verify(consumerSubscriber).accept(1);
    verifyNoMoreInteractions(testChannelSubscriber);
  }

  @Channel
  private interface SimpleConsumer {
    void accept(int value);
  }
}
