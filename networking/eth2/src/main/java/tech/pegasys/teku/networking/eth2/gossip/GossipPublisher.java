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

package tech.pegasys.teku.networking.eth2.gossip;

import tech.pegasys.teku.infrastructure.subscribers.Subscribers;

public class GossipPublisher<T> {
  private final Subscribers<PublishedGossipSubscriber<T>> subscribers = Subscribers.create(true);

  public void publish(final T gossipMessage) {
    subscribers.deliver(PublishedGossipSubscriber::onGossipPublished, gossipMessage);
  }

  public long subscribe(final PublishedGossipSubscriber<T> subscriber) {
    return subscribers.subscribe(subscriber);
  }

  public boolean unsubscribe(final long subscriberId) {
    return subscribers.unsubscribe(subscriberId);
  }

  public interface PublishedGossipSubscriber<T> {
    void onGossipPublished(T message);
  }
}
