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

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;

public class RemoteValidatorService extends Service {

  private final RemoteValidatorSubscriptions subscriptions;
  private final RemoteValidatorApi api;
  private final RemoteValidatorBeaconChainEventsAdapter beaconChainEventsAdapter;

  public RemoteValidatorService(ServiceConfig serviceConfig) {
    final RemoteValidatorMetrics metrics =
        new RemoteValidatorMetrics(serviceConfig.getMetricsSystem());
    subscriptions = new RemoteValidatorSubscriptions(serviceConfig.getConfig(), metrics);
    api = new RemoteValidatorApi(serviceConfig.getConfig(), subscriptions);
    beaconChainEventsAdapter =
        new RemoteValidatorBeaconChainEventsAdapter(serviceConfig, subscriptions);
  }

  @Override
  protected SafeFuture<Void> doStart() {
    return SafeFuture.fromRunnable(
        () -> {
          api.start();
          beaconChainEventsAdapter.start();
        });
  }

  @Override
  protected SafeFuture<Void> doStop() {
    return SafeFuture.fromRunnable(
        () -> {
          api.stop();
          subscriptions.unsubscribeAll();
        });
  }
}
