/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.services.powchain;

import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_ETH1_REQUESTS;

import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.beacon.pow.ErrorTrackingEth1Provider;
import tech.pegasys.teku.beacon.pow.Eth1Provider;
import tech.pegasys.teku.beacon.pow.Eth1ProviderSelector;
import tech.pegasys.teku.beacon.pow.FallbackAwareEth1Provider;
import tech.pegasys.teku.beacon.pow.MonitorableEth1Provider;
import tech.pegasys.teku.beacon.pow.ThrottlingEth1Provider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class Eth1Providers {

  private final Eth1ProviderMonitor eth1ProviderMonitor;
  private final Eth1Provider eth1Provider;

  public Eth1Providers(
      final Eth1ProviderMonitor eth1ProviderMonitor, final Eth1Provider eth1Provider) {
    this.eth1ProviderMonitor = eth1ProviderMonitor;
    this.eth1Provider = eth1Provider;
  }

  public static Eth1Providers create(
      final List<MonitorableEth1Provider> baseProviders,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem) {
    final Eth1Provider eth1Provider;
    final Eth1ProviderSelector eth1ProviderSelector;
    if (baseProviders.size() == 1) {
      final MonitorableEth1Provider web3jEth1Provider = baseProviders.get(0);
      eth1ProviderSelector = new Eth1ProviderSelector(Collections.singletonList(web3jEth1Provider));

      eth1Provider =
          new ThrottlingEth1Provider(
              new ErrorTrackingEth1Provider(web3jEth1Provider, asyncRunner, timeProvider),
              MAXIMUM_CONCURRENT_ETH1_REQUESTS,
              metricsSystem);
    } else {
      eth1ProviderSelector = new Eth1ProviderSelector(baseProviders);

      eth1Provider =
          new ThrottlingEth1Provider(
              new ErrorTrackingEth1Provider(
                  new FallbackAwareEth1Provider(eth1ProviderSelector, asyncRunner),
                  asyncRunner,
                  timeProvider),
              MAXIMUM_CONCURRENT_ETH1_REQUESTS,
              metricsSystem);
    }
    final Eth1ProviderMonitor eth1ProviderMonitor =
        new Eth1ProviderMonitor(eth1ProviderSelector, asyncRunner);
    return new Eth1Providers(eth1ProviderMonitor, eth1Provider);
  }

  public Eth1Provider getEth1Provider() {
    return eth1Provider;
  }

  public void start() {
    eth1ProviderMonitor.start();
  }

  public void stop() {
    eth1ProviderMonitor.stop();
  }
}
