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

package tech.pegasys.artemis.networking.eth2;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import tech.pegasys.artemis.util.events.Subscribers;

/**
 * Service tracks long term attestation subnet subscriptions and notifies subscribers on their
 * changes
 */
public class AttestationSubnetService {
  private final Subscribers<Consumer<Iterable<Integer>>> subscribers = Subscribers.create(true);
  private Iterable<Integer> currentSubscriptions = Collections.emptyList();
  private final ExecutorService publisherExecutor = Executors.newSingleThreadExecutor(
      new ThreadFactoryBuilder()
          .setDaemon(true)
          .setNameFormat("AttestationSubnetServicePublisherThread")
          .build());

  public synchronized void updateSubscriptions(final Iterable<Integer> subnetIndices) {
    publisherExecutor.execute(() -> subscribers.deliver(Consumer::accept, subnetIndices));
    currentSubscriptions = subnetIndices;
  }

  public synchronized long subscribeToUpdates(Consumer<Iterable<Integer>> observer) {
    publisherExecutor.execute(() -> observer.accept(currentSubscriptions));
    return subscribers.subscribe(observer);
  }

  public void unsubscribe(long subscriptionId) {
    subscribers.unsubscribe(subscriptionId);
  }
}
