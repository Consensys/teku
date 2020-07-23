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

package tech.pegasys.teku.services.remotevalidator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RemoteValidatorSubscriptions implements BeaconChainEventsListener {

  // TODO should we parametrize maxSubscribers?
  private final int maxSubscribers = 1_000;

  private final Map<String, Consumer<BeaconChainEvent>> subscriptions = new ConcurrentHashMap<>();

  boolean subscribe(String id, Consumer<BeaconChainEvent> sendEvent) {
    synchronized (this) {
      if (subscriptions.size() >= maxSubscribers) {
        return false;
      } else {
        subscriptions.put(id, sendEvent);
        return true;
      }
    }
  }

  void unsubscribe(String id) {
    subscriptions.remove(id);
  }

  void unsubscribeAll() {
    subscriptions.clear();
  }

  @Override
  public void onEvent(final BeaconChainEvent event) {
    subscriptions.values().parallelStream().forEach((callback) -> callback.accept(event));
  }
}
