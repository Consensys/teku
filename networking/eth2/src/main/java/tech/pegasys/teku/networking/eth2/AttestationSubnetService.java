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

package tech.pegasys.teku.networking.eth2;

import java.util.Collections;
import java.util.function.Consumer;
import tech.pegasys.teku.util.events.Subscribers;

/**
 * Service tracks long term attestation subnet subscriptions and notifies subscribers on their
 * changes
 */
public class AttestationSubnetService {
  private final Subscribers<Consumer<Iterable<Integer>>> subscribers = Subscribers.create(true);
  private volatile Iterable<Integer> currentSubscriptions = Collections.emptyList();

  public void updateSubscriptions(final Iterable<Integer> subnetIndices) {
    subscribers.deliver(Consumer::accept, subnetIndices);
    currentSubscriptions = subnetIndices;
  }

  public long subscribeToUpdates(Consumer<Iterable<Integer>> observer) {
    observer.accept(currentSubscriptions);
    return subscribers.subscribe(observer);
  }

  public void unsubscribe(long subscriptionId) {
    subscribers.unsubscribe(subscriptionId);
  }
}
