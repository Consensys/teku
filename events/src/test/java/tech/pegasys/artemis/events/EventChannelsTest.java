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

import org.junit.jupiter.api.Test;

class EventChannelsTest {
  private final EventChannels channels = new EventChannels(EventChannel::create);

  @Test
  public void shouldBeAbleToGetPublisherBeforeSubscriber() {
    final Runnable publisher = channels.getPublisher(Runnable.class);

    final Runnable subscriber = mock(Runnable.class);
    channels.subscribe(Runnable.class, subscriber);

    publisher.run();

    verify(subscriber).run();
  }

  @Test
  public void shouldBeAbleToSubscribeBeforePublisherIsCreated() {
    final Runnable subscriber = mock(Runnable.class);
    channels.subscribe(Runnable.class, subscriber);

    final Runnable publisher = channels.getPublisher(Runnable.class);

    publisher.run();

    verify(subscriber).run();
  }

  @Test
  public void shouldBeAbleToAddMultipleSubscribers() {
    final Runnable subscriber1 = mock(Runnable.class);
    final Runnable subscriber2 = mock(Runnable.class);

    channels.subscribe(Runnable.class, subscriber1);
    final Runnable publisher = channels.getPublisher(Runnable.class);
    channels.subscribe(Runnable.class, subscriber2);

    publisher.run();

    verify(subscriber1).run();
    verify(subscriber2).run();
  }

  @Test
  public void shouldReturnSamePublisherForSameChannel() {
    final Runnable publisher1 = channels.getPublisher(Runnable.class);
    final Runnable publisher2 = channels.getPublisher(Runnable.class);

    assertThat(publisher1).isSameAs(publisher2);
  }

  @Test
  public void shouldKeepDifferentChannelsSeparate() {
    final Runnable runnableSubscriber = mock(Runnable.class);
    final SimpleConsumer consumerSubscriber = mock(SimpleConsumer.class);
    channels.subscribe(Runnable.class, runnableSubscriber);
    channels.subscribe(SimpleConsumer.class, consumerSubscriber);

    final Runnable runnablePublisher = channels.getPublisher(Runnable.class);
    final SimpleConsumer consumerPublisher = channels.getPublisher(SimpleConsumer.class);

    runnablePublisher.run();
    verify(runnableSubscriber).run();
    verifyNoInteractions(consumerSubscriber);

    consumerPublisher.accept(1);
    verify(consumerSubscriber).accept(1);
    verifyNoMoreInteractions(runnableSubscriber);
  }

  private interface SimpleConsumer {
    void accept(int value);
  }
}
