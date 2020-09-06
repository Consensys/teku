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

import tech.pegasys.teku.infrastructure.subscribers.ObservableValue;
import tech.pegasys.teku.infrastructure.subscribers.ValueObserver;

/**
 * Service tracks long term attestation subnet subscriptions and notifies subscribers on their
 * changes
 */
public class AttestationSubnetService {
  ObservableValue<Iterable<Integer>> attSubnetSubscriptions = new ObservableValue<>(true);

  public synchronized void updateSubscriptions(final Iterable<Integer> subnetIndices) {
    attSubnetSubscriptions.set(subnetIndices);
  }

  public synchronized long subscribeToUpdates(ValueObserver<Iterable<Integer>> observer) {
    return attSubnetSubscriptions.subscribe(observer);
  }

  public void unsubscribe(long subscriptionId) {
    attSubnetSubscriptions.unsubscribe(subscriptionId);
  }
}
