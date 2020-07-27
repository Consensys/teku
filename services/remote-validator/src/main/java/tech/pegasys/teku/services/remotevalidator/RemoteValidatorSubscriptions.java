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
  private final RemoteValidatorMetrics metrics;

  private final Map<String, Consumer<BeaconChainEvent>> subscriptions = new ConcurrentHashMap<>();

  public RemoteValidatorSubscriptions(
      final TekuConfiguration configuration, final RemoteValidatorMetrics metrics) {
    checkNotNull(configuration, "TekuConfiguration can't be null");
    checkNotNull(metrics, "RemoteValidatorMetrics can't be null");

    this.maxSubscribers = configuration.getRemoteValidatorApiMaxSubscribers();
    this.metrics = metrics;
  }

  SubscriptionStatus subscribe(String id, Consumer<BeaconChainEvent> sendEvent) {
    synchronized (this) {
      if (subscriptions.size() >= maxSubscribers) {
        return SubscriptionStatus.maxSubscribers();
      } else {
        subscriptions.put(id, sendEvent);
        updateSubscribedValidatorsMetric();
        return SubscriptionStatus.success();
      }
    }
  }

  void unsubscribe(String id) {
    subscriptions.remove(id);
    updateSubscribedValidatorsMetric();
  }

  void unsubscribeAll() {
    subscriptions.clear();
    updateSubscribedValidatorsMetric();
  }

  @Override
  public void onEvent(final BeaconChainEvent event) {
    subscriptions.values().parallelStream().forEach((callback) -> callback.accept(event));
  }

  private void updateSubscribedValidatorsMetric() {
    metrics.updateConnectedValidators(subscriptions.size());
  }

  static class SubscriptionStatus {

    private final boolean hasSubscribed;
    private final String info;

    static SubscriptionStatus success() {
      return new SubscriptionStatus(true, "ok");
    }

    static SubscriptionStatus maxSubscribers() {
      return new SubscriptionStatus(false, "Reached max subscribers");
    }

    private SubscriptionStatus(final boolean hasSubscribed, final String info) {
      this.hasSubscribed = hasSubscribed;
      this.info = info;
    }

    public boolean hasSubscribed() {
      return hasSubscribed;
    }

    public String getInfo() {
      return info;
    }
  }
}
