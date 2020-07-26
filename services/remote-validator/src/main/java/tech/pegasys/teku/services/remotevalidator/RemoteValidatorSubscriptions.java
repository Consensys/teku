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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class RemoteValidatorSubscriptions implements BeaconChainEventsListener {

  private final int maxSubscribers;

  private final Map<String, Consumer<BeaconChainEvent>> subscriptions = new ConcurrentHashMap<>();

  public RemoteValidatorSubscriptions(final TekuConfiguration configuration) {
    checkNotNull(configuration, "TekuConfiguration can't be null");

    this.maxSubscribers = configuration.getRemoteValidatorApiMaxSubscribers();
  }

  boolean subscribe(String id, Consumer<BeaconChainEvent> sendEvent) {
    // TODO return a subscribe status?
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
